import test from "node:test";
import assert from "node:assert/strict";
import { AnalyticsClient } from "./AnalyticsClient.ts";

const input = (payload = {}) => ({ eventType: "CONTENT_CLICK", contentId: 7, payload });
function fixture(overrides = {}) {
  let now = 1000;
  let id = 0;
  const calls = [];
  const client = new AnalyticsClient({ anonymousId: "anonymous", sessionId: "visit", token: () => null,
    now: () => now, uuid: () => `id-${++id}`,
    transport: async (body, token, keepalive) => { calls.push({ events: JSON.parse(body).events, body, token, keepalive }); return 202; },
    ...overrides });
  return { client, calls, advance: ms => { now += ms; } };
}

test("events queue until flush and retain independent IDs in a batch", async () => {
  const { client, calls } = fixture();
  client.track(input()); client.track(input());
  assert.equal(calls.length, 0);
  await client.flush();
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0].events.map(e => e.eventId), ["id-1", "id-2"]);
  assert.equal(client.pendingCount, 0);
});

test("failed batch retries the exact original IDs, timestamps and payload", async () => {
  const bodies = [];
  const { client, advance } = fixture({ transport: async body => { bodies.push(body); return bodies.length === 1 ? 503 : 202; } });
  const facts = { surface: "home" };
  client.track(input(facts));
  facts.surface = "changed";
  await client.flush();
  await client.flush();
  assert.equal(bodies.length, 1, "backoff must suppress immediate retry");
  advance(1000);
  await client.flush();
  assert.equal(bodies[0], bodies[1]);
  assert.equal(JSON.parse(bodies[1]).events[0].payload.surface, "home");
});

test("queue and retry attempts remain bounded during outage", async () => {
  const { client, advance } = fixture({ maxQueueEvents: 2, maxAttempts: 2, transport: async () => 503 });
  client.track(input()); client.track(input());
  assert.equal(client.track(input()), null);
  await client.flush(); advance(1000); await client.flush();
  assert.equal(client.pendingCount, 0);
  assert.equal(client.stats.dropped, 3);
});

test("HTTP validation and authentication failures are not retried", async () => {
  for (const status of [400, 401, 403, 413]) {
    const { client } = fixture({ transport: async () => status });
    client.track(input()); await client.flush();
    assert.equal(client.pendingCount, 0);
  }
});

test("batch byte limits count UTF-8, split batches and drop oversized single events", async () => {
  const { client, calls } = fixture({ maxBytes: 700 });
  client.track(input({ query: "한글".repeat(20) }));
  client.track(input({ query: "한글".repeat(20) }));
  await client.flush();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 2);
  calls.forEach(call => assert.ok(Buffer.byteLength(call.body) <= 700));
  assert.equal(client.track(input({ query: "한글".repeat(1000) })), null);
});

test("different login credentials never merge or reattribute queued anonymous events", async () => {
  let token = null;
  const { client, calls } = fixture({ token: () => token });
  client.track(input()); token = "user-a"; client.track(input()); token = "user-b";
  await client.flush(); await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(calls.map(c => c.token), [null, "user-a"]);
});

test("playback cleanup retains its original account after another account signs in", async () => {
  const { client, calls } = fixture({ token: () => "new-account" });
  client.track(input(), "playback-owner");
  await client.flush();
  assert.equal(calls[0].token, "playback-owner");
});

test("pagehide uses keepalive and preserves IDs while regular batch is in flight", async () => {
  let finish;
  const calls = [];
  const { client } = fixture({ transport: (body, token, keepalive) => {
    calls.push({ body, token, keepalive });
    return keepalive ? Promise.resolve(202) : new Promise(resolve => { finish = resolve; });
  } });
  client.track(input());
  const regular = client.flush();
  client.track(input());
  await client.flushOnPageHide();
  assert.equal(calls[1].keepalive, true);
  assert.deepEqual(JSON.parse(calls[1].body).events.map(e => e.eventId), ["id-1", "id-2"]);
  finish(202); await regular;
  assert.equal(client.pendingCount, 0);
});

test("max event count triggers flush and stale queue entries expire", async () => {
  const { client, calls, advance } = fixture({ maxEvents: 2 });
  client.track(input()); client.track(input());
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 1);
  client.track(input()); advance(300001); await client.flush();
  assert.equal(calls.length, 1);
  assert.equal(client.stats.dropped, 1);
});
