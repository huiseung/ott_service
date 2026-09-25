import { apiRequest } from "@/shared/lib/apiClient";

export interface AnalyticsFilter { contentId: number; from: string; to: string }
export interface AnalyticsDashboardData extends AnalyticsFilter {
  queriedAt: string;
  reach: { playStarts: number; uniqueViewers: number; impressions: number; clicks: number; detailViews: number; ctaClicks: number; latestEventAt: string | null };
  engagement: { sessions: number; watchHours: number; averageWatchSeconds: number | null; eligibleSessions: number; completedSessions: number; completionRate: number | null };
  acquisition: { ctaClicks: number; checkoutCtas: number; convertedCtas: number; conversionRate: number | null; attributedSubscriptions: number; testSubscriptions: number };
  quality: { sessions: number; startupSamples: number; averageStartupMs: number | null; p95StartupMs: number | null; bufferCount: number; openBufferSessions: number; rebufferRatio: number | null; errorSessions: number; errorRate: number | null; fatalErrorSessions: number; fatalErrorRate: number | null };
  retention: { bucket: number; eligibleSessions: number; watchedSessions: number; rate: number | null }[];
  episodes: { episodeId: number; nextEpisodeId: number; eligibleViewers: number; convertedViewers: number; rate: number | null; mature: number }[];
  episodesTruncated: boolean;
  daily: { date: string; playStarts: number; uniqueViewers: number; impressions: number; clicks: number }[];
}

export function getAnalytics(filter: AnalyticsFilter, signal: AbortSignal) {
  const params = new URLSearchParams({ from: filter.from, to: filter.to });
  return apiRequest<AnalyticsDashboardData>(`/api/admin/analytics/contents/${filter.contentId}?${params}`, { signal });
}
