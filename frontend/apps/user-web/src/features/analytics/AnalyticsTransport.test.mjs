import test from "node:test";
import assert from "node:assert/strict";
import { createAnalyticsTransport } from "./AnalyticsTransport.ts";

const token = (sub, version = 1) => `header.${Buffer.from(JSON.stringify({ sub, version })).toString("base64url")}.signature`;
const result = (status, body = {}) => ({ status, ok: status >= 200 && status < 300, json: async () => body });

test("long playback refreshes an expired token and retries identical batch for the same account", async () => {
  let current = token("7");
  const captured = current;
  const fresh = token("7", 2);
  const calls = [];
  const send = createAnalyticsTransport({ apiBaseUrl: "", getToken: () => current, setToken: value => { current = value; },
    fetch: async (url, init) => {
      calls.push({ url, ...init });
      if (url.endsWith("refresh")) return result(200, { accessToken: fresh });
      return result(init.headers.Authorization === `Bearer ${fresh}` ? 202 : 401);
    } });
  assert.equal(await send("original batch", captured, false), 202);
  assert.equal(calls.length, 3);
  assert.equal(calls[0].body, calls[2].body);
  assert.equal(current, fresh);
  assert.equal(await send("next batch", captured, false), 202);
  assert.equal(calls.length, 4, "reuse a newer token belonging to the same account");
});

test("logout or account switch during refresh cannot reattribute queued events", async () => {
  const captured = token("7");
  let current = captured;
  let sends = 0;
  const send = createAnalyticsTransport({ apiBaseUrl: "", getToken: () => current,
    setToken: () => assert.fail("must not replace the new account's token"),
    fetch: async url => {
      if (url.endsWith("refresh")) { current = token("8"); return result(200, { accessToken: token("8") }); }
      sends++; return result(401);
    } });
  assert.equal(await send("batch", captured, false), 401);
  assert.equal(sends, 1);
});

test("anonymous and pagehide transport never starts authentication refresh", async () => {
  const calls = [];
  const send = createAnalyticsTransport({ apiBaseUrl: "", getToken: () => token("7"), setToken: () => assert.fail(),
    fetch: async (url, init) => { calls.push({ url, init }); return result(401); } });
  assert.equal(await send("anonymous", null, false), 401);
  assert.equal(calls[0].init.headers.Authorization, undefined);
  assert.equal(await send("pagehide", token("7"), true), 401);
  assert.ok(calls.every(call => call.url.endsWith("batch")));
});
