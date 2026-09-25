import Link from "next/link";
import { Suspense } from "react";
import { CollectionList } from "@/features/collection/components/CollectionList";
export default function CollectionsPage() {
  return <><div className="page-heading"><div><span className="eyebrow">CMS</span><h1>Collections</h1><p>Collection metadata와 Content 편성 관리</p></div><Link className="button primary" href="/admin/collections/new">+ Collection 생성</Link></div>
    <Suspense fallback={<section className="panel" role="status">Collection 목록을 불러오는 중…</section>}><CollectionList /></Suspense></>;
}
