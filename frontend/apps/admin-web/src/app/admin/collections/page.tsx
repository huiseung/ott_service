"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { adminCollectionApi, type CollectionListItem } from "@/features/collection/api/adminCollectionApi";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { userError } from "@/shared/lib/apiClient";

export default function CollectionsPage() {
  const [items, setItems] = useState<CollectionListItem[]>([]); const [query, setQuery] = useState(""); const [error, setError] = useState("");
  useEffect(() => { adminCollectionApi.list({ query, size: 30 }).then(page => { setItems(page.content); setError(""); }).catch(reason => setError(userError(reason))); }, [query]);
  return <><div className="page-heading"><div><span className="eyebrow">CMS</span><h1>Collections</h1><p>수동 편성과 국가별 Preview</p></div></div><section className="panel"><div className="filters"><label>검색<input value={query} onChange={event => setQuery(event.target.value)} placeholder="collection title" /></label></div>{error && <p className="error">{error}</p>}<div className="table-wrap"><table><thead><tr><th>ID</th><th>Status</th><th>Min Visible</th><th>Updated</th></tr></thead><tbody>{items.map(item => <tr key={item.id}><td><Link href={`/admin/collections/${item.id}`}>Collection #{item.id}</Link></td><td><StatusBadge value={item.status} /></td><td>{item.minVisibleItems}</td><td>{new Date(item.updatedAt).toLocaleString()}</td></tr>)}</tbody></table></div></section></>;
}
