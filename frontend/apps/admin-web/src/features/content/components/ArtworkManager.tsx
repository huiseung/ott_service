"use client";
import { useRef, useState } from "react";
import type { AdminImage } from "@/features/content/api/adminContentApi";

interface Slot {
  type: "POSTER" | "LANDSCAPE" | "HERO" | "THUMBNAIL";
  label: string;
  ratio: string;
  minimum: string;
}

export function ArtworkManager({ slots, images, onUpload, onDelete }: { slots: Slot[]; images: AdminImage[]; onUpload: (type: Slot["type"], file: File) => Promise<void>; onDelete: (type: Slot["type"]) => Promise<void> }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [previews, setPreviews] = useState<Record<string, string>>({});
  const inputs = useRef<Record<string, HTMLInputElement | null>>({});
  const byType = new Map(images.map(image => [image.type, image]));
  async function upload(type: Slot["type"], file: File) {
    const url = URL.createObjectURL(file); setPreviews(value => ({ ...value, [type]: url })); setBusy(type);
    try { await onUpload(type, file); } finally { setBusy(null); }
  }
  return <div className="artwork-grid">{slots.map(slot => {
    const image = byType.get(slot.type); const preview = previews[slot.type] ?? image?.url;
    return <section className="artwork-card" key={slot.type}>
      <div className="section-head"><div><h3>{slot.label}</h3><small>{slot.ratio} · 최소 {slot.minimum}</small></div>{busy === slot.type && <span className="muted">Uploading...</span>}</div>
      <div className={`artwork-preview ${slot.type === "POSTER" ? "poster" : ""}`}>{preview ? <img src={preview} alt={`${slot.label} preview`} /> : <span>NO IMAGE</span>}</div>
      {image && <p className="muted">{image.width}x{image.height} · {image.mimeType} · {Math.round(image.fileSize / 1024)}KB</p>}
      <input ref={node => { inputs.current[slot.type] = node; }} className="sr-only" type="file" accept="image/jpeg,image/png,image/webp" onChange={event => { const file = event.target.files?.[0]; if (file) void upload(slot.type, file); event.currentTarget.value = ""; }} />
      <div className="actions"><button className="button small" disabled={busy === slot.type} onClick={() => inputs.current[slot.type]?.click()}>{image ? "Replace" : "Upload"}</button><button className="button small danger" disabled={!image || busy === slot.type} onClick={() => void onDelete(slot.type)}>Delete</button></div>
    </section>;
  })}</div>;
}
