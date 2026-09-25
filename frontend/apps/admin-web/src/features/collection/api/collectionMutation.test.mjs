import test from "node:test";
import assert from "node:assert/strict";
import { executeCollectionMutation } from "./collectionMutation.ts";

const collection = items => ({
  id: 7, status: "DRAFT", minVisibleItems: 0, version: 1,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  localizations: [], availabilities: [],
  items: items.map((contentId, index) => ({ id: contentId, contentId, displayOrder: index + 1 })),
});

test("successful bulk reorder uses the response and makes no extra GET", async () => {
  const saved = collection([22, 11]);
  const result = await executeCollectionMutation(async () => saved, async () => { throw new Error("Unexpected GET"); });
  assert.equal(result.status, "saved");
  assert.deepEqual(result.collection.items.map(item => item.contentId), [22, 11]);
});

test("lost response after commit re-reads actual order without replaying the write", async () => {
  let writes = 0;
  let server = collection([11, 22]);
  const lostResponse = new Error("Connection lost after commit");
  const result = await executeCollectionMutation(async () => {
    writes++;
    server = collection([22, 11]);
    throw lostResponse;
  }, async () => server);
  assert.equal(writes, 1);
  assert.equal(result.status, "recovered");
  assert.equal(result.error, lostResponse);
  assert.deepEqual(result.collection.items.map(item => item.contentId), [22, 11]);
});

test("rejected stale reorder adopts the server's concurrently added Content", async () => {
  const server = collection([11, 22, 33]);
  const result = await executeCollectionMutation(async () => { throw new Error("Reorder membership mismatch"); }, async () => server);
  assert.equal(result.status, "recovered");
  assert.deepEqual(result.collection.items.map(item => item.contentId), [11, 22, 33]);
});

test("failed reorder and failed reload report unsynchronized state without stale data", async () => {
  const rejected = new Error("Reorder failed");
  const result = await executeCollectionMutation(async () => { throw rejected; }, async () => { throw new Error("GET failed"); });
  assert.equal(result.status, "unsynchronized");
  assert.equal(result.error, rejected);
  assert.equal("collection" in result, false);
});
