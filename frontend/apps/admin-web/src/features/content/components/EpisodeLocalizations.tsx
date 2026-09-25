"use client";
import { useState, type FormEvent } from "react";
import { adminSeriesApi, type Episode, type EpisodeLocalization, type EpisodeLocalizationRequest } from "../api/adminSeriesApi";
import { MutationFeedback, type ContentMutation } from "./useContentMutation";

export function EpisodeLocalizations({ episode, onChange, mutation }: {
  episode: Episode; onChange: (episode: Episode) => void; mutation: ContentMutation;
}) {
  const [revision, setRevision] = useState(0);
  const hint = "Locale(최대 20자), 제목(필수·최대 300자), 설명(최대 4000자)을 확인하세요. Locale은 중복 등록할 수 없습니다.";
  async function save(request: EpisodeLocalizationRequest, existing?: EpisodeLocalization) {
    const ok = await mutation.run("Episode Localization 저장", hint, async () => {
      if (!existing && episode.localizations.some(item => item.locale === request.locale)) throw new Error("이미 등록된 Locale입니다. 기존 양식에서 수정하세요.");
      if (existing) onChange(await adminSeriesApi.updateLocalization(episode.id, existing.locale, { title: request.title, description: request.description }));
      else onChange(await adminSeriesApi.addLocalization(episode.id, request));
    });
    if (ok && !existing) setRevision(value => value + 1);
  }
  function remove(item: EpisodeLocalization) {
    if (!window.confirm(`Episode의 ${item.locale} 제목과 설명을 삭제하시겠습니까?`)) return;
    void mutation.run("Episode Localization 삭제", hint, async () => {
      await adminSeriesApi.deleteLocalization(episode.id, item.locale);
      onChange({ ...episode, localizations: episode.localizations.filter(value => value.id !== item.id) });
    });
  }
  return <section className="panel"><h2>Episode Localization</h2><p className="muted">Locale별 제목과 설명입니다. Locale은 서비스 국가와 별개입니다.</p>
    {episode.localizations.length === 0 && <p className="muted">등록된 Localization이 없습니다.</p>}
    {episode.localizations.map(item => <div className="metadata-entry" key={item.id}><h3>{item.locale}</h3><LocalizationForm key={JSON.stringify(item)} initial={item} pending={mutation.pending} onSave={value => void save(value, item)} onDelete={() => remove(item)} /></div>)}
    <div className="metadata-entry"><h3>Locale 추가</h3><LocalizationForm key={revision} pending={mutation.pending} onSave={value => void save(value)} /></div>
    {mutation.scope.startsWith("Episode Localization") && <MutationFeedback mutation={mutation} />}
  </section>;
}

function LocalizationForm({ initial, pending, onSave, onDelete }: {
  initial?: EpisodeLocalization; pending: boolean; onSave: (request: EpisodeLocalizationRequest) => void; onDelete?: () => void;
}) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onSave({ locale: initial?.locale ?? String(data.get("locale")).trim(), title: String(data.get("title")).trim(), description: String(data.get("description")).trim() || null });
  }
  return <form onSubmit={submit}><fieldset className="metadata-fields" disabled={pending}>
    {!initial && <label>Metadata Locale<input name="locale" required pattern=".*\S.*" maxLength={20} placeholder="ko-KR / en-US / ja-JP" /></label>}
    <label className="metadata-full">Title<input name="title" required pattern=".*\S.*" maxLength={300} defaultValue={initial?.title ?? ""} /></label>
    <label className="metadata-full">Description (선택, 최대 4000자)<textarea name="description" rows={5} maxLength={4000} defaultValue={initial?.description ?? ""} /></label>
    <div className="actions metadata-full"><button className="button primary">{initial ? "Localization 저장" : "Locale 추가"}</button>{onDelete && <button type="button" className="button danger" onClick={onDelete}>Localization 삭제</button>}</div>
  </fieldset></form>;
}
