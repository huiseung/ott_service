"use client";
import Link from "next/link";
import { use, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { userApi, type PlaybackStart } from "@/features/api";
import { useAuth } from "@/features/auth/AuthProvider";
import { ProgressReporter } from "@/features/player/ProgressReporter";
import { ApiError, errorMessage } from "@/shared/apiClient";

const time = (seconds: number) => Number.isFinite(seconds) ? `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, "0")}` : "0:00";

export default function WatchPage({ params }: { params: Promise<{ videoId: string }> }) {
  const { videoId } = use(params); const id = Number(videoId); const router = useRouter();
  const { user, loading: authLoading, epoch } = useAuth();
  const video = useRef<HTMLVideoElement>(null); const reporter = useRef<ProgressReporter | null>(null);
  const applied = useRef(false); const lastSaved = useRef(0); const playhead = useRef(0);
  const [session, setSession] = useState<PlaybackStart | null>(null);
  const [error, setError] = useState(""); const [loading, setLoading] = useState(true);
  const [buffering, setBuffering] = useState(false); const [playing, setPlaying] = useState(false);
  const [position, setPosition] = useState(0); const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(1); const [retryKey, setRetryKey] = useState(0);
  const start = useCallback(async () => {
    try { const value = await userApi.startPlayback(id); setSession(value); setError(""); setLoading(false); }
    catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) router.replace(`/login?next=${encodeURIComponent(`/watch/${id}`)}`);
      else { setError(errorMessage(reason)); setLoading(false); }
    }
  }, [id, router]);
  useEffect(() => {
    if (authLoading) return;
    if (!user) { router.replace(`/login?next=${encodeURIComponent(`/watch/${id}`)}`); return; }
    const timer = setTimeout(() => void start(), 0);
    return () => clearTimeout(timer);
  }, [authLoading, user, id, epoch, retryKey, router, start]);
  useEffect(() => {
    if (!session || !video.current) return;
    const element = video.current;
    let active = true;
    let hls: import("hls.js").default | null = null;
    applied.current = false; lastSaved.current = 0; playhead.current = 0;
    reporter.current?.cancel();
    const progress = new ProgressReporter(id, session.playbackSessionId, session.durationMs);
    reporter.current = progress;
    const seekResume = () => {
      if (applied.current || !Number.isFinite(element.duration) || element.duration <= 0) return;
      const target = Math.min(session.resumePositionMs / 1000, Math.max(0, element.duration - 1));
      if (target > 0) element.currentTime = target;
      applied.current = true; playhead.current = target; setPosition(target); setDuration(element.duration);
    };
    const update = () => { if (applied.current) { playhead.current = element.currentTime; setPosition(element.currentTime); } };
    const pause = () => { setPlaying(false); if (applied.current) progress.report(element.currentTime); };
    const seeked = () => { update(); if (applied.current) progress.report(element.currentTime); };
    const ended = () => { setPlaying(false); if (applied.current) progress.report(element.duration); };
    const pagehide = () => { if (applied.current) progress.leave(element.currentTime); };
    element.addEventListener("loadedmetadata", seekResume);
    element.addEventListener("durationchange", seekResume);
    element.addEventListener("timeupdate", update);
    element.addEventListener("pause", pause);
    element.addEventListener("seeked", seeked);
    element.addEventListener("ended", ended);
    element.addEventListener("waiting", () => setBuffering(true));
    element.addEventListener("playing", () => { setPlaying(true); setBuffering(false); });
    window.addEventListener("pagehide", pagehide);
    const heartbeat = setInterval(() => { if (applied.current && !element.paused && !element.seeking && !element.ended && element.readyState >= 3 && Math.abs(element.currentTime - lastSaved.current) >= 1) { lastSaved.current = element.currentTime; progress.report(element.currentTime); } }, 10000);
    if (element.canPlayType("application/vnd.apple.mpegurl")) element.src = session.manifestUrl;
    else void import("hls.js").then(({ default: Hls }) => {
      if (!active) return;
      if (!Hls.isSupported()) { setError("이 브라우저는 HLS 재생을 지원하지 않습니다."); return; }
      hls = new Hls(); hls.on(Hls.Events.ERROR, (_event, data) => { if (data.fatal && active) setError("재생 중 오류가 발생했습니다. 재시도하면 재생 권한을 갱신합니다."); });
      hls.loadSource(session.manifestUrl); hls.attachMedia(element);
    }).catch(() => { if (active) setError("플레이어를 불러오지 못했습니다."); });
    return () => {
      active = false; clearInterval(heartbeat); window.removeEventListener("pagehide", pagehide);
      element.removeEventListener("loadedmetadata", seekResume); element.removeEventListener("durationchange", seekResume);
      element.removeEventListener("timeupdate", update); element.removeEventListener("pause", pause);
      element.removeEventListener("seeked", seeked); element.removeEventListener("ended", ended);
      if (applied.current) progress.leave(element.currentTime); else progress.cancel();
      reporter.current = null; element.pause(); hls?.destroy(); element.removeAttribute("src"); element.load();
    };
  }, [session, id, epoch]);
  if (!Number.isSafeInteger(id) || id < 1) return <p className="error">유효하지 않은 영상 ID입니다.</p>;
  const retry = () => { setLoading(true); setError(""); setSession(null); setRetryKey(value => value + 1); };
  return <><div className="page-heading"><div><span className="eyebrow">NOW WATCHING</span><h1>{session?.title ?? "영상 시청"}</h1><p>원하는 시점으로 이동한 뒤 다시 이어볼 수 있습니다.</p></div><Link className="button" href="/">← 목록으로</Link></div>
    <section className="panel player-panel">{loading && <p className="muted">재생 정보를 불러오는 중입니다…</p>}{error && <div className="error" role="alert">{error} <button className="button small" onClick={retry}>재시도</button></div>}
      <video ref={video} className="video" playsInline preload="metadata" onError={() => setError("영상을 재생하지 못했습니다. 재시도해 주세요.")} />
      {session && <div className="player-controls"><div className="control-row"><button className="button" onClick={() => { const element = video.current; if (!element) return; if (element.paused) void element.play().catch(() => setError("재생을 시작할 수 없습니다. 다시 시도해 주세요.")); else element.pause(); }}>{playing ? "일시정지" : "재생"}</button><span>{time(position)} / {time(duration || session.durationMs / 1000)}</span>{buffering && <span className="muted" role="status">버퍼링 중…</span>}</div><label className="slider-label">재생 위치<input type="range" min="0" max={Math.max(1, duration || session.durationMs / 1000)} step="0.1" value={Math.min(position, Math.max(1, duration || session.durationMs / 1000))} onChange={event => { const next = Number(event.target.value); if (video.current) video.current.currentTime = next; setPosition(next); }} /></label><div className="control-row"><label className="volume">음량 <input type="range" min="0" max="1" step="0.05" value={volume} onChange={event => { const next = Number(event.target.value); setVolume(next); if (video.current) video.current.volume = next; }} /></label><button className="button small" onClick={() => void video.current?.requestFullscreen()}>전체 화면</button></div></div>}
    </section></>;
}
