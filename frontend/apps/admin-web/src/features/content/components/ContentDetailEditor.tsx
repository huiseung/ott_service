"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { adminContentApi, type ContentDetail, type ContentRequest } from "../api/adminContentApi";
import { ApiError, userError } from "@/shared/lib/apiClient";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { ContentOverviewForm } from "./ContentOverviewForm";
import { ContentLocalizations } from "./ContentLocalizations";
import { ContentGenres } from "./ContentGenres";
import { ContentAvailabilities } from "./ContentAvailabilities";
import { ContentArtwork } from "./ContentArtwork";
import { SeriesManager } from "./SeriesManager";
import { MediaConnections } from "./MediaConnections";
import { MutationFeedback, useContentMutation } from "./useContentMutation";

export function ContentDetailEditor({ id }: { id: number }) {
  const [content, setContent] = useState<ContentDetail | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const mutation = useContentMutation();

  useEffect(() => {
    const controller = new AbortController();
    adminContentApi.get(id, controller.signal).then(data => {
      if (!controller.signal.aborted) { setContent(data); setLoading(false); }
    }).catch(reason => {
      if (!controller.signal.aborted) {
        setError(reason instanceof ApiError && reason.status === 404 ? "Content를 찾을 수 없습니다." : userError(reason));
        setLoading(false);
      }
    });
    return () => controller.abort();
  }, [id, revision]);

  function saveOverview(request: ContentRequest) {
    if (request.status === "ARCHIVED" && content?.status !== "ARCHIVED" && !window.confirm("Content 상태를 ARCHIVED로 변경하시겠습니까?")) return;
    void mutation.run("Overview 저장", "Type, Status, 두 자리 Original Country와 Original Language(최대 20자)를 확인하세요.", async () => {
      setContent(await adminContentApi.update(id, request));
    });
  }

  const overview: ContentRequest | undefined = content ? {
    type: content.type, status: content.status, originalCountry: content.originalCountry,
    originalLanguage: content.originalLanguage, releaseDate: content.releaseDate,
  } : undefined;

  return <>
    <div className="page-heading"><div><span className="eyebrow">CMS / CONTENT #{id}</span><h1>Content 상세</h1><p>작품 기본 정보 및 글로벌 metadata 관리</p></div><div className="actions"><Link className="button" href={`/admin/analytics?contentId=${id}`}>분석 보기</Link><Link className="button" href="/admin/contents">목록</Link></div></div>
    {loading ? <section className="panel" role="status">Content를 불러오는 중입니다…</section> :
      error ? <section className="panel"><p className="error" role="alert">{error}</p><button className="button" onClick={() => { setError(""); setLoading(true); setRevision(value => value + 1); }}>다시 시도</button></section> :
      content && <>
        <nav className="metadata-nav" aria-label="Content 편집 영역"><a className="button" href="#overview">Overview</a><a className="button" href="#localization">Localization</a><a className="button" href="#genres">Genres</a><a className="button" href="#availability">Availability</a><a className="button" href="#artwork">Artwork</a>{content.type === "SERIES" ? <a className="button" href="#series">Series</a> : <a className="button" href="#media">Media</a>}</nav>
        <section className="panel" id="overview"><h2>Overview</h2>
          <div className="kv"><span>Content ID</span><strong>#{content.id}</strong><span>저장된 Status</span><StatusBadge value={content.status} /><span>Created At</span><time dateTime={content.createdAt}>{new Date(content.createdAt).toLocaleString("ko-KR")}</time><span>Updated At</span><time dateTime={content.updatedAt}>{new Date(content.updatedAt).toLocaleString("ko-KR")}</time></div>
          <ContentOverviewForm key={JSON.stringify(overview)} initial={overview} pending={mutation.pending} onSave={saveOverview} />
          {mutation.scope.startsWith("Overview") && <MutationFeedback mutation={mutation} />}
        </section>
        <ContentLocalizations content={content} onChange={setContent} mutation={mutation} />
        <ContentGenres content={content} onChange={setContent} mutation={mutation} />
        <ContentAvailabilities content={content} onChange={setContent} mutation={mutation} />
        <ContentArtwork id={id} />
        {content.type === "SERIES" ? <SeriesManager contentId={id} /> : <section className="panel" id="media"><h2>Media 연결 상태</h2><MediaConnections id={id} kind="content" /></section>}
      </>}
  </>;
}

