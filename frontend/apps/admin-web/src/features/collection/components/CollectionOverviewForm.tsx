"use client";
import { useState, type FormEvent } from "react";
import type { ContentStatus } from "@/features/content/api/adminContentApi";
import type { CollectionCreateRequest, CollectionOverviewRequest } from "../api/adminCollectionApi";

export function CollectionOverviewForm({ initial, disabled, creating = false, onSave }: {
  initial?: CollectionOverviewRequest; disabled: boolean; creating?: boolean; onSave: (request: CollectionCreateRequest) => void;
}) {
  const [status, setStatus] = useState<ContentStatus>(initial?.status ?? "DRAFT");
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (status === "ARCHIVED" && initial?.status !== "ARCHIVED" && !window.confirm("ARCHIVED로 저장하면 현재 API에서 이후 수정하거나 복원할 수 없습니다. 계속하시겠습니까?")) return;
    const data = new FormData(event.currentTarget);
    const request: CollectionCreateRequest = { status, minVisibleItems: Number(data.get("minVisibleItems")) };
    if (creating) request.localizations = [{ locale: String(data.get("locale")).trim(), title: String(data.get("title")).trim(), description: String(data.get("description")).trim() || null }];
    onSave(request);
  }
  return <form onSubmit={submit}><fieldset className="metadata-fields" disabled={disabled}>
    <label>Status<select value={status} onChange={event => { const value = event.target.value; if (value === "DRAFT" || value === "PUBLISHED" || value === "ARCHIVED") setStatus(value); }}><option>DRAFT</option><option>PUBLISHED</option><option>ARCHIVED</option></select></label>
    <label>minVisibleItems<input name="minVisibleItems" type="number" required min={0} max={2147483647} step={1} defaultValue={initial?.minVisibleItems ?? 0} /></label>
    {creating && <>
      <label>첫 Metadata Locale<input name="locale" required pattern=".*\S.*" maxLength={20} placeholder="ko-KR" /></label>
      <label>Title<input name="title" required pattern=".*\S.*" maxLength={300} /></label>
      <label className="metadata-full">Description (선택, 최대 4000자)<textarea name="description" rows={3} maxLength={4000} /></label>
    </>}
    <div className="metadata-full"><p className="muted">minVisibleItems는 최소 노출 항목 수 설정입니다. 실제 노출 여부는 Backend에서 판단합니다.</p><button className="button primary">{creating ? "Collection 생성" : "Overview 저장"}</button></div>
  </fieldset></form>;
}
