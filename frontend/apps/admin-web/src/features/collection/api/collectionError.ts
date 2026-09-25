import { ApiError, userError } from "@/shared/lib/apiClient";

export function collectionError(error: unknown, hint: string) {
  if (!(error instanceof ApiError)) return userError(error);
  if (error.status === 400) return `입력값을 저장할 수 없습니다. ${hint}`;
  if (error.status === 404) return "Collection 또는 편집 대상이 없습니다. 목록에서 다시 확인하세요.";
  if (error.status === 409) return `중복 등록, 다른 요청의 변경 또는 ARCHIVED 상태로 저장할 수 없습니다. ${hint}`;
  return userError(error);
}
