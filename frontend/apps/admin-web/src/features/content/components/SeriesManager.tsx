"use client";
import { useCallback, useState } from "react";
import { adminSeriesApi } from "../api/adminSeriesApi";
import { SeriesEntryForm } from "./SeriesEntryForm";
import { EpisodeList } from "./EpisodeList";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";
import { MutationFeedback, useContentMutation } from "./useContentMutation";

export function SeriesManager({ contentId }: { contentId: number }) {
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminSeriesApi.seasons(contentId, signal), [contentId]));
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [newRevision, setNewRevision] = useState(0);
  const mutation = useContentMutation();
  const selected = resource.data?.find(season => season.id === selectedId) ?? resource.data?.[0];
  const hint = "Season Number는 작품 내에서 중복되지 않는 1 이상의 정수여야 합니다. Status를 확인하세요.";
  return <section className="panel" id="series"><h2>Series · Seasons</h2>
    <ResourceFeedback resource={resource} label="Season 목록" />
    {!resource.loading && !resource.error && resource.data && <>
      <p className="muted">Season 삭제는 현재 API에서 지원하지 않습니다.</p>
      {resource.data.length === 0 ? <p className="muted">등록된 Season이 없습니다.</p> :
        <label className="season-select">Season 선택<select value={selected?.id ?? ""} disabled={mutation.pending} onChange={event => setSelectedId(Number(event.target.value))}>{resource.data.map(season => <option key={season.id} value={season.id}>Season {season.seasonNumber} · {season.status} · #{season.id}</option>)}</select></label>}
      {selected && <>
        <h3>Season {selected.seasonNumber} 편집</h3>
        <SeriesEntryForm key={JSON.stringify(selected)} kind="Season" initial={{ number: selected.seasonNumber, status: selected.status }} pending={mutation.pending} onSave={value => {
          if (value.status === "ARCHIVED" && selected.status !== "ARCHIVED" && !window.confirm("Season을 ARCHIVED로 변경하시겠습니까?")) return;
          void mutation.run("Season 저장", hint, async () => {
            const updated = await adminSeriesApi.updateSeason(selected.id, { seasonNumber: value.number, status: value.status });
            resource.setData(current => (current ?? []).map(item => item.id === updated.id ? updated : item).sort((a, b) => a.seasonNumber - b.seasonNumber));
            setSelectedId(updated.id);
          });
        }} />
      </>}
      <details className="metadata-entry"><summary>Season 생성</summary><SeriesEntryForm key={newRevision} kind="Season" pending={mutation.pending} onSave={async value => {
        const ok = await mutation.run("Season 생성", hint, async () => {
          const created = await adminSeriesApi.createSeason(contentId, { seasonNumber: value.number, status: value.status });
          resource.setData(current => [...(current ?? []), created].sort((a, b) => a.seasonNumber - b.seasonNumber));
          setSelectedId(created.id);
        });
        if (ok) setNewRevision(value => value + 1);
      }} /></details>
      {mutation.scope.startsWith("Season") && <MutationFeedback mutation={mutation} />}
      {selected && <EpisodeList key={selected.id} seasonId={selected.id} seasonNumber={selected.seasonNumber} mutation={mutation} />}
    </>}
  </section>;
}

