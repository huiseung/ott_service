import { apiRequest, setAccessToken } from "@/shared/apiClient";
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean }
export interface VideoItem { id: number; title: string }
export interface ContentItem { contentId: number; type: "MOVIE" | "SERIES"; title: string; videoId: number }
export interface ContentPage { items: ContentItem[]; nextCursor: number | null; hasNext: boolean }
export interface CurrentUser { userId: number; loginId: string; displayName: string }
export interface AuthResponse extends CurrentUser { accessToken: string; expiresInSeconds: number }
export interface PlaybackStart { playbackSessionId: string; hlsUrl: string; durationSeconds: number; resumePositionSeconds: number; expiresAt: string; title?: string }
export const userApi = {
  createCheckout: (request: { requestId: string; contentId: number; ctaEventId: string | null }) =>
    apiRequest<{ checkoutId: string; subscriptionId: string; status: string }>("/api/user/subscriptions/checkouts", { method: "POST", body: JSON.stringify(request) }),
  activateLocalSubscription: (checkoutId: string) => apiRequest<{ subscriptionId: string; status: string }>(
    `/api/user/subscriptions/local/checkouts/${checkoutId}/activate`, { method: "POST" }),
  subscription: () => apiRequest<{ subscriptionId: string | null; status: string }>("/api/user/subscriptions/me"),
  contents: (cursor: number | null, signal?: AbortSignal) => apiRequest<ContentPage>(`/api/public/contents?size=20${cursor === null ? "" : `&cursor=${cursor}`}`, { signal }),
  videos: (page: number) => apiRequest<Page<VideoItem>>(`/api/user/videos?page=${page}&size=20`),
  me: () => apiRequest<CurrentUser>("/api/user/me"),
  login: async (loginId: string, password: string) => {
    const response = await apiRequest<AuthResponse>("/api/auth/login", { method: "POST", body: JSON.stringify({ loginId, password }) });
    setAccessToken(response.accessToken);
    return { userId: response.userId, loginId: response.loginId, displayName: response.displayName };
  },
  refresh: async () => {
    const response = await apiRequest<AuthResponse>("/api/auth/refresh", { method: "POST" }, false);
    setAccessToken(response.accessToken);
    return { userId: response.userId, loginId: response.loginId, displayName: response.displayName };
  },
  logout: async () => {
    try { await apiRequest<void>("/api/auth/logout", { method: "POST" }, false); }
    finally { setAccessToken(null); }
  },
  startPlayback: (videoId: number) => apiRequest<PlaybackStart>(`/api/playback/videos/${videoId}/sessions`, { method: "POST" }),
};
