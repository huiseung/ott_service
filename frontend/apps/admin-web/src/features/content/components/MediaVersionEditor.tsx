"use client";
import { useCallback, useState } from "react";
import { adminSeriesApi, type MediaVersion } from "../api/adminSeriesApi";
import { adminVideoApi } from "@/features/video/api/adminVideoApi";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";
import { MutationFeedback, useContentMutation } from "./useContentMutation";

const versionTypes = ["ORIGINAL", "CENSORED", "DIRECTORS_CUT", "LOCALIZED_CUT"] as const;
const statuses = ["DRAFT", "PUBLISHED", "ARCHIVED"] as const;

export function MediaVersionEditor({ id, kind, version, onSaved }: {
  id: number; kind: "content" | "episode"; version?: MediaVersion; onSaved: () => void;
}) {
  const mutation = useContentMutation();
  const [versionType, setVersionType] = useState<MediaVersion["versionType"]>(version?.versionType ?? "ORIGINAL");
  const [status, setStatus] = useState<MediaVersion["status"]>(version?.status ?? "DRAFT");
  return <form onSubmit={event => {
    event.preventDefault();
    void mutation.run("MediaVersion 저장", "같은 편집본 유형은 한 번만 등록할 수 있습니다.", async () => {
      const request = { versionType, status };
      if (version) await adminSeriesApi.updateMedia(version.id, request);
      else await adminSeriesApi.createMedia(kind, id, request);
      onSaved();
    });
  }}><fieldset className="metadata-fields" disabled={mutation.pending}>
    <label>편집본<select value={versionType} onChange={event => { const value = versionTypes.find(type => type === event.target.value); if (value) setVersionType(value); }}>{versionTypes.map(type => <option key={type}>{type}</option>)}</select></label>
    <label>공개 상태<select value={status} onChange={event => { const value = statuses.find(item => item === event.target.value); if (value) setStatus(value); }}>{statuses.map(item => <option key={item}>{item}</option>)}</select></label>
    <button className="button">{version ? "편집본 저장" : "편집본 생성"}</button>
  </fieldset><MutationFeedback mutation={mutation} /></form>;
}

export function VideoConnectionForm({ versionId, onSaved }: { versionId: number; onSaved: () => void }) {
  const [page, setPage] = useState(0);
  const [videoId, setVideoId] = useState("");
  const mutation = useContentMutation();
  const videos = useAdminResource(useCallback(() => adminVideoApi.listVideos({ page, size: 20 }), [page]));
  return <><ResourceFeedback resource={videos} label="업로드 영상" /><form onSubmit={event => {
    event.preventDefault();
    void mutation.run("Video 연결", "이미 연결된 영상은 다시 연결할 수 없습니다.", async () => {
      await adminSeriesApi.attachVideo(versionId, Number(videoId));
      onSaved();
    });
  }}>
    <fieldset className="metadata-fields" disabled={mutation.pending}>
      <label>업로드한 Video<select required value={videoId} onChange={event => setVideoId(event.target.value)}>
        <option value="">영상을 선택하세요</option>
        {!videos.loading && !videos.error && videos.data?.content.map(video => <option key={video.id} value={video.id}>#{video.id} {video.title} ({video.status})</option>)}
      </select></label>
      <div className="actions"><button className="button small" type="button" disabled={page === 0 || videos.loading} onClick={() => { setVideoId(""); setPage(value => value - 1); }}>이전</button><button className="button small" type="button" disabled={!videos.data || videos.data.last || videos.loading} onClick={() => { setVideoId(""); setPage(value => value + 1); }}>다음</button></div>
      <button className="button primary" disabled={!videoId || videos.loading || !!videos.error}>선택한 Video 연결</button>
    </fieldset><MutationFeedback mutation={mutation} />
  </form></>;
}
