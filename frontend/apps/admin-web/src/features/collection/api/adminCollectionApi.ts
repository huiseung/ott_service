import { apiRequest } from "@/shared/lib/apiClient";
import type { PageResponse } from "@/shared/lib/page";
import type { AvailabilityStatus, ContentStatus, ContentType } from "@/features/content/api/adminContentApi";

export interface CollectionListItem { id: number; status: ContentStatus; minVisibleItems: number; version: number; createdAt: string; updatedAt: string }
export interface CollectionItem { id: number; contentId: number; type: ContentType; status: ContentStatus; displayOrder: number; title: string | null; landscapeImageUrl: string | null; createdAt: string }
export interface CollectionLocalizationRequest { locale: string; title: string; description: string | null }
export interface CollectionLocalization extends CollectionLocalizationRequest { id: number; createdAt: string; updatedAt: string }
export interface CollectionAvailabilityRequest { countryCode: string; status: AvailabilityStatus; availableFrom: string; availableUntil: string | null }
export interface CollectionAvailability extends CollectionAvailabilityRequest { id: number }
export interface CollectionOverviewRequest { status: ContentStatus; minVisibleItems: number }
export interface CollectionCreateRequest extends CollectionOverviewRequest { localizations?: CollectionLocalizationRequest[] }
export interface CollectionDetail extends CollectionListItem { localizations: CollectionLocalization[]; availabilities: CollectionAvailability[]; items: CollectionItem[] }
export type CollectionPreviewReason =
  | "COLLECTION_NOT_PUBLISHED" | "COLLECTION_TERRITORY_NOT_CONFIGURED" | "COLLECTION_TERRITORY_DISABLED"
  | "COLLECTION_NOT_YET_AVAILABLE" | "COLLECTION_AVAILABILITY_EXPIRED"
  | "CONTENT_NOT_PUBLISHED" | "CONTENT_TERRITORY_NOT_CONFIGURED" | "CONTENT_TERRITORY_DISABLED"
  | "CONTENT_NOT_YET_AVAILABLE" | "CONTENT_AVAILABILITY_EXPIRED";
export interface CollectionPreview {
  collectionId: number; countryCode: string; collectionVisible: boolean; collectionReason: CollectionPreviewReason | null;
  totalItems: number; visibleItems: number; minVisibleItems: number; displayable: boolean;
  items: { contentId: number; displayOrder: number; visible: boolean; reason: CollectionPreviewReason | null }[];
}

export const adminCollectionApi = {
  list: ({ query, status, country, page = 0, size = 10 }: { query?: string; status?: ContentStatus; country?: string; page?: number; size?: number } = {}, signal?: AbortSignal) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (query) params.set("query", query); if (status) params.set("status", status); if (country) params.set("country", country);
    return apiRequest<PageResponse<CollectionListItem>>(`/api/admin/collections?${params}`, { signal });
  },
  get: (id: number, signal?: AbortSignal) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}`, { signal }),
  preview: (id: number, country: string, signal?: AbortSignal) => apiRequest<CollectionPreview>(`/api/admin/collections/${id}/preview?${new URLSearchParams({ country })}`, { signal }),
  create: (request: CollectionCreateRequest) => apiRequest<CollectionDetail>("/api/admin/collections", { method: "POST", body: JSON.stringify(request) }),
  update: (id: number, request: CollectionOverviewRequest) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}`, { method: "PATCH", body: JSON.stringify(request) }),
  addLocalization: (id: number, request: CollectionLocalizationRequest) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}/localizations`, { method: "POST", body: JSON.stringify(request) }),
  updateLocalization: (id: number, locale: string, request: Omit<CollectionLocalizationRequest, "locale">) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}/localizations/${encodeURIComponent(locale)}`, { method: "PUT", body: JSON.stringify(request) }),
  deleteLocalization: (id: number, locale: string) => apiRequest<void>(`/api/admin/collections/${id}/localizations/${encodeURIComponent(locale)}`, { method: "DELETE" }),
  setAvailability: (id: number, request: CollectionAvailabilityRequest) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}/availabilities/${encodeURIComponent(request.countryCode)}`, { method: "PUT", body: JSON.stringify(request) }),
  deleteAvailability: (id: number, country: string) => apiRequest<void>(`/api/admin/collections/${id}/availabilities/${encodeURIComponent(country)}`, { method: "DELETE" }),
  addItems: (id: number, contentIds: number[]) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}/items`, { method: "POST", body: JSON.stringify({ contentIds }) }),
  removeItem: (id: number, contentId: number) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}/items/${contentId}`, { method: "DELETE" }),
  reorder: (id: number, contentIds: number[]) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}/items/order`, { method: "PUT", body: JSON.stringify({ contentIds }) }),
};
