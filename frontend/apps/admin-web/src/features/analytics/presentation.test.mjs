import test from "node:test";
import assert from "node:assert/strict";
import { daysBetween, metric, percent, validRange } from "./presentation.ts";

test("missing samples remain distinct from observed zero", () => {
  assert.equal(metric(null), "—"); assert.equal(percent(null), "—");
  assert.equal(metric(0), "0"); assert.equal(percent(0), "0%");
  assert.equal(percent(0.25), "25%"); assert.equal(metric(NaN), "—");
});
test("inclusive UTC date range is bounded to 31 days and valid calendar dates", () => {
  assert.equal(validRange("2026-01-01", "2026-01-31"), true);
  assert.equal(validRange("2026-01-01", "2026-02-01"), false);
  assert.equal(validRange("2026-02-30", "2026-03-01"), false);
  assert.equal(validRange("2026-03-02", "2026-03-01"), false);
  assert.deepEqual(daysBetween("2026-09-24", "2026-09-25"), ["2026-09-24", "2026-09-25"]);
});
