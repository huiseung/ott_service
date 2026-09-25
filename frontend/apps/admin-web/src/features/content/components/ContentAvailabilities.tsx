"use client";
import { useState, type FormEvent } from "react";
import { adminContentApi, type AvailabilityRequest, type AvailabilityStatus, type ContentAvailability, type ContentDetail } from "../api/adminContentApi";
import { MutationFeedback, type ContentMutation } from "./useContentMutation";

function utcInput(value?: string | null) {
  return value ? new Date(value).toISOString().slice(0, -1) : "";
}

// Keep the backend's original precision if the operator did not edit the timestamp.
function instant(value: string, original?: string | null) {
  return original && value === utcInput(original) ? original : new Date(`${value}Z`).toISOString();
}

export function ContentAvailabilities({ content, onChange, mutation }: {
  content: ContentDetail; onChange: (content: ContentDetail) => void; mutation: ContentMutation;
}) {
  const [newRevision, setNewRevision] = useState(0);
  async function save(request: AvailabilityRequest, existing?: ContentAvailability) {
    if (existing && request.status === "DISABLED" && existing.status !== "DISABLED" && !window.confirm(`${existing.countryCode}의 Availability를 DISABLED로 변경하시겠습니까?`)) return;
    const ok = await mutation.run("Availability 저장", "두 자리 국가 코드, 상태, 시작 시각과 종료 시각(시작 이후 또는 동일)을 확인하세요.", async () => {
      if (!existing && content.availabilities.some(item => item.countryCode === request.countryCode)) throw new Error("이미 등록된 국가입니다. 해당 국가의 편집 양식을 사용하세요.");
      onChange(await adminContentApi.setAvailability(content.id, request));
    });
    if (ok && !existing) setNewRevision(value => value + 1);
  }
  return <section className="panel" id="availability">
    <h2>Availability</h2><p className="muted">서비스 대상 국가 설정입니다. Original Country와 별개입니다. 모든 시각은 UTC이며 종료 시각을 비우면 종료 제한이 없습니다.</p>
    <p className="muted">등록된 상태와 기간을 표시합니다. 실제 서비스 가능 여부는 Backend에서 판단합니다.</p>
    {content.availabilities.length === 0 && <p className="muted">등록된 Availability가 없습니다.</p>}
    {content.availabilities.map(item => <div className="metadata-entry" key={item.id}><h3>{item.countryCode}</h3><AvailabilityForm key={JSON.stringify(item)} initial={item} pending={mutation.pending} onSave={request => void save(request, item)} /></div>)}
    <div className="metadata-entry"><h3>국가 추가</h3><AvailabilityForm key={newRevision} pending={mutation.pending} onSave={request => void save(request)} /></div>
    {mutation.scope.startsWith("Availability") && <MutationFeedback mutation={mutation} />}
  </section>;
}

function AvailabilityForm({ initial, pending, onSave }: {
  initial?: ContentAvailability; pending: boolean; onSave: (request: AvailabilityRequest) => void;
}) {
  const [status, setStatus] = useState<AvailabilityStatus>(initial?.status ?? "DISABLED");
  const [from, setFrom] = useState(utcInput(initial?.availableFrom));
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const until = String(data.get("availableUntil"));
    onSave({
      countryCode: initial?.countryCode ?? String(data.get("countryCode")).trim().toUpperCase(),
      status,
      availableFrom: instant(from, initial?.availableFrom),
      availableUntil: until ? instant(until, initial?.availableUntil) : null,
    });
  }
  return <form onSubmit={submit}><fieldset className="metadata-fields" disabled={pending}>
    <label>Available Country<input name="countryCode" required readOnly={!!initial} pattern="[A-Za-z]{2}" maxLength={2} placeholder="KR" defaultValue={initial?.countryCode ?? ""} /></label>
    <label>Status<select value={status} onChange={event => { if (event.target.value === "AVAILABLE" || event.target.value === "DISABLED") setStatus(event.target.value); }}><option>DISABLED</option><option>AVAILABLE</option></select></label>
    <label>Available From (UTC)<input type="datetime-local" required step="0.001" value={from} onChange={event => setFrom(event.target.value)} /></label>
    <label>Available Until (UTC, 선택)<input name="availableUntil" type="datetime-local" step="0.001" min={from || undefined} defaultValue={utcInput(initial?.availableUntil)} /></label>
    <div className="actions metadata-full"><button className="button primary">{initial ? "Availability 저장" : "국가 추가"}</button></div>
  </fieldset></form>;
}
