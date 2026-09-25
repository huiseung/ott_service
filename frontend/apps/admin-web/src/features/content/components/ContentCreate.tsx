"use client";
import Link from "next/link";
import { useState } from "react";
import { adminContentApi, type ContentDetail } from "../api/adminContentApi";
import { ContentOverviewForm } from "./ContentOverviewForm";
import { MutationFeedback, useContentMutation } from "./useContentMutation";

export function ContentCreate() {
  const mutation = useContentMutation();
  const [created, setCreated] = useState<ContentDetail | null>(null);
  return <section className="panel">
    <h2>Overview</h2>
    <p className="muted">Content 생성 후 상세 화면에서 Locale별 제목, Genre 및 Availability를 등록하세요.</p>
    {created ? <div><p>Content #{created.id}가 생성되었습니다.</p><Link className="button primary" href={`/admin/contents/${created.id}`}>상세 metadata 편집</Link></div> :
      <ContentOverviewForm creating pending={mutation.pending} onSave={request => void mutation.run("Content 생성", "Type, Status, 두 자리 국가 코드와 원어(최대 20자)를 확인하세요.", async () => { setCreated(await adminContentApi.create(request)); })} />}
    <MutationFeedback mutation={mutation} />
  </section>;
}
