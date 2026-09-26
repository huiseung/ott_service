"use client";
import { useEffect, useId, useRef, useState } from "react";
import type { AdminImage } from "../api/adminContentApi";
import { MutationFeedback, useContentMutation } from "./useContentMutation";
import { ArtworkPreview } from "./ArtworkPreview";

export interface ArtworkSlot<T extends AdminImage["type"]> { type: T; label: string; ratio: string; minimum: string }

function ArtworkCard<T extends AdminImage["type"]>({ slot, image, onUpload, onDelete, onBusyChange }: {
  slot: ArtworkSlot<T>; image?: AdminImage;
  onUpload: (type: T, file: File) => Promise<void>; onDelete: (type: T) => Promise<void>;
  onBusyChange: (busy: boolean) => void;
}) {
  const mutation = useContentMutation();
  const input = useRef<HTMLInputElement>(null);
  const lock = useRef(false);
  const objectUrl = useRef<string | null>(null);
  const dragDepth = useRef(0);
  const [dragging, setDragging] = useState(false);
  const [draft, setDraft] = useState<{ file: File; url: string } | null>(null);
  const [selectionError, setSelectionError] = useState("");
  const hintId = useId();

  useEffect(() => () => {
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
  }, []);

  function clearDraft() {
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
    objectUrl.current = null;
    setDraft(null);
  }

  async function upload(file: File) {
    if (lock.current) return;
    lock.current = true;
    onBusyChange(true);
    setSelectionError("");
    try {
      const success = await mutation.run(`${slot.label} 업로드`, `JPEG·PNG·WebP 파일, 최소 ${slot.minimum}, 비율 ${slot.ratio}와 파일 크기 제한을 확인하세요.`, async () => {
        if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
        objectUrl.current = URL.createObjectURL(file);
        setDraft({ file, url: objectUrl.current });
        await onUpload(slot.type, file);
      });
      if (success) clearDraft();
    } finally { lock.current = false; onBusyChange(false); }
  }

  function selectFiles(files: FileList | null) {
    if (lock.current || !files?.length) return;
    if (files.length !== 1) { setSelectionError("이미지는 영역마다 한 장씩 놓아주세요."); return; }
    const file = files[0];
    if (!file.size) { setSelectionError("비어 있는 파일은 업로드할 수 없습니다."); return; }
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      setSelectionError("JPEG, PNG, WebP 이미지를 선택하세요."); return;
    }
    void upload(file);
  }

  async function remove() {
    if (lock.current || !window.confirm(`${slot.label} 이미지를 삭제하시겠습니까?`)) return;
    lock.current = true;
    onBusyChange(true);
    try {
      const success = await mutation.run(`${slot.label} 삭제`, "이미지 정보를 새로고침하고 다시 시도하세요.", () => onDelete(slot.type));
      if (success) { clearDraft(); setSelectionError(""); }
    } finally { lock.current = false; onBusyChange(false); }
  }

  const previewUrl = draft?.url ?? image?.url;
  return <section className="artwork-card" aria-label={`${slot.label} 이미지 관리`}>
    <h3>{slot.label}</h3><small>권장 비율 {slot.ratio} · 최소 {slot.minimum}</small>
    <button type="button" className={`artwork-dropzone${dragging ? " is-dragging" : ""}${mutation.pending ? " is-busy" : ""}`}
      aria-label={`${slot.label} 이미지 ${image ? "교체" : "업로드"}`} aria-describedby={hintId}
      aria-disabled={mutation.pending} aria-busy={mutation.pending}
      onClick={() => { if (!lock.current) input.current?.click(); }}
      onDragEnter={event => {
        event.preventDefault(); event.stopPropagation();
        if (!lock.current && event.dataTransfer.types.includes("Files")) { dragDepth.current++; setDragging(true); }
      }}
      onDragOver={event => {
        event.preventDefault(); event.stopPropagation();
        event.dataTransfer.dropEffect = lock.current ? "none" : "copy";
      }}
      onDragLeave={event => {
        event.preventDefault(); event.stopPropagation();
        dragDepth.current = Math.max(0, dragDepth.current - 1);
        if (!dragDepth.current) setDragging(false);
      }}
      onDrop={event => {
        event.preventDefault(); event.stopPropagation();
        dragDepth.current = 0; setDragging(false);
        selectFiles(event.dataTransfer.files);
      }}>
      <ArtworkPreview key={previewUrl} url={previewUrl} label={slot.label} poster={slot.type === "POSTER"} />
      <span className="artwork-drop-hint" id={hintId}>
        <strong>{mutation.pending ? "저장 중…" : dragging ? "여기에 놓으세요" : image ? "새 이미지를 놓아 교체" : "이미지를 여기에 놓으세요"}</strong>
        <span>{mutation.pending ? "미리보기 · 완료될 때까지 기다려 주세요" : "드래그하거나 클릭해서 선택 · 선택 즉시 업로드"}</span>
      </span>
    </button>
    <input aria-label={`${slot.label} 이미지 파일`} ref={input} className="sr-only" tabIndex={-1} type="file"
      accept="image/jpeg,image/png,image/webp" disabled={mutation.pending}
      onChange={event => { selectFiles(event.currentTarget.files); event.currentTarget.value = ""; }} />
    {draft && <p className="image-info artwork-file-name">{draft.file.name} · {(draft.file.size / 1024).toLocaleString("ko-KR", { maximumFractionDigits: 0 })} KB<br />{mutation.pending ? "선택한 이미지 미리보기" : "아직 저장되지 않은 미리보기"}</p>}
    {!draft && image && <div className="image-info"><p>{image.width} × {image.height}px · {image.mimeType}<br />{image.fileSize.toLocaleString("ko-KR")} bytes</p><p>등록: <time dateTime={image.createdAt}>{new Date(image.createdAt).toLocaleString("ko-KR")}</time><br />수정: <time dateTime={image.updatedAt}>{new Date(image.updatedAt).toLocaleString("ko-KR")}</time></p></div>}
    {selectionError && <p className="error" role="alert">{selectionError}</p>}
    <MutationFeedback mutation={mutation} />
    <div className="actions">
      {draft && !mutation.pending && <>
        <button type="button" className="button small" onClick={() => void upload(draft.file)}>다시 업로드</button>
        <button type="button" className="button small" onClick={clearDraft}>{image ? "저장된 이미지 보기" : "미리보기 닫기"}</button>
      </>}
      {image && <button type="button" className="button small danger" disabled={mutation.pending} onClick={() => void remove()}>이미지 삭제</button>}
    </div>
  </section>;
}

