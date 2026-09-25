"use client";
import Link from "next/link";
import { useCallback } from "react";
import { adminSeriesApi } from "../api/adminSeriesApi";
import { SeriesEntryForm } from "./SeriesEntryForm";
import { EpisodeLocalizations } from "./EpisodeLocalizations";
import { EpisodeArtwork } from "./ContentArtwork";
import { MediaConnections } from "./MediaConnections";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";
import { MutationFeedback, useContentMutation } from "./useContentMutation";

export function EpisodeDetailEditor({ id }: { id: number }) {
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminSeriesApi.episode(id, signal), [id]));
  const mutation = useContentMutation();
  const episode = resource.data;
  const initial = episode ? { number: episode.episodeNumber, status: episode.status, releaseAt: episode.releaseAt } : undefined;
  return <>
    <div className="page-heading"><div><span className="eyebrow">CMS / EPISODE #{id}</span><h1>Episode 편집</h1></div><Link className="button" href="/admin/contents">Content 목록</Link></div>
    <ResourceFeedback resource={resource} label="Episode" />
    {!resource.loading && !resource.error && episode && <>
      <EpisodeParentLink key={episode.seasonId} seasonId={episode.seasonId} />
      <section className="panel"><h2>Episode {episode.episodeNumber} · Overview</h2><p className="muted">Release At은 UTC입니다. 비워 두면 공개 시각을 지정하지 않습니다.</p>
        <SeriesEntryForm key={JSON.stringify(initial)} kind="Episode" initial={initial} pending={mutation.pending} onSave={value => {
          if (value.status === "ARCHIVED" && episode.status !== "ARCHIVED" && !window.confirm("Episode를 ARCHIVED로 변경하시겠습니까?")) return;
          void mutation.run("Episode 저장", "Episode Number는 Season 내에서 중복되지 않는 1 이상의 정수여야 합니다. Status와 UTC 공개 시각을 확인하세요.", async () => {
            resource.setData(await adminSeriesApi.updateEpisode(id, { episodeNumber: value.number, status: value.status, releaseAt: value.releaseAt }));
          });
        }} />
        {mutation.scope === "Episode 저장" && <MutationFeedback mutation={mutation} />}
      </section>
      <EpisodeLocalizations episode={episode} onChange={resource.setData} mutation={mutation} />
      <EpisodeArtwork id={id} />
      <section className="panel"><h2>Media 연결 상태</h2><MediaConnections id={id} kind="episode" /></section>
    </>}
  </>;
}

function EpisodeParentLink({ seasonId }: { seasonId: number }) {
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminSeriesApi.season(seasonId, signal), [seasonId]));
  return <div className="metadata-nav"><ResourceFeedback resource={resource} label="상위 Season" />{!resource.loading && !resource.error && resource.data && <Link className="button" href={`/admin/contents/${resource.data.seriesContentId}#series`}>Content #{resource.data.seriesContentId} · Season {resource.data.seasonNumber}</Link>}</div>;
}

