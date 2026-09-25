"use client";
import { useState, type FormEvent } from "react";
import { adminCollectionApi, type CollectionDetail, type CollectionLocalization, type CollectionLocalizationRequest } from "../api/adminCollectionApi";
import type { CollectionMutation } from "./useCollectionMutation";

export function CollectionLocalizations({ collection, disabled, mutation }: {
  collection: CollectionDetail; disabled: boolean; mutation: CollectionMutation;
}) {
  const [revision, setRevision] = useState(0);
  const hint = "Locale(20자), 필수 Title(300자), Description(4000자)을 확인하세요. Locale은 중복될 수 없습니다.";
  async function save(request: CollectionLocalizationRequest, existing?: CollectionLocalization) {
    const ok = await mutation.run("Localization 저장", hint, async () => {
      if (!existing && collection.localizations.some(item => item.locale === request.locale)) throw new Error("이미 등록된 Locale입니다. 기존 Locale 양식에서 수정하세요.");
      return existing ? adminCollectionApi.updateLocalization(collection.id, existing.locale, { title: request.title, description: request.description }) : adminCollectionApi.addLocalization(collection.id, request);
    });
    if (ok && !existing) setRevision(value => value + 1);
  }
  return <section className="panel" id="collection-localization"><h2>Localization</h2><p className="muted">Locale별 Collection 제목과 설명입니다. 서비스 대상 국가와 별개입니다.</p>
    {collection.localizations.length === 0 && <p className="muted">등록된 Localization이 없습니다.</p>}
    {collection.localizations.map(item => <div className="metadata-entry" key={item.id}><h3>{item.locale}</h3><LocalizationForm key={JSON.stringify(item)} initial={item} disabled={disabled} onSave={value => void save(value, item)} onDelete={() => {
      if (!window.confirm(`${item.locale}의 제목과 설명을 삭제하시겠습니까?`)) return;
      void mutation.run("Localization 삭제", hint, async () => { await adminCollectionApi.deleteLocalization(collection.id, item.locale); return adminCollectionApi.get(collection.id); });
    }} /></div>)}
    <div className="metadata-entry"><h3>Locale 추가</h3><LocalizationForm key={revision} disabled={disabled} onSave={value => void save(value)} /></div>
  </section>;
}

function LocalizationForm({ initial, disabled, onSave, onDelete }: {
  initial?: CollectionLocalization; disabled: boolean; onSave: (request: CollectionLocalizationRequest) => void; onDelete?: () => void;
}) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onSave({ locale: initial?.locale ?? String(data.get("locale")).trim(), title: String(data.get("title")).trim(), description: String(data.get("description")).trim() || null });
  }
  return <form onSubmit={submit}><fieldset className="metadata-fields" disabled={disabled}>
    {!initial && <label>Metadata Locale<input name="locale" required pattern=".*\S.*" maxLength={20} placeholder="ko-KR / en-US / ja-JP" /></label>}
    <label className="metadata-full">Title<input name="title" required pattern=".*\S.*" maxLength={300} defaultValue={initial?.title ?? ""} /></label>
    <label className="metadata-full">Description (선택, 최대 4000자)<textarea name="description" rows={4} maxLength={4000} defaultValue={initial?.description ?? ""} /></label>
    <div className="actions metadata-full"><button className="button primary">{initial ? "Localization 저장" : "Locale 추가"}</button>{onDelete && <button type="button" className="button danger" onClick={onDelete}>Localization 삭제</button>}</div>
  </fieldset></form>;
}
