import test from "node:test";
import assert from "node:assert/strict";
import { collectionPreviewReasonLabel } from "./collectionPreviewReason.ts";

test("all Collection Preview reason codes have Korean operator labels", () => {
  const expected = {
    COLLECTION_NOT_PUBLISHED: "게시되지 않은 Collection",
    COLLECTION_TERRITORY_NOT_CONFIGURED: "해당 국가 Collection 공개 정책 없음",
    COLLECTION_TERRITORY_DISABLED: "해당 국가 Collection 공개 비활성화",
    COLLECTION_NOT_YET_AVAILABLE: "Collection 공개 시작 전",
    COLLECTION_AVAILABILITY_EXPIRED: "Collection 공개 기간 종료",
    CONTENT_NOT_PUBLISHED: "게시되지 않은 콘텐츠",
    CONTENT_TERRITORY_NOT_CONFIGURED: "해당 국가 공개 정책 없음",
    CONTENT_TERRITORY_DISABLED: "해당 국가 공개 비활성화",
    CONTENT_NOT_YET_AVAILABLE: "공개 시작 전",
    CONTENT_AVAILABILITY_EXPIRED: "공개 기간 종료",
  };
  for (const [code, label] of Object.entries(expected)) assert.equal(collectionPreviewReasonLabel(code), label);
});

test("null reason does not imply visibility or displayability", () => {
  assert.equal(collectionPreviewReasonLabel(null), "—");
});

test("unrecognized future reason codes remain visible instead of being interpreted", () => {
  for (const code of ["CONTENT_NEW_RESTRICTION", "toString", "__proto__"]) {
    assert.equal(collectionPreviewReasonLabel(code), `알 수 없는 사유 (${code})`);
  }
});
