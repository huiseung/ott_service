import { apiRequest } from "@/shared/lib/apiClient";
import type { PageResponse } from "@/shared/lib/page";

export type ContentType = "MOVIE" | "SERIES";
export type ContentStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type ContentImageType = "POSTER" | "LANDSCAPE" | "HERO";
export type EpisodeImageType = "THUMBNAIL";
export type AvailabilityStatus = "AVAILABLE" | "DISABLED";
export interface ContentRequest {
  type: ContentType; status: ContentStatus; originalCountry: string; originalLanguage: string; releaseDate: string | null;
}
export interface LocalizationRequest {
  locale: string; title: string; shortDescription: string | null; description: string | null;
}
export interface ContentLocalization extends LocalizationRequest { id: number; createdAt: string; updatedAt: string }
export interface AvailabilityRequest {
  countryCode: string; status: AvailabilityStatus; availableFrom: string; availableUntil: string | null;
}
export interface ContentAvailability extends AvailabilityRequest { id: number }

export interface ContentListItem {
  id: number; type: ContentType; status: ContentStatus; originalCountry: string; originalLanguage: string;
  releaseDate: string | null; createdAt: string; updatedAt: string; landscapeImageUrl: string | null;
}
export interface ContentDetail extends Omit<ContentListItem, "landscapeImageUrl"> {
  localizations: ContentLocalization[];
  genreCodes: string[]; availabilities: ContentAvailability[];
}
export interface AdminImage {
  type: ContentImageType | EpisodeImageType; url: string; width: number; height: number; fileSize: number; mimeType: string;
  createdAt: string; updatedAt: string;
}

function form(file: File) { const data = new FormData(); data.set("file", file); return data; }

export const adminContentApi = {
  list: ({ query, type, genre, status, country, page = 0, size = 20 }: { query?: string; type?: ContentType; genre?: string; status?: ContentStatus; country?: string; page?: number; size?: number } = {}, signal?: AbortSignal) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (query) params.set("query", query); if (type) params.set("type", type); if (status) params.set("status", status); if (country) params.set("country", country);
    if (genre) params.set("genre", genre);
    return apiRequest<PageResponse<ContentListItem>>(`/api/admin/contents?${params}`, { signal });
  },
  get: (id: number, signal?: AbortSignal) => apiRequest<ContentDetail>(`/api/admin/contents/${id}`, { signal }),
  create: (request: ContentRequest) => apiRequest<ContentDetail>("/api/admin/contents", { method: "POST", body: JSON.stringify(request) }),
  update: (id: number, request: ContentRequest) => apiRequest<ContentDetail>(`/api/admin/contents/${id}`, { method: "PUT", body: JSON.stringify(request) }),
  addLocalization: (id: number, request: LocalizationRequest) => apiRequest<ContentDetail>(`/api/admin/contents/${id}/localizations`, { method: "POST", body: JSON.stringify(request) }),
  updateLocalization: (id: number, locale: string, request: Omit<LocalizationRequest, "locale">) => apiRequest<ContentDetail>(`/api/admin/contents/${id}/localizations/${encodeURIComponent(locale)}`, { method: "PUT", body: JSON.stringify(request) }),
  deleteLocalization: (id: number, locale: string) => apiRequest<void>(`/api/admin/contents/${id}/localizations/${encodeURIComponent(locale)}`, { method: "DELETE" }),
  replaceGenres: (id: number, genreCodes: string[]) => apiRequest<ContentDetail>(`/api/admin/contents/${id}/genres`, { method: "PUT", body: JSON.stringify({ genreCodes }) }),
  setAvailability: (id: number, request: AvailabilityRequest) => apiRequest<ContentDetail>(`/api/admin/contents/${id}/availabilities/${encodeURIComponent(request.countryCode)}`, { method: "PUT", body: JSON.stringify(request) }),
  images: (contentId: number, signal?: AbortSignal) => apiRequest<{ images: AdminImage[] }>(`/api/admin/contents/${contentId}/images`, { signal }),
  uploadImage: (contentId: number, type: ContentImageType, file: File) => apiRequest<AdminImage>(`/api/admin/contents/${contentId}/images/${type}`, { method: "PUT", body: form(file) }),
  deleteImage: (contentId: number, type: ContentImageType) => apiRequest<void>(`/api/admin/contents/${contentId}/images/${type}`, { method: "DELETE" }),
  episodeImages: (episodeId: number, signal?: AbortSignal) => apiRequest<{ images: AdminImage[] }>(`/api/admin/episodes/${episodeId}/images`, { signal }),
  uploadEpisodeImage: (episodeId: number, type: EpisodeImageType, file: File) => apiRequest<AdminImage>(`/api/admin/episodes/${episodeId}/images/${type}`, { method: "PUT", body: form(file) }),
  deleteEpisodeImage: (episodeId: number, type: EpisodeImageType) => apiRequest<void>(`/api/admin/episodes/${episodeId}/images/${type}`, { method: "DELETE" }),
};
