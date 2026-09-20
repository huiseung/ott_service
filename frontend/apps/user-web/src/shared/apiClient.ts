import { config } from "./config";
export class ApiError extends Error { constructor(public status: number, message: string) { super(message); } }
export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try { response = await fetch(`${config.apiBaseUrl}${path}`, { ...init, credentials: "include", cache: "no-store", headers: { ...(init.body ? { "Content-Type": "application/json" } : {}), ...init.headers } }); }
  catch { throw new ApiError(0, "네트워크에 연결할 수 없습니다. Backend 실행 상태를 확인하세요."); }
  if (!response.ok) throw new ApiError(response.status, response.status === 401 ? "로그인이 필요하거나 세션이 만료되었습니다." : `요청에 실패했습니다. (HTTP ${response.status})`);
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export function errorMessage(error: unknown) { return error instanceof Error ? error.message : "요청에 실패했습니다."; }
