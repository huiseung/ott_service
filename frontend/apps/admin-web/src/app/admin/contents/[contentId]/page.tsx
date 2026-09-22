"use client";
import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import { adminContentApi, type AdminImage, type ContentDetail, type Season } from "@/features/content/api/adminContentApi";
import { ArtworkManager } from "@/features/content/components/ArtworkManager";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { userError } from "@/shared/lib/apiClient";

export default function ContentDetailPage({ params }: { params: Promise<{ contentId: string }> }) {
  const { contentId } = use(params); const id = Number(contentId);
  const [content, setContent] = useState<ContentDetail | null>(null); const [images, setImages] = useState<AdminImage[]>([]); const [seasons, setSeasons] = useState<Season[]>([]); const [error, setError] = useState("");
  const refresh = useCallback(async () => { try { const [detail, imageResult] = await Promise.all([adminContentApi.get(id), adminContentApi.images(id)]); setContent(detail); setImages(imageResult.images); setError(""); if (detail.type === "SERIES") setSeasons(await adminContentApi.seasons(id)); } catch (reason) { setError(userError(reason)); } }, [id]);
  useEffect(() => { const timer = setTimeout(() => void refresh(), 0); return () => clearTimeout(timer); }, [refresh]);
  return <><div className="page-heading"><div><span className="eyebrow">CONTENT / #{id}</span><h1>{content?.localizations[0]?.title ?? "Content"}</h1><p>Artwork는 Video와 별도 CMS Asset입니다.</p></div><button className="button" onClick={() => void refresh()}>새로고침</button></div>{error && <div className="panel error">{error}</div>}{content && <><section className="panel"><div className="kv"><span>Type</span><strong>{content.type}</strong><span>Status</span><StatusBadge value={content.status} /><span>Locale</span><strong>{content.localizations.map(item => item.locale).join(", ") || "—"}</strong></div></section><section className="panel"><span className="eyebrow">ARTWORK</span><h2>Content Artwork</h2><ArtworkManager slots={[{ type: "POSTER", label: "POSTER", ratio: "2:3", minimum: "600x900" }, { type: "LANDSCAPE", label: "LANDSCAPE", ratio: "16:9", minimum: "1280x720" }, { type: "HERO", label: "HERO", ratio: "16:9", minimum: "1920x1080" }]} images={images} onUpload={async (type, file) => { await adminContentApi.uploadImage(id, type as "POSTER" | "LANDSCAPE" | "HERO", file); await refresh(); }} onDelete={async type => { await adminContentApi.deleteImage(id, type as "POSTER" | "LANDSCAPE" | "HERO"); await refresh(); }} /></section>{content.type === "SERIES" && <section className="panel"><div className="section-head"><h2>Episodes</h2></div>{seasons.length ? seasons.map(season => <SeasonBlock seasonId={season.id} key={season.id} />) : <p className="muted">등록된 시즌이 없습니다.</p>}</section>}</>}</>;
}

function SeasonBlock({ seasonId }: { seasonId: number }) {
  const [episodes, setEpisodes] = useState<{ id: number; episodeNumber: number; localizations: { title: string }[] }[]>([]);
  useEffect(() => { adminContentApi.episodes(seasonId).then(setEpisodes).catch(() => setEpisodes([])); }, [seasonId]);
  return <div className="episode-list"><strong>Season #{seasonId}</strong>{episodes.map(episode => <Link className="dashboard-row" href={`/admin/episodes/${episode.id}`} key={episode.id}><span>Episode {episode.episodeNumber} · {episode.localizations[0]?.title ?? "Untitled"}</span><span className="button small">Thumbnail</span></Link>)}</div>;
}
