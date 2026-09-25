"use client";
import { useState, type FormEvent } from "react";
import type { ContentRequest, ContentStatus, ContentType } from "../api/adminContentApi";

export function ContentOverviewForm({ initial, pending, onSave, creating = false }: {
  initial?: ContentRequest; pending: boolean; onSave: (request: ContentRequest) => void; creating?: boolean;
}) {
  const [type, setType] = useState<ContentType>(initial?.type ?? "MOVIE");
  const [status, setStatus] = useState<ContentStatus>(initial?.status ?? "DRAFT");
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onSave({
      type, status,
      originalCountry: String(data.get("originalCountry")).trim().toUpperCase(),
      originalLanguage: String(data.get("originalLanguage")).trim(),
      releaseDate: String(data.get("releaseDate")) || null,
    });
  }
  return <form onSubmit={submit}>
    <fieldset className="metadata-fields" disabled={pending}>
      <label>Type<select value={type} onChange={event => { if (event.target.value === "MOVIE" || event.target.value === "SERIES") setType(event.target.value); }}><option>MOVIE</option><option>SERIES</option></select></label>
      <label>Status<select value={status} onChange={event => { const value = event.target.value; if (value === "DRAFT" || value === "PUBLISHED" || value === "ARCHIVED") setStatus(value); }}><option>DRAFT</option><option>PUBLISHED</option><option>ARCHIVED</option></select></label>
      <label>Original Country<input name="originalCountry" required pattern="[A-Za-z]{2}" maxLength={2} title="두 자리 국가 코드" placeholder="KR" defaultValue={initial?.originalCountry ?? ""} /></label>
      <label>Original Language<input name="originalLanguage" required pattern=".*\S.*" maxLength={20} placeholder="ko" defaultValue={initial?.originalLanguage ?? ""} /></label>
      <label>Release Date (선택)<input type="date" name="releaseDate" defaultValue={initial?.releaseDate ?? ""} /></label>
      <div className="metadata-full"><p className="muted">Original Country는 작품의 원산지 metadata입니다. 서비스 대상 국가는 Availability에서 별도로 관리합니다.</p>
        <button className="button primary" disabled={pending}>{pending ? "저장 중…" : creating ? "Content 생성" : "Overview 저장"}</button>
      </div>
    </fieldset>
  </form>;
}
