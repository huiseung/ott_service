"use client";
import Link from "next/link";
import { useCallback, useState } from "react";
import { adminContentApi } from "../api/adminContentApi";
import { adminSeriesApi } from "../api/adminSeriesApi";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { ArtworkPreview } from "./ArtworkPreview";
import { MediaConnections } from "./MediaConnections";
import { SeriesEntryForm } from "./SeriesEntryForm";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";
import { MutationFeedback, type ContentMutation } from "./useContentMutation";

export function EpisodeList({ seasonId, seasonNumber, mutation }: { seasonId: number; seasonNumber: number; mutation: ContentMutation }) {
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminSeriesApi.episodes(seasonId, signal), [seasonId]));
  const [newRevision, setNewRevision] = useState(0);
  return <div className="metadata-entry"><h3>Season {seasonNumber} · Episodes</h3>
    <ResourceFeedback resource={resource} label="Episode 목록" />
    {!resource.loading && !resource.error && resource.data && <>
      {resource.data.length === 0 ? <p className="muted">등록된 Episode가 없습니다.</p> : <div className="table-wrap"><table>
        <caption className="sr-only">Season {seasonNumber} Episode 목록</caption>
        <thead><tr>{["Episode Number", "Localized Title", "Status", "Release At", "Thumbnail", "Media 연결", "작업"].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
        <tbody>{resource.data.map(episode => <tr key={episode.id}>
          <td>{episode.episodeNumber}<small>#{episode.id}</small></td>
          <td>{episode.localizations.length ? episode.localizations.map(item => <div key={item.id}>{item.title}<small>{item.locale}</small></div>) : <span className="muted">제목 미등록</span>}</td>
          <td><StatusBadge value={episode.status} /></td>
          <td>{episode.releaseAt ? <time dateTime={episode.releaseAt}>{new Date(episode.releaseAt).toLocaleString("ko-KR", { timeZone: "UTC" })} UTC</time> : "—"}</td>
          <td><EpisodeThumbnail id={episode.id} /></td>
          <td><MediaConnections id={episode.id} kind="episode" compact /></td>
          <td><Link className="button small" href={`/admin/episodes/${episode.id}`}>Episode 편집</Link></td>
        </tr>)}</tbody>
      </table></div>}
      <div className="metadata-entry"><h3>Episode 생성</h3><SeriesEntryForm key={newRevision} kind="Episode" pending={mutation.pending} onSave={async value => {
        const ok = await mutation.run("Episode 생성", "Episode Number는 Season 내에서 중복되지 않는 1 이상의 정수여야 합니다. Status와 UTC 공개 시각을 확인하세요.", async () => {
          const created = await adminSeriesApi.createEpisode(seasonId, { episodeNumber: value.number, status: value.status, releaseAt: value.releaseAt });
          resource.setData(current => [...(current ?? []), created].sort((a, b) => a.episodeNumber - b.episodeNumber));
        });
        if (ok) setNewRevision(value => value + 1);
      }} /></div>
      {mutation.scope.startsWith("Episode") && <MutationFeedback mutation={mutation} />}
    </>}
  </div>;
}

function EpisodeThumbnail({ id }: { id: number }) {
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminContentApi.episodeImages(id, signal), [id]));
  const thumbnail = resource.data?.images.find(image => image.type === "THUMBNAIL");
  return <div className="episode-thumbnail">
    <ResourceFeedback resource={resource} label="Thumbnail" />
    {!resource.loading && !resource.error && <><ArtworkPreview key={thumbnail?.url} url={thumbnail?.url} label="THUMBNAIL" /><button className="button small" onClick={resource.reload}>이미지 새로고침</button></>}
  </div>;
}

