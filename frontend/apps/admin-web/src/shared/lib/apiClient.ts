import { config } from "./config";
export class ApiError extends Error { constructor(public status: number, message: string) { super(message); } }
// Spring currently uses HTTP Basic. Credentials live only in this tab's memory.
let basicAuthorization: string | null = null;
export function setAdminCredentials(username: string, password: string) { basicAuthorization = `Basic ${btoa(`${username}:${password}`)}`; }
export function hasAdminCredentials() { return basicAuthorization !== null; }
export function getAdminAuthorization() { return basicAuthorization; }
export function clearAdminCredentials() { basicAuthorization = null; }
export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${config.adminApiBaseUrl}${path}`, {
    ...init,
    headers: { ...(init.body ? { "Content-Type": "application/json" } : {}), ...(basicAuthorization ? { Authorization: basicAuthorization } : {}), ...init.headers },
    cache: "no-store",
  });
  if (!response.ok) throw new ApiError(response.status, await response.text() || `HTTP ${response.status}`);
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export function userError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "인증 정보가 올바르지 않습니다. 다시 입력하세요.";
    if (error.status === 403) return "관리자 권한이 없습니다.";
    if (error.status === 409) return "상태가 변경되었습니다. 새로고침 후 다시 시도하세요.";
    return `API 요청이 실패했습니다. (HTTP ${error.status})`;
  }
  return error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
}
