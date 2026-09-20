import { apiRequest } from "@/shared/lib/apiClient";
import type { PageResponse } from "@/shared/lib/page";
export type VideoStatus = "DRAFT" | "PROCESSING" | "READY" | "PROCESSING_FAILED";
export type VideoFileStatus = "PREPARING" | "UPLOADING" | "COMPLETING" | "COMPLETED" | "FAILED" | "EXPIRED" | "ABORTED";
export type JobStatus = "QUEUED" | "PROCESSING" | "RETRY_WAIT" | "COMPLETED" | "FAILED" | "SUPERSEDED";
export type JobStage = "WAITING" | "DOWNLOADING_SOURCE" | "PROBING" | "TRANSCODING" | "UPLOADING_PACKAGE" | "VALIDATING_PACKAGE" | "PUBLISHING" | "COMPLETED";
export interface AdminVideoDetail {
  id: number; title: string; status: VideoStatus;
  source: { videoFileId: number; status: VideoFileStatus } | null;
  processing: { jobId: number; status: JobStatus; stage: JobStage; progressPercent: number; processedMs: number | null; durationMs: number | null; attempt: number; errorCode: string | null } | null;
  mediaPackage: { id: number; profileVersion: string } | null;
}
export interface AdminVideoListItem {
  id: number; title: string; status: VideoStatus; activeVideoFileId: number | null;
  publishedMediaPackageId: number | null; createdAt: string; updatedAt: string;
  processing: AdminVideoDetail["processing"];
}
export const adminVideoApi = {
  getVideo: (id: number) => apiRequest<AdminVideoDetail>(`/api/admin/videos/${id}`),
  listVideos: ({ status, page = 0, size = 20 }: { status?: VideoStatus; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set("status", status);
    return apiRequest<PageResponse<AdminVideoListItem>>(`/api/admin/videos?${params}`);
  },
};
// TODO backend: title search and updatedAt sort parameters are not in the list contract.
