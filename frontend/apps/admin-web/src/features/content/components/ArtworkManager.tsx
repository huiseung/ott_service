"use client";
import { useRef } from "react";
import type { AdminImage } from "../api/adminContentApi";
import { MutationFeedback, useContentMutation } from "./useContentMutation";
import { ArtworkPreview } from "./ArtworkPreview";

export interface ArtworkSlot<T extends AdminImage["type"]> { type: T; label: string; ratio: string; minimum: string }

export function ArtworkManager<T extends AdminImage["type"]>({ slots, images, onUpload, onDelete, onRefresh }: {
  slots: ArtworkSlot<T>[]; images: AdminImage[];
  onUpload: (type: T, file: File) => Promise<void>; onDelete: (type: T) => Promise<void>; onRefresh: () => void;
}) {
  const mutation = useContentMutation();
  const inputs = useRef<Partial<Record<T, HTMLInputElement | null>>>({});
  const byType = new Map(images.map(image => [image.type, image]));
  function upload(slot: ArtworkSlot<T>, file: File) {
    if (byType.has(slot.type) && !window.confirm(`${slot.label} 이미지를 선택한 파일로 교체하시겠습니까?`)) return;
    void mutation.run(`${slot.label} 업로드`, `JPEG·PNG·WebP 파일, 최소 ${slot.minimum}, 비율 ${slot.ratio}와 파일 크기 제한을 확인하세요.`, async () => {
      if (!file.size) throw new Error("비어 있는 파일은 업로드할 수 없습니다.");
      if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) throw new Error("JPEG, PNG, WebP 이미지를 선택하세요.");
      await onUpload(slot.type, file);
    });
  }
  function remove(slot: ArtworkSlot<T>) {
    if (!window.confirm(`${slot.label} 이미지를 삭제하시겠습니까?`)) return;
    void mutation.run(`${slot.label} 삭제`, "이미지 정보를 새로고침하고 다시 시도하세요.", () => onDelete(slot.type));
  }
  return <>
    <p className="muted">JPEG · PNG · WebP / 파일 크기 제한 기본 10MB. 실제 허용 여부는 서버에서 검사합니다.</p>
    <button className="button small" disabled={mutation.pending} onClick={onRefresh}>이미지 새로고침</button>
    <div className="artwork-grid">{slots.map(slot => {
      const image = byType.get(slot.type);
      return <section className="artwork-card" key={slot.type}>
        <h3>{slot.label}</h3><small>권장 비율 {slot.ratio} · 최소 {slot.minimum}</small>
        <ArtworkPreview key={image?.url} url={image?.url} label={slot.label} poster={slot.type === "POSTER"} />
        {image && <div className="image-info"><p>{image.width} × {image.height}px · {image.mimeType}<br />{image.fileSize.toLocaleString("ko-KR")} bytes</p><p>등록: <time dateTime={image.createdAt}>{new Date(image.createdAt).toLocaleString("ko-KR")}</time><br />수정: <time dateTime={image.updatedAt}>{new Date(image.updatedAt).toLocaleString("ko-KR")}</time></p></div>}
        <input aria-label={`${slot.label} 이미지 파일`} ref={node => { inputs.current[slot.type] = node; }} className="sr-only" tabIndex={-1} type="file" accept="image/jpeg,image/png,image/webp" disabled={mutation.pending} onChange={event => { const file = event.currentTarget.files?.[0]; event.currentTarget.value = ""; if (file) upload(slot, file); }} />
        <div className="actions"><button className="button small" disabled={mutation.pending} onClick={() => inputs.current[slot.type]?.click()}>{image ? "Replace" : "Upload"}</button><button className="button small danger" disabled={!image || mutation.pending} onClick={() => remove(slot)}>Delete</button></div>
      </section>;
    })}</div>
    <MutationFeedback mutation={mutation} />
  </>;
}
