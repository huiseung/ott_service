"use client";
import Link from "next/link";
import { useCallback } from "react";
import { adminCollectionApi } from "../api/adminCollectionApi";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";
import { CollectionOverviewForm } from "./CollectionOverviewForm";
import { CollectionLocalizations } from "./CollectionLocalizations";
import { CollectionAvailabilities } from "./CollectionAvailabilities";
import { CollectionContents } from "./CollectionContents";
import { CollectionTerritoryPreview } from "./CollectionTerritoryPreview";
import { CollectionMutationFeedback, useCollectionMutation } from "./useCollectionMutation";

export function CollectionDetailEditor({ id }: { id: number }) {
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminCollectionApi.get(id, signal), [id]));
  const mutation = useCollectionMutation(id, resource.setData);
  const collection = resource.data;
  const disabled = mutation.pending || !mutation.synchronized || collection?.status === "ARCHIVED";
  const overview = collection ? { status: collection.status, minVisibleItems: collection.minVisibleItems } : undefined;
  return <>
    <div className="page-heading"><div><span className="eyebrow">CMS / COLLECTION #{id}</span><h1>Collection 편성</h1></div><Link className="button" href="/admin/collections">목록</Link></div>
    <ResourceFeedback resource={resource} label="Collection 상세" />
    {!resource.loading && !resource.error && collection && <>
      <nav className="metadata-nav" aria-label="Collection 편집 영역"><a className="button" href="#collection-overview">Overview</a><a className="button" href="#collection-localization">Localization</a><a className="button" href="#collection-availability">Availability</a><a className="button" href="#collection-contents">Contents</a><a className="button" href="#collection-preview">Territory Preview</a></nav>
      <div className="collection-feedback"><CollectionMutationFeedback mutation={mutation} /></div>
      {collection.status === "ARCHIVED" && <p className="panel muted">ARCHIVED Collection입니다. 현재 Backend API에서 수정·복원을 지원하지 않습니다.</p>}
      <section className="panel" id="collection-overview"><h2>Overview</h2>
        <div className="kv"><span>ID</span><strong>#{id}</strong><span>Content Count</span><strong>{collection.items.length}</strong><span>Updated At</span><time dateTime={collection.updatedAt}>{new Date(collection.updatedAt).toLocaleString("ko-KR")}</time></div>
        <CollectionOverviewForm key={JSON.stringify(overview)} initial={overview} disabled={disabled} onSave={request => void mutation.run("Overview 저장", "Status와 0 이상의 minVisibleItems를 확인하세요.", () => adminCollectionApi.update(id, { status: request.status, minVisibleItems: request.minVisibleItems }))} />
      </section>
      <CollectionLocalizations collection={collection} disabled={disabled} mutation={mutation} />
      <CollectionAvailabilities collection={collection} disabled={disabled} mutation={mutation} />
      <CollectionContents collection={collection} disabled={disabled} mutation={mutation} />
      <CollectionTerritoryPreview collection={collection} updating={mutation.pending} synchronized={mutation.synchronized} />
    </>}
  </>;
}
