"use client";
import { use, useCallback, useEffect, useState } from "react";
import { adminContentApi, type AdminImage } from "@/features/content/api/adminContentApi";
import { ArtworkManager } from "@/features/content/components/ArtworkManager";
import { userError } from "@/shared/lib/apiClient";

export default function EpisodeImagesPage({ params }: { params: Promise<{ episodeId: string }> }) {
  const { episodeId } = use(params); const id = Number(episodeId);
  const [images, setImages] = useState<AdminImage[]>([]); const [error, setError] = useState("");
  const refresh = useCallback(async () => { try { const result = await adminContentApi.episodeImages(id); setImages(result.images); setError(""); } catch (reason) { setError(userError(reason)); } }, [id]);
  useEffect(() => { const timer = setTimeout(() => void refresh(), 0); return () => clearTimeout(timer); }, [refresh]);
  return <><div className="page-heading"><div><span className="eyebrow">EPISODE / #{id}</span><h1>Episode Thumbnail</h1><p>16:9, 최소 1280x720</p></div></div>{error && <div className="panel error">{error}</div>}<section className="panel"><ArtworkManager slots={[{ type: "THUMBNAIL", label: "THUMBNAIL", ratio: "16:9", minimum: "1280x720" }]} images={images} onUpload={async (_type, file) => { await adminContentApi.uploadEpisodeImage(id, "THUMBNAIL", file); await refresh(); }} onDelete={async () => { await adminContentApi.deleteEpisodeImage(id, "THUMBNAIL"); await refresh(); }} /></section></>;
}
