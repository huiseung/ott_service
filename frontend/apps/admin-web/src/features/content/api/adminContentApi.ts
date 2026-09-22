import { apiRequest } from "@/shared/lib/apiClient";
import type { PageResponse } from "@/shared/lib/page";

export type ContentType = "MOVIE" | "SERIES";
export type ContentStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type ContentImageType = "POSTER" | "LANDSCAPE" | "HERO";
export type EpisodeImageType = "THUMBNAIL";

export interface ContentListItem {
  id: number; type: ContentType; status: ContentStatus; originalCountry: string; originalLanguage: string;
  releaseDate: string | null; createdAt: string; updatedAt: string; landscapeImageUrl: string | null;
}
export interface ContentDetail extends Omit<ContentListItem, "landscapeImageUrl"> {
  localizations: { locale: string; title: string; shortDescription: string | null; description: string | null }[];
  genreCodes: string[]; availabilities: unknown[];
}
export interface AdminImage {
  type: ContentImageType | EpisodeImageType; url: string; width: number; height: number; fileSize: number; mimeType: string;
  createdAt: string; updatedAt: string;
}
export interface Season { id: number; seriesContentId: number; seasonNumber: number; status: ContentStatus }
export interface Episode { id: number; seasonId: number; episodeNumber: number; status: ContentStatus; releaseAt: string | null; localizations: { locale: string; title: string }[] }

function form(file: File) { const data = new FormData(); data.set("file", file); return data; }

export const adminContentApi = {
  list: ({ query, type, status, country, page = 0, size = 20 }: { query?: string; type?: ContentType; status?: ContentStatus; country?: string; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (query) params.set("query", query); if (type) params.set("type", type); if (status) params.set("status", status); if (country) params.set("country", country);
    return apiRequest<PageResponse<ContentListItem>>(`/api/admin/contents?${params}`);
  },
  get: (id: number) => apiRequest<ContentDetail>(`/api/admin/contents/${id}`),
  images: (contentId: number) => apiRequest<{ images: AdminImage[] }>(`/api/admin/contents/${contentId}/images`),
  uploadImage: (contentId: number, type: ContentImageType, file: File) => apiRequest<AdminImage>(`/api/admin/contents/${contentId}/images/${type}`, { method: "PUT", body: form(file) }),
  deleteImage: (contentId: number, type: ContentImageType) => apiRequest<void>(`/api/admin/contents/${contentId}/images/${type}`, { method: "DELETE" }),
  seasons: (contentId: number) => apiRequest<Season[]>(`/api/admin/contents/${contentId}/seasons`),
  episodes: (seasonId: number) => apiRequest<Episode[]>(`/api/admin/seasons/${seasonId}/episodes`),
  episodeImages: (episodeId: number) => apiRequest<{ images: AdminImage[] }>(`/api/admin/episodes/${episodeId}/images`),
  uploadEpisodeImage: (episodeId: number, type: EpisodeImageType, file: File) => apiRequest<AdminImage>(`/api/admin/episodes/${episodeId}/images/${type}`, { method: "PUT", body: form(file) }),
  deleteEpisodeImage: (episodeId: number, type: EpisodeImageType) => apiRequest<void>(`/api/admin/episodes/${episodeId}/images/${type}`, { method: "DELETE" }),
};
