"use client";
import Link from "next/link";
import { useCallback } from "react";
import { adminSeriesApi } from "../api/adminSeriesApi";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";
import { MediaVersionEditor, VideoConnectionForm } from "./MediaVersionEditor";

export function MediaConnections({ id, kind, compact = false }: { id: number; kind: "content" | "episode"; compact?: boolean }) {
  const resource = useAdminResource(useCallback((signal: AbortSignal) =>
    kind === "content" ? adminSeriesApi.contentMedia(id, signal) : adminSeriesApi.episodeMedia(id, signal), [id, kind]));
  return <div className="media-connections">
    <ResourceFeedback resource={resource} label="Media 연결" />
    {!resource.loading && !resource.error && resource.data && <>
      {resource.data.length === 0 ? <p className="muted">MediaVersion 없음</p> : resource.data.map(version =>
        <div className="media-connection" key={version.id}>
          <strong>{version.versionType} <small>MediaVersion #{version.id}</small></strong>
          <StatusBadge value={version.status} />
          {version.videoId === null ? <p className="muted">Video 미연결</p> :
            <div><Link className="content-id-link" href={`/admin/videos/${version.videoId}`}>{version.video?.title ?? `Video #${version.videoId}`}</Link>
              {version.video ? <><StatusBadge value={version.video.status} />{!compact && <p className="muted">Video #{version.video.id} · Source #{version.video.activeVideoFileId ?? "—"} · Media Package #{version.video.publishedMediaPackageId ?? "—"}</p>}</> : <p className="muted">연결 ID는 있으나 Video 상세 정보가 없습니다.</p>}
            </div>}
          {!compact && <>
            <MediaVersionEditor id={id} kind={kind} version={version} onSaved={resource.reload} />
            {version.videoId === null && <VideoConnectionForm versionId={version.id} onSaved={resource.reload} />}
          </>}
        </div>)}
      <button className="button small" onClick={resource.reload}>연결 상태 새로고침</button>
    </>}
    {!compact && <>
      <h3>새 편집본</h3><MediaVersionEditor id={id} kind={kind} onSaved={resource.reload} />
      <p className="muted">영상을 연결한 뒤 Content와 MediaVersion을 PUBLISHED로 저장하고 Availability를 AVAILABLE로 설정하세요. 인코딩 완료 후 지정된 국가·기간에 사용자에게 노출됩니다. SERIES는 Season과 Episode도 PUBLISHED여야 합니다.</p>
      <p><Link className="button" href="/admin/videos">Video 업로드 / 관리</Link></p>
    </>}
  </div>;
}

