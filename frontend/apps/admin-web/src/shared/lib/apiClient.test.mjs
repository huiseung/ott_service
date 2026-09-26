import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import ts from "typescript";

const source = await readFile(new URL("./apiClient.ts", import.meta.url), "utf8");
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText
  .replace('"./config"', JSON.stringify(new URL("./config.ts", import.meta.url).href));
const { apiRequest, loginAdmin, logoutAdmin, ApiError, userError } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString("base64")}`);

test("login verifies the session and sends cookies and CSRF without Basic credentials", async t => {
  const calls = [];
  t.mock.method(globalThis, "fetch", async (url, init) => {
    calls.push({ url, init });
    if (url.endsWith("/csrf")) return Response.json({ headerName: "X-CSRF-TOKEN", token: "test-token" });
    if (url.endsWith("/login")) return new Response(null, { status: 204 });
    return Response.json({ username: "test-admin" });
  });
  assert.deepEqual(await loginAdmin("test-admin", "test-only"), { username: "test-admin" });
  assert.equal(calls.length, 3);
  for (const { init } of calls) {
    assert.equal(init.credentials, "include");
    assert.equal(init.cache, "no-store");
    assert.equal(init.headers.has("Authorization"), false);
  }
  assert.equal(calls[1].init.headers.get("X-CSRF-TOKEN"), "test-token");
  assert.equal(calls[1].init.headers.get("Content-Type"), "application/x-www-form-urlencoded");
});

test("Content creation, PATCH and logout use fresh CSRF tokens", async t => {
  const writes = [];
  let tokens = 0;
  t.mock.method(globalThis, "fetch", async (url, init) => {
    if (url.endsWith("/csrf")) return Response.json({ headerName: "X-CSRF-TOKEN", token: `token-${++tokens}` });
    writes.push(init);
    return new Response(null, { status: 204 });
  });
  await apiRequest("/api/admin/contents", { method: "POST", body: JSON.stringify({ type: "MOVIE" }) });
  await apiRequest("/api/admin/media-versions/1", { method: "PATCH", body: "{}" });
  await logoutAdmin();
  assert.deepEqual(writes.map(write => write.headers.get("X-CSRF-TOKEN")), ["token-1", "token-2", "token-3"]);
  assert.ok(writes.every(write => write.credentials === "include"));
});

test("failed writes are not replayed and CSRF failure is distinct from bad credentials", async t => {
  let writes = 0;
  t.mock.method(globalThis, "fetch", async url => {
    if (url.endsWith("/csrf")) return Response.json({ headerName: "X-CSRF-TOKEN", token: "token" });
    writes++;
    return Response.json({ code: "CSRF_INVALID" }, { status: 403 });
  });
  await assert.rejects(apiRequest("/api/admin/contents", { method: "POST" }), error => {
    assert.match(userError(error), /보안 토큰/);
    return error instanceof ApiError && error.status === 403;
  });
  assert.equal(writes, 1);
});
