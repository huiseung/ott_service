import { apiRequest } from "@/shared/lib/apiClient";
import type { ContentStatus } from "./adminContentApi";
import type { VideoStatus } from "@/features/video/api/adminVideoApi";

export interface SeasonRequest { seasonNumber: number; status: ContentStatus }
export interface Season extends SeasonRequest { id: number; seriesContentId: number; createdAt: string; updatedAt: string }
export interface EpisodeRequest { episodeNumber: number; status: ContentStatus; releaseAt: string | null }
export interface EpisodeLocalizationRequest { locale: string; title: string; description: string | null }
export interface EpisodeLocalization extends EpisodeLocalizationRequest { id: number; createdAt: string; updatedAt: string }
export interface Episode extends EpisodeRequest {
  id: number; seasonId: number; createdAt: string; updatedAt: string; localizations: EpisodeLocalization[];
}
export interface MediaVersion {
  id: number; contentId: number | null; episodeId: number | null; videoId: number | null;
  versionType: "ORIGINAL" | "CENSORED" | "DIRECTORS_CUT" | "LOCALIZED_CUT"; status: ContentStatus;
  createdAt: string; updatedAt: string;
  video: { id: number; title: string; status: VideoStatus; activeVideoFileId: number | null; publishedMediaPackageId: number | null } | null;
}

export const adminSeriesApi = {
  createMedia: (kind: "content" | "episode", id: number, request: Pick<MediaVersion, "versionType" | "status">) => apiRequest<MediaVersion>(`/api/admin/${kind === "content" ? "contents" : "episodes"}/${id}/media-versions`, { method: "POST", body: JSON.stringify(request) }),
  updateMedia: (id: number, request: Pick<MediaVersion, "versionType" | "status">) => apiRequest<MediaVersion>(`/api/admin/media-versions/${id}`, { method: "PATCH", body: JSON.stringify(request) }),
  attachVideo: (id: number, videoId: number) => apiRequest<MediaVersion>(`/api/admin/media-versions/${id}/video`, { method: "PUT", body: JSON.stringify({ videoId }) }),
  seasons: (id: number, signal?: AbortSignal) => apiRequest<Season[]>(`/api/admin/contents/${id}/seasons`, { signal }),
  season: (id: number, signal?: AbortSignal) => apiRequest<Season>(`/api/admin/seasons/${id}`, { signal }),
  createSeason: (id: number, request: SeasonRequest) => apiRequest<Season>(`/api/admin/contents/${id}/seasons`, { method: "POST", body: JSON.stringify(request) }),
  updateSeason: (id: number, request: SeasonRequest) => apiRequest<Season>(`/api/admin/seasons/${id}`, { method: "PATCH", body: JSON.stringify(request) }),
  episodes: (id: number, signal?: AbortSignal) => apiRequest<Episode[]>(`/api/admin/seasons/${id}/episodes`, { signal }),
  episode: (id: number, signal?: AbortSignal) => apiRequest<Episode>(`/api/admin/episodes/${id}`, { signal }),
  createEpisode: (id: number, request: EpisodeRequest) => apiRequest<Episode>(`/api/admin/seasons/${id}/episodes`, { method: "POST", body: JSON.stringify(request) }),
  updateEpisode: (id: number, request: EpisodeRequest) => apiRequest<Episode>(`/api/admin/episodes/${id}`, { method: "PATCH", body: JSON.stringify(request) }),
  addLocalization: (id: number, request: EpisodeLocalizationRequest) => apiRequest<Episode>(`/api/admin/episodes/${id}/localizations`, { method: "POST", body: JSON.stringify(request) }),
  updateLocalization: (id: number, locale: string, request: Omit<EpisodeLocalizationRequest, "locale">) => apiRequest<Episode>(`/api/admin/episodes/${id}/localizations/${encodeURIComponent(locale)}`, { method: "PUT", body: JSON.stringify(request) }),
  deleteLocalization: (id: number, locale: string) => apiRequest<void>(`/api/admin/episodes/${id}/localizations/${encodeURIComponent(locale)}`, { method: "DELETE" }),
  episodeMedia: (id: number, signal?: AbortSignal) => apiRequest<MediaVersion[]>(`/api/admin/episodes/${id}/media-versions`, { signal }),
  contentMedia: (id: number, signal?: AbortSignal) => apiRequest<MediaVersion[]>(`/api/admin/contents/${id}/media-versions`, { signal }),
};
