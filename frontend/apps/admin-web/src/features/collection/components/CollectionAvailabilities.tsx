"use client";
import { useState, type FormEvent } from "react";
import type { AvailabilityStatus } from "@/features/content/api/adminContentApi";
import { adminCollectionApi, type CollectionAvailability, type CollectionAvailabilityRequest, type CollectionDetail } from "../api/adminCollectionApi";
import type { CollectionMutation } from "./useCollectionMutation";

export function CollectionAvailabilities({ collection, disabled, mutation }: {
  collection: CollectionDetail; disabled: boolean; mutation: CollectionMutation;
}) {
  const [revision, setRevision] = useState(0);
  const hint = "두 자리 국가 코드와 상태, UTC 시작 시각을 확인하세요. 종료 시각은 시작 시각 이후 또는 같아야 합니다.";
  async function save(request: CollectionAvailabilityRequest, existing?: CollectionAvailability) {
    if (existing?.status === "AVAILABLE" && request.status === "DISABLED" && !window.confirm(`${existing.countryCode} 설정을 DISABLED로 변경하시겠습니까?`)) return;
    const ok = await mutation.run("Availability 저장", hint, async () => {
      if (!existing && collection.availabilities.some(item => item.countryCode === request.countryCode)) throw new Error("이미 등록된 국가입니다. 해당 국가의 양식에서 수정하세요.");
      return adminCollectionApi.setAvailability(collection.id, request);
    });
    if (ok && !existing) setRevision(value => value + 1);
  }
  return <section className="panel" id="collection-availability"><h2>Availability</h2>
    <p className="muted">국가별 설정이며 모든 시각은 UTC입니다. 종료 시각을 비우면 종료 제한이 없습니다. Collection 설정이 Content Availability를 우회하지 않습니다.</p>
    {collection.availabilities.length === 0 && <p className="muted">등록된 Availability가 없습니다.</p>}
    {collection.availabilities.map(item => <div className="metadata-entry" key={item.id}><h3>{item.countryCode}</h3><AvailabilityForm key={JSON.stringify(item)} initial={item} disabled={disabled} onSave={value => void save(value, item)} onDelete={() => {
      if (!window.confirm(`${item.countryCode}의 Availability 설정을 삭제하시겠습니까?`)) return;
      void mutation.run("Availability 삭제", hint, async () => { await adminCollectionApi.deleteAvailability(collection.id, item.countryCode); return adminCollectionApi.get(collection.id); });
    }} /></div>)}
    <div className="metadata-entry"><h3>국가 추가</h3><AvailabilityForm key={revision} disabled={disabled} onSave={value => void save(value)} /></div>
  </section>;
}
const utcInput = (value?: string | null) => value ? new Date(value).toISOString().slice(0, -1) : "";
function instant(value: string, original?: string | null) {
  const date = new Date(value + "Z");
  return original && date.getTime() === new Date(original).getTime() ? original : date.toISOString();
}

function AvailabilityForm({ initial, disabled, onSave, onDelete }: {
  initial?: CollectionAvailability; disabled: boolean; onSave: (request: CollectionAvailabilityRequest) => void; onDelete?: () => void;
}) {
  const [status, setStatus] = useState<AvailabilityStatus>(initial?.status ?? "DISABLED");
  const [from, setFrom] = useState(utcInput(initial?.availableFrom));
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const until = String(data.get("availableUntil"));
    onSave({ countryCode: initial?.countryCode ?? String(data.get("countryCode")).trim().toUpperCase(), status, availableFrom: instant(from, initial?.availableFrom), availableUntil: until ? instant(until, initial?.availableUntil) : null });
  }
  return <form onSubmit={submit}><fieldset className="metadata-fields" disabled={disabled}>
    <label>Country<input name="countryCode" required readOnly={!!initial} pattern="[A-Za-z]{2}" maxLength={2} placeholder="KR" defaultValue={initial?.countryCode ?? ""} /></label>
    <label>Status<select value={status} onChange={event => { const value = event.target.value; if (value === "AVAILABLE" || value === "DISABLED") setStatus(value); }}><option>DISABLED</option><option>AVAILABLE</option></select></label>
    <label>Available From (UTC)<input type="datetime-local" required step="0.001" value={from} onChange={event => setFrom(event.target.value)} /></label>
    <label>Available Until (UTC, 선택)<input name="availableUntil" type="datetime-local" step="0.001" min={from || undefined} defaultValue={utcInput(initial?.availableUntil)} /></label>
    <div className="actions metadata-full"><button className="button primary">{initial ? "Availability 저장" : "국가 추가"}</button>{onDelete && <button type="button" className="button danger" onClick={onDelete}>Availability 삭제</button>}</div>
  </fieldset></form>;
}
