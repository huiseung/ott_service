import { apiRequest } from "@/shared/lib/apiClient";
import type { VideoFileStatus } from "@/features/video/api/adminVideoApi";
export interface CreateVideoResponse { videoId: number; videoFileId: number; partSize: number; totalParts: number; status: VideoFileStatus }
export interface UploadStatusResponse { videoFileId: number; status: VideoFileStatus; partSize: number; totalParts: number; fingerprint: string; confirmedParts: number[]; retryParts: number[] }
export interface AckPart { partNumber: number; etag: string; size: number }
export const videoUploadApi = {
  create: (title: string, file: File, fingerprint: string, key: string) => apiRequest<CreateVideoResponse>("/api/admin/videos", { method: "POST", headers: { "Idempotency-Key": key }, body: JSON.stringify({ title, file: { fileName: file.name, fileSize: file.size, contentType: file.type || "application/octet-stream", fingerprint } }) }),
  presign: (id: number, partNumbers: number[]) => apiRequest<{ parts: { partNumber: number; url: string }[]; expiresAt: string }>(`/api/admin/video-files/${id}/parts/presign`, { method: "POST", body: JSON.stringify({ partNumbers }) }),
  ack: (id: number, parts: AckPart[]) => apiRequest<void>(`/api/admin/video-files/${id}/parts`, { method: "POST", body: JSON.stringify({ parts }) }),
  status: (id: number) => apiRequest<UploadStatusResponse>(`/api/admin/video-files/${id}/upload-status`),
  complete: (id: number) => apiRequest<void>(`/api/admin/video-files/${id}/complete`, { method: "POST" }),
  abort: (id: number) => apiRequest<void>(`/api/admin/video-files/${id}`, { method: "DELETE" }),
};
