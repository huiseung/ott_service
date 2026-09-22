import { apiRequest, setAccessToken } from "@/shared/apiClient";
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean }
export interface VideoItem { id: number; title: string }
export interface CurrentUser { userId: number; loginId: string; displayName: string }
export interface AuthResponse extends CurrentUser { accessToken: string; expiresInSeconds: number }
export interface PlaybackStart { playbackSessionId: string; hlsUrl: string; durationSeconds: number; resumePositionSeconds: number; expiresAt: string; title?: string }
export type WatchEventType = "PLAY" | "PROGRESS" | "PAUSE" | "COMPLETE" | "SESSION_END";
export interface WatchEventRequest { eventType: WatchEventType; positionSeconds: number; sequence: number }
export interface WatchEventResponse { eventId: string; status: string }
export const userApi = {
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
  event: (sessionId: string, event: WatchEventRequest) => apiRequest<WatchEventResponse>(`/api/playback/sessions/${sessionId}/events`, { method: "POST", body: JSON.stringify(event) }),
};