export function ArtworkManager<T extends AdminImage["type"]>({ slots, images, onUpload, onDelete, onRefresh }: {
  slots: ArtworkSlot<T>[]; images: AdminImage[];
  onUpload: (type: T, file: File) => Promise<void>; onDelete: (type: T) => Promise<void>; onRefresh: () => void;
}) {
  const [busySlots, setBusySlots] = useState<Set<T>>(() => new Set());
  const byType = new Map(images.map(image => [image.type, image]));
  return <>
    <p className="muted">각 영역에 이미지를 놓으면 바로 미리보고 업로드합니다. 기존 이미지도 새 파일을 놓아 교체할 수 있습니다.</p>
    <p className="muted">JPEG · PNG · WebP / 파일 크기 제한 기본 10MB. 실제 허용 여부는 서버에서 검사합니다.</p>
    <button type="button" className="button small" disabled={busySlots.size > 0} onClick={onRefresh}>이미지 새로고침</button>
    <div className="artwork-grid">{slots.map(slot => <ArtworkCard key={slot.type} slot={slot} image={byType.get(slot.type)}
      onUpload={onUpload} onDelete={onDelete} onBusyChange={busy => setBusySlots(current => {
        const next = new Set(current);
        if (busy) next.add(slot.type); else next.delete(slot.type);
        return next;
      })} />)}</div>
  </>;
}
