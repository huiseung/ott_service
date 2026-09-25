"use client";
import { useState, type FormEvent } from "react";
import { adminContentApi, type ContentDetail, type ContentLocalization, type LocalizationRequest } from "../api/adminContentApi";
import { MutationFeedback, type ContentMutation } from "./useContentMutation";

export function ContentLocalizations({ content, onChange, mutation }: {
  content: ContentDetail; onChange: (content: ContentDetail) => void; mutation: ContentMutation;
}) {
  const [newRevision, setNewRevision] = useState(0);
  const hint = "Locale(최대 20자), 제목(필수·최대 300자), 요약(500자), 설명(4000자)을 확인하세요.";
  async function save(request: LocalizationRequest, existing?: ContentLocalization) {
    const ok = await mutation.run("Localization 저장", hint, async () => {
      if (!existing && content.localizations.some(item => item.locale === request.locale)) throw new Error("이미 등록된 Locale입니다. 해당 Locale의 편집 양식을 사용하세요.");
      if (existing) {
        const { title, shortDescription, description } = request;
        onChange(await adminContentApi.updateLocalization(content.id, existing.locale, { title, shortDescription, description }));
      } else onChange(await adminContentApi.addLocalization(content.id, request));
    });
    if (ok && !existing) setNewRevision(value => value + 1);
  }
  function remove(item: ContentLocalization) {
    if (!window.confirm(`${item.locale}의 제목과 설명을 삭제하시겠습니까?`)) return;
    void mutation.run("Localization 삭제", hint, async () => {
      await adminContentApi.deleteLocalization(content.id, item.locale);
      onChange({ ...content, localizations: content.localizations.filter(value => value.id !== item.id) });
    });
  }
  return <section className="panel" id="localization">
    <h2>Localization</h2><p className="muted">Locale별 작품 metadata입니다. 서비스 국가와 별개이며, Locale을 바꾸려면 새 Locale을 추가하세요.</p>
    {content.localizations.length === 0 && <p className="muted">등록된 Localization이 없습니다.</p>}
    {content.localizations.map(item => <div className="metadata-entry" key={item.id}>
      <h3>{item.locale}</h3>
      <LocalizationForm key={JSON.stringify(item)} initial={item} pending={mutation.pending} onSave={request => void save(request, item)} onDelete={() => remove(item)} />
    </div>)}
    <div className="metadata-entry"><h3>Locale 추가</h3><LocalizationForm key={newRevision} pending={mutation.pending} onSave={request => void save(request)} /></div>
    {mutation.scope.startsWith("Localization") && <MutationFeedback mutation={mutation} />}
  </section>;
}

function LocalizationForm({ initial, pending, onSave, onDelete }: {
  initial?: ContentLocalization; pending: boolean; onSave: (request: LocalizationRequest) => void; onDelete?: () => void;
}) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onSave({
      locale: initial?.locale ?? String(data.get("locale")).trim(),
      title: String(data.get("title")).trim(),
      shortDescription: String(data.get("shortDescription")).trim() || null,
      description: String(data.get("description")).trim() || null,
    });
  }
  return <form onSubmit={submit}><fieldset className="metadata-fields" disabled={pending}>
    {!initial && <label>Metadata Locale<input name="locale" required pattern=".*\S.*" maxLength={20} placeholder="ko-KR / en-US / ja-JP" /></label>}
    <label className="metadata-full">Title<input name="title" required pattern=".*\S.*" maxLength={300} defaultValue={initial?.title ?? ""} /></label>
    <label className="metadata-full">Short Description (선택, 최대 500자)<textarea name="shortDescription" maxLength={500} rows={2} defaultValue={initial?.shortDescription ?? ""} /></label>
    <label className="metadata-full">Description (선택, 최대 4000자)<textarea name="description" maxLength={4000} rows={5} defaultValue={initial?.description ?? ""} /></label>
    <div className="actions metadata-full"><button className="button primary">{initial ? "Localization 저장" : "Locale 추가"}</button>{onDelete && <button className="button danger" type="button" onClick={onDelete}>Localization 삭제</button>}</div>
  </fieldset></form>;
}
