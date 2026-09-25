import test from "node:test";
import assert from "node:assert/strict";
import { statusTone } from "./statusPresentation.ts";

test("Video, Upload, and Encoding status tones retain their previous behavior", () => {
  const groups = {
    success: ["READY", "COMPLETED"],
    danger: ["PROCESSING_FAILED", "FAILED", "ABORTED", "EXPIRED"],
    progress: ["PROCESSING", "UPLOADING", "TRANSCODING", "PROBING"],
    neutral: ["DRAFT", "PREPARING", "COMPLETING", "QUEUED", "RETRY_WAIT", "PAUSED", "SUPERSEDED", "WAITING", "DOWNLOADING_SOURCE", "UPLOADING_PACKAGE", "VALIDATING_PACKAGE", "PUBLISHING"],
  };
  for (const [tone, statuses] of Object.entries(groups)) {
    for (const status of statuses) {
      // UPLOADING_PACKAGE matched the old regex's UPLOADING branch.
      assert.equal(statusTone(status), status === "UPLOADING_PACKAGE" ? "progress" : tone, status);
    }
  }
});

test("CMS status tones distinguish published/available from draft/disabled/archived", () => {
  for (const value of ["PUBLISHED", "AVAILABLE"]) assert.equal(statusTone(value), "success");
  for (const value of ["DRAFT", "DISABLED", "ARCHIVED", "UNKNOWN"]) assert.equal(statusTone(value), "neutral");
});
