import { apiRequest } from "@/shared/lib/apiClient";
import type { PageResponse } from "@/shared/lib/page";
import type { JobStage, JobStatus } from "@/features/video/api/adminVideoApi";
export interface ProcessingJobSummary {
  id: number; jobKey: string; videoId: number; videoFileId: number; profileVersion: string;
  status: JobStatus; stage: JobStage; progressPercent: number; attempt: number;
  processedMs: number | null; durationMs: number | null; generation: number;
  workerId: string | null; leaseUntil: string | null; lastHeartbeatAt: string | null;
  nextRunAt: string | null; errorCode: string | null; errorMessage: string | null;
  createdAt: string; startedAt: string | null; completedAt: string | null; updatedAt: string;
}
export const mediaProcessingApi = {
  retry: (jobId: number) => apiRequest<void>(`/api/admin/media-processing-jobs/${jobId}/retry`, { method: "POST" }),
  listJobs: ({ status, videoId, page = 0, size = 20 }: { status?: JobStatus; videoId?: number; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set("status", status);
    if (videoId) params.set("videoId", String(videoId));
    return apiRequest<PageResponse<ProcessingJobSummary>>(`/api/admin/media-processing-jobs?${params}`);
  },
};
