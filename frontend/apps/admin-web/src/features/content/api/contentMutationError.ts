import { ApiError, userError } from "@/shared/lib/apiClient";

export function contentMutationError(error: unknown, hint: string): string {
  if (!(error instanceof ApiError)) return userError(error);
  let message = "";
  try {
    const body: unknown = JSON.parse(error.message);
    if (body && typeof body === "object" && "message" in body && typeof body.message === "string") message = body.message;
  } catch { /* Non-JSON responses use the status-based fallback below. */ }
  if (error.status === 400) {
    if (message.startsWith("Genre does not exist:")) return "등록되지 않은 Genre 코드입니다. 기존 장르 코드를 확인하세요.";
    if (message === "availableUntil must be greater than or equal to availableFrom") return "Available Until은 Available From 이후 또는 같은 시각이어야 합니다.";
    return `입력값을 저장할 수 없습니다. ${hint}`;
  }
  if (error.status === 404) return "Content 또는 편집 대상이 없습니다. 목록에서 다시 확인하세요.";
  if (error.status === 409) return `이미 등록된 값이거나 다른 요청으로 데이터가 변경되었습니다. ${hint}`;
  if (error.status === 413) return "파일이 서버의 업로드 크기 제한을 초과했습니다. 더 작은 파일을 선택하세요.";
  return userError(error);
}
