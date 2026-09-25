"use client";
import Link from "next/link";
import { useRef, useState } from "react";
import { adminCollectionApi, type CollectionDetail } from "../api/adminCollectionApi";
import { collectionError } from "../api/collectionError";
import { CollectionOverviewForm } from "./CollectionOverviewForm";

export function CollectionCreate() {
  const lock = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState<CollectionDetail | null>(null);
  return <section className="panel"><h2>Overview · 첫 Localization</h2>
    {created ? <div role="status"><p>Collection #{created.id} 생성이 완료되었습니다.</p><Link className="button primary" href={`/admin/collections/${created.id}`}>Collection 편집</Link></div> :
      <CollectionOverviewForm creating disabled={pending} onSave={async request => {
        if (lock.current) return;
        lock.current = true; setPending(true); setError("");
        try { setCreated(await adminCollectionApi.create(request)); }
        catch (reason) { setError(collectionError(reason, "Status, 0 이상의 minVisibleItems, Locale(20자), Title(300자), Description(4000자)을 확인하세요.")); }
        finally { lock.current = false; setPending(false); }
      }} />}
    {pending && <p role="status">Collection 생성 중…</p>}
    {error && <p className="error" role="alert">{error}</p>}
  </section>;
}
