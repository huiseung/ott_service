import type { CollectionPreviewReason } from "./adminCollectionApi";

const reasonLabels = {
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
} satisfies Record<CollectionPreviewReason, string>;

const labels = new Map<string, string>(Object.entries(reasonLabels));

// Presentation only: never infer visibility from a reason, status, or date.
export function collectionPreviewReasonLabel(reason: string | null) {
  return reason === null ? "—" : labels.get(reason) ?? `알 수 없는 사유 (${reason})`;
}
