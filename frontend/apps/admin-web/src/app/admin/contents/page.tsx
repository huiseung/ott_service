"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { adminContentApi, type ContentListItem, type ContentStatus, type ContentType } from "@/features/content/api/adminContentApi";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { userError } from "@/shared/lib/apiClient";

export default function ContentsPage() {
  const [items, setItems] = useState<ContentListItem[]>([]); const [query, setQuery] = useState(""); const [type, setType] = useState<ContentType | "">(""); const [status, setStatus] = useState<ContentStatus | "">(""); const [error, setError] = useState("");
  useEffect(() => { let active = true; adminContentApi.list({ query, type: type || undefined, status: status || undefined, size: 30 }).then(page => { if (active) { setItems(page.content); setError(""); } }).catch(reason => { if (active) setError(userError(reason)); }); return () => { active = false; }; }, [query, type, status]);
  return <><div className="page-heading"><div><span className="eyebrow">CMS</span><h1>Contents</h1><p>작품 검색과 Artwork 관리 진입점</p></div></div>
    <section className="panel"><div className="filters"><label>검색<input value={query} onChange={event => setQuery(event.target.value)} placeholder="title or id" /></label><label>Type<select value={type} onChange={event => setType(event.target.value as ContentType | "")}><option value="">ALL</option><option>MOVIE</option><option>SERIES</option></select></label><label>Status<select value={status} onChange={event => setStatus(event.target.value as ContentStatus | "")}><option value="">ALL</option><option>DRAFT</option><option>PUBLISHED</option><option>ARCHIVED</option></select></label></div>{error && <p className="error">{error}</p>}<div className="content-grid">{items.map(content => <Link className="content-card" href={`/admin/contents/${content.id}`} key={content.id}><div className="artwork-preview">{content.landscapeImageUrl ? <img src={content.landscapeImageUrl} alt="" /> : <span>LANDSCAPE</span>}</div><strong>Content #{content.id}</strong><small>{content.type}</small><StatusBadge value={content.status} /></Link>)}</div></section></>;
}
