import { config } from "./config";
export class ApiError extends Error { constructor(public status: number, message: string) { super(message); } }

const tokenKey = "ott_access_token";
let accessToken: string | null = typeof window === "undefined" ? null : window.localStorage.getItem(tokenKey);

export function setAccessToken(token: string | null) {
  accessToken = token;
  if (typeof window === "undefined") return;
  if (token) window.localStorage.setItem(tokenKey, token);
  else window.localStorage.removeItem(tokenKey);
}

export function getAccessToken() {
  return accessToken;
}

async function refreshAccessToken() {
  const response = await fetch(`${config.apiBaseUrl}/api/auth/refresh`, {
    method: "POST",
    credentials: "include",
    cache: "no-store",
  });
  if (!response.ok) {
    setAccessToken(null);
    throw new ApiError(response.status, "로그인이 필요하거나 세션이 만료되었습니다.");
  }
  const body = await response.json() as { accessToken: string };
  setAccessToken(body.accessToken);
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  let response: Response;
  const headers = {
    ...(init.body ? { "Content-Type": "application/json" } : {}),
    ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    ...init.headers,
  };
  try { response = await fetch(`${config.apiBaseUrl}${path}`, { ...init, credentials: "include", cache: "no-store", headers }); }
  catch { throw new ApiError(0, "네트워크에 연결할 수 없습니다. Backend 실행 상태를 확인하세요."); }
  if (response.status === 401 && retry && !path.startsWith("/api/auth/")) {
    await refreshAccessToken();
    return apiRequest<T>(path, init, false);
  }
  if (!response.ok) throw new ApiError(response.status, response.status === 401 ? "로그인이 필요하거나 세션이 만료되었습니다." : `요청에 실패했습니다. (HTTP ${response.status})`);
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export function errorMessage(error: unknown) { return error instanceof Error ? error.message : "요청에 실패했습니다."; }
