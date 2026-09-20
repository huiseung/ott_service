import { apiRequest } from "@/shared/apiClient";
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; first: boolean; last: boolean }
export interface VideoItem { id: number; title: string }
export interface CurrentUser { id: number; loginId: string; displayName: string }
export interface PlaybackStart { playbackSessionId: number; manifestUrl: string; durationMs: number; resumePositionMs: number; expiresAt: string; title: string }
export interface ProgressEvent { playbackSessionId: number; clientEventSeq: number; positionMs: number; durationMs: number }
export interface ProgressResponse { positionMs: number; clientEventSeq: number }
export const userApi = {
  videos: (page: number) => apiRequest<Page<VideoItem>>(`/api/user/videos?page=${page}&size=20`),
  me: () => apiRequest<CurrentUser>("/api/user/me"),
  login: (loginId: string, password: string) => apiRequest<CurrentUser>("/api/user/login", { method: "POST", body: JSON.stringify({ loginId, password }) }),
  logout: () => apiRequest<void>("/api/user/logout", { method: "POST" }),
  startPlayback: (videoId: number) => apiRequest<PlaybackStart>(`/api/user/videos/${videoId}/playback`, { method: "POST" }),
  progress: (videoId: number, event: ProgressEvent) => apiRequest<ProgressResponse>(`/api/user/videos/${videoId}/progress`, { method: "POST", body: JSON.stringify(event) }),
};
