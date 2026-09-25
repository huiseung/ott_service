import { Suspense } from "react";
import Link from "next/link";
import { ContentList } from "@/features/content/components/ContentList";

export default function ContentsPage() {
  return <>
    <div className="page-heading"><div><span className="eyebrow">CMS</span><h1>Content 관리</h1><p>작품 검색 및 등록 상태 조회</p></div><Link className="button primary" href="/admin/contents/new">+ Content 생성</Link></div>
    <Suspense fallback={<section className="panel" role="status">Content 목록을 불러오는 중입니다…</section>}>
      <ContentList />
    </Suspense>
  </>;
}
