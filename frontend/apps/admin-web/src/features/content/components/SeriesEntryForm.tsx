"use client";
import { useState, type FormEvent } from "react";
import type { ContentStatus } from "../api/adminContentApi";

export function SeriesEntryForm({ kind, initial, pending, onSave }: {
  kind: "Season" | "Episode"; initial?: { number: number; status: ContentStatus; releaseAt?: string | null };
  pending: boolean; onSave: (value: { number: number; status: ContentStatus; releaseAt: string | null }) => void;
}) {
  const [status, setStatus] = useState<ContentStatus>(initial?.status ?? "DRAFT");
  const releaseInput = initial?.releaseAt ? new Date(initial.releaseAt).toISOString().slice(0, -1) : "";
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const release = String(data.get("releaseAt") ?? "");
    // Preserve the server's sub-millisecond precision when only number/status changed.
    const releaseAt = !release ? null : initial?.releaseAt && new Date(release + "Z").getTime() === new Date(initial.releaseAt).getTime() ? initial.releaseAt : new Date(release + "Z").toISOString();
    onSave({ number: Number(data.get("number")), status, releaseAt });
  }
  return <form onSubmit={submit}><fieldset className="metadata-fields" disabled={pending}>
    <label>{kind} Number<input name="number" type="number" min={1} max={2147483647} step={1} required defaultValue={initial?.number ?? ""} /></label>
    <label>Status<select value={status} onChange={event => { const value = event.target.value; if (value === "DRAFT" || value === "PUBLISHED" || value === "ARCHIVED") setStatus(value); }}><option>DRAFT</option><option>PUBLISHED</option><option>ARCHIVED</option></select></label>
    {kind === "Episode" && <label>Release At (UTC, 선택)<input type="datetime-local" step="0.001" name="releaseAt" defaultValue={releaseInput} /></label>}
    <div className="actions metadata-full"><button className="button primary">{initial ? `${kind} 저장` : `${kind} 생성`}</button></div>
  </fieldset></form>;
}
