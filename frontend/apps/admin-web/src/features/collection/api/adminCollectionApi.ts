import { apiRequest } from "@/shared/lib/apiClient";
import type { PageResponse } from "@/shared/lib/page";
import type { ContentStatus, ContentType } from "@/features/content/api/adminContentApi";

export interface CollectionListItem { id: number; status: ContentStatus; minVisibleItems: number; version: number; createdAt: string; updatedAt: string }
export interface CollectionItem { id: number; contentId: number; type: ContentType; status: ContentStatus; displayOrder: number; title: string | null; landscapeImageUrl: string | null; createdAt: string }
export interface CollectionDetail extends CollectionListItem { localizations: { locale: string; title: string; description: string | null }[]; availabilities: unknown[]; items: CollectionItem[] }

export const adminCollectionApi = {
  list: ({ query, status, country, page = 0, size = 20 }: { query?: string; status?: ContentStatus; country?: string; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (query) params.set("query", query); if (status) params.set("status", status); if (country) params.set("country", country);
    return apiRequest<PageResponse<CollectionListItem>>(`/api/admin/collections?${params}`);
  },
  get: (id: number) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}`),
  addItems: (id: number, contentIds: number[]) => apiRequest<CollectionDetail>(`/api/admin/collections/${id}/items`, { method: "POST", body: JSON.stringify({ contentIds }) }),
};
