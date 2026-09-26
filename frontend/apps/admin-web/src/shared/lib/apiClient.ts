import { config } from "./config";
export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}
export type AdminSession = { username: string };
export const sessionExpiredEvent = "admin-session-expired";

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  // Refresh the token before mutations, including login/logout. Never auto-retry a mutation.
  if (!["GET", "HEAD", "OPTIONS"].includes((init.method ?? "GET").toUpperCase())) {
    const csrf = await apiRequest<{ headerName: string; token: string }>("/api/admin/auth/csrf");
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(`${config.adminApiBaseUrl}${path}`, {
    ...init, headers, credentials: "include", cache: "no-store",
  });
  if (!response.ok) {
    if (response.status === 401 && path !== "/api/admin/auth/login" && typeof window !== "undefined") {
      window.dispatchEvent(new Event(sessionExpiredEvent));
    }
    throw new ApiError(response.status, await response.text() || `HTTP ${response.status}`);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export const getAdminSession = () => apiRequest<AdminSession>("/api/admin/auth/session");
export async function loginAdmin(username: string, password: string) {
  await apiRequest<void>("/api/admin/auth/login", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ username, password }).toString(),
  });
  return getAdminSession();
}
export const logoutAdmin = () => apiRequest<void>("/api/admin/auth/logout", { method: "POST" });
export function userError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "로그인이 필요하거나 인증 정보가 올바르지 않습니다. 다시 로그인하세요.";
    if (error.status === 403 && error.message.includes('"CSRF_INVALID"')) return "요청 보안 토큰이 만료되었습니다. 다시 시도하세요.";
    if (error.status === 403) return "관리자 권한이 없습니다.";
    if (error.status === 409) return "상태가 변경되었습니다. 새로고침 후 다시 시도하세요.";
    return `API 요청이 실패했습니다. (HTTP ${error.status})`;
  }
  return error instanceof Error ? error.message : "요청을 처리하지 못했습니다.";
}
