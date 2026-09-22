"use client";
import { use, useCallback, useEffect, useState } from "react";
import { adminCollectionApi, type CollectionDetail } from "@/features/collection/api/adminCollectionApi";
import { adminContentApi, type ContentListItem } from "@/features/content/api/adminContentApi";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { userError } from "@/shared/lib/apiClient";

export default function CollectionDetailPage({ params }: { params: Promise<{ collectionId: string }> }) {
  const { collectionId } = use(params); const id = Number(collectionId);
  const [collection, setCollection] = useState<CollectionDetail | null>(null); const [candidates, setCandidates] = useState<ContentListItem[]>([]); const [query, setQuery] = useState(""); const [error, setError] = useState("");
  const refresh = useCallback(async () => { try { setCollection(await adminCollectionApi.get(id)); setError(""); } catch (reason) { setError(userError(reason)); } }, [id]);
  useEffect(() => { const timer = setTimeout(() => void refresh(), 0); return () => clearTimeout(timer); }, [refresh]);
  useEffect(() => { adminContentApi.list({ query, status: "PUBLISHED", size: 10 }).then(page => setCandidates(page.content)).catch(() => setCandidates([])); }, [query]);
  async function add(contentId: number) { try { await adminCollectionApi.addItems(id, [contentId]); await refresh(); } catch (reason) { setError(userError(reason)); } }
  return <><div className="page-heading"><div><span className="eyebrow">COLLECTION / #{id}</span><h1>Collection 편성</h1><p>LANDSCAPE Artwork는 편성 카드와 Content Picker에 표시됩니다.</p></div></div>{error && <div className="panel error">{error}</div>}{collection && <><section className="panel"><div className="kv"><span>Status</span><StatusBadge value={collection.status} /><span>Min Visible</span><strong>{collection.minVisibleItems}</strong><span>Items</span><strong>{collection.items.length}</strong></div></section><section className="panel"><h2>Items</h2><div className="content-grid">{collection.items.map(item => <div className="content-card" key={item.id}><div className="artwork-preview">{item.landscapeImageUrl ? <img src={item.landscapeImageUrl} alt="" /> : <span>LANDSCAPE</span>}</div><strong>{item.title ?? `Content #${item.contentId}`}</strong><small>{item.type} · #{item.contentId}</small><StatusBadge value={item.status} /></div>)}</div></section><section className="panel"><h2>Content Picker</h2><div className="filters"><label>검색<input value={query} onChange={event => setQuery(event.target.value)} placeholder="title" /></label></div><div className="content-grid">{candidates.map(content => <div className="content-card" key={content.id}><div className="artwork-preview">{content.landscapeImageUrl ? <img src={content.landscapeImageUrl} alt="" /> : <span>LANDSCAPE</span>}</div><strong>Content #{content.id}</strong><small>{content.type}</small><button className="button small" onClick={() => void add(content.id)}>Add</button></div>)}</div></section></>}</>;
}
