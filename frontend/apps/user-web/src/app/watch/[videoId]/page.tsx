"use client";
import Link from "next/link";
import { use, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { userApi, type PlaybackStart } from "@/features/api";
import { useAuth } from "@/features/auth/AuthProvider";
import { PlaybackAnalytics } from "@/features/analytics/PlaybackAnalytics";
import { trackEvent } from "@/features/analytics/analytics";
import { ApiError, errorMessage, getAccessToken } from "@/shared/apiClient";
import { config } from "@/shared/config";

const time = (seconds: number) => Number.isFinite(seconds) ? `${Math.floor(seconds / 60)}:${String(Math.floor(seconds % 60)).padStart(2, "0")}` : "0:00";

export default function WatchPage({ params }: { params: Promise<{ videoId: string }> }) {
  const { videoId } = use(params); const id = Number(videoId); const router = useRouter();
  const { user, loading: authLoading, epoch } = useAuth();
  const video = useRef<HTMLVideoElement>(null);
  const applied = useRef(false);
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
    applied.current = false;
    const playbackToken = getAccessToken();
    const analytics = new PlaybackAnalytics(session.playbackSessionId, id, event => trackEvent(event, playbackToken));
    const sample = () => ({ positionMs: Math.max(0, Number.isFinite(element.currentTime) ? element.currentTime * 1000 : 0),
      durationMs: (Number.isFinite(element.duration) ? element.duration : session.durationSeconds) * 1000,
      playbackRate: element.playbackRate, ended: element.ended });
    analytics.record("PLAYBACK_SESSION_STARTED", sample());
    const seekResume = () => {
      if (applied.current || !Number.isFinite(element.duration) || element.duration <= 0) return;
      const target = Math.min(session.resumePositionSeconds, Math.max(0, element.duration - 1));
      if (target > 0) element.currentTime = target;
      applied.current = true; setPosition(target); setDuration(element.duration);
    };
    const update = () => { if (applied.current) { analytics.sample(sample()); setPosition(element.currentTime); } };
    const pause = () => { setPlaying(false); if (applied.current) analytics.pause(sample()); };
    const seeking = () => analytics.seeking(sample());
    const seeked = () => { analytics.seeked(sample()); update(); };
    const ended = () => { setPlaying(false); analytics.ended(sample()); };
    const pagehide = () => analytics.stop(sample(), "pagehide");
    const waiting = () => { setBuffering(true); analytics.waiting(sample()); };
    const playing = () => { setPlaying(true); setBuffering(false); analytics.playing(sample()); };
    const play = () => analytics.playRequested();
    const ratechange = () => analytics.record("PLAYBACK_RATE_CHANGED", sample());
    const mediaError = () => analytics.error(sample(), "media", String(element.error?.code ?? "unknown"), true);
    element.addEventListener("loadedmetadata", seekResume);
    element.addEventListener("durationchange", seekResume);
    element.addEventListener("timeupdate", update);
    element.addEventListener("pause", pause);
    element.addEventListener("seeking", seeking);
    element.addEventListener("seeked", seeked);
    element.addEventListener("ended", ended);
    element.addEventListener("waiting", waiting);
    element.addEventListener("playing", playing);
    element.addEventListener("play", play);
    element.addEventListener("ratechange", ratechange);
    element.addEventListener("error", mediaError);
    window.addEventListener("ott:analytics-pagehide", pagehide);
    const heartbeat = setInterval(() => { if (applied.current && !element.paused && !element.seeking && !element.ended && element.readyState >= 3) analytics.record("HEARTBEAT", sample()); }, 10000);
    if (element.canPlayType("application/vnd.apple.mpegurl")) element.src = new URL(session.hlsUrl, config.apiBaseUrl).toString();
    else void import("hls.js").then(({ default: Hls }) => {
      if (!active) return;
      if (!Hls.isSupported()) { analytics.error(sample(), "hls", "unsupported", true); setError("이 브라우저는 HLS 재생을 지원하지 않습니다."); return; }
      hls = new Hls(); hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!active) return;
        analytics.error(sample(), "hls", data.details, data.fatal);
        if (data.fatal) setError("재생 중 오류가 발생했습니다. 재시도하면 재생 권한을 갱신합니다.");
      });
      hls.on(Hls.Events.LEVEL_SWITCHED, (_event, data) => {
        const level = hls?.levels[data.level];
        if (active && level) analytics.record("QUALITY_CHANGED", sample(), {
          bitrate: level.bitrate, resolution: `${level.width}x${level.height}` });
      });
      hls.loadSource(new URL(session.hlsUrl, config.apiBaseUrl).toString()); hls.attachMedia(element);
    }).catch(() => { if (active) { analytics.error(sample(), "hls", "load_failed", true); setError("플레이어를 불러오지 못했습니다."); } });
    return () => {
      active = false; clearInterval(heartbeat); window.removeEventListener("ott:analytics-pagehide", pagehide);
      element.removeEventListener("loadedmetadata", seekResume); element.removeEventListener("durationchange", seekResume);
      element.removeEventListener("timeupdate", update); element.removeEventListener("pause", pause);
      element.removeEventListener("seeking", seeking); element.removeEventListener("seeked", seeked); element.removeEventListener("ended", ended);
      element.removeEventListener("waiting", waiting); element.removeEventListener("playing", playing);
      element.removeEventListener("play", play); element.removeEventListener("ratechange", ratechange); element.removeEventListener("error", mediaError);
      analytics.stop(sample(), "unmount");
      element.pause(); hls?.destroy(); element.removeAttribute("src"); element.load();
    };
  }, [session, id, epoch]);
  if (!Number.isSafeInteger(id) || id < 1) return <p className="error">유효하지 않은 영상 ID입니다.</p>;
  const retry = () => { setLoading(true); setError(""); setSession(null); setRetryKey(value => value + 1); };
  return <><div className="page-heading"><div><span className="eyebrow">NOW WATCHING</span><h1>{session?.title ?? "영상 시청"}</h1><p>원하는 시점으로 이동한 뒤 다시 이어볼 수 있습니다.</p></div><Link className="button" href="/">← 목록으로</Link></div>
    <section className="panel player-panel">{loading && <p className="muted">재생 정보를 불러오는 중입니다…</p>}{error && <div className="error" role="alert">{error} <button className="button small" onClick={retry}>재시도</button></div>}
      <video ref={video} className="video" playsInline preload="metadata" onError={() => setError("영상을 재생하지 못했습니다. 재시도해 주세요.")} />
      {session && <div className="player-controls"><div className="control-row"><button className="button" onClick={() => { const element = video.current; if (!element) return; if (element.paused) void element.play().catch(() => setError("재생을 시작할 수 없습니다. 다시 시도해 주세요.")); else element.pause(); }}>{playing ? "일시정지" : "재생"}</button><span>{time(position)} / {time(duration || session.durationSeconds)}</span>{buffering && <span className="muted" role="status">버퍼링 중…</span>}</div><label className="slider-label">재생 위치<input type="range" min="0" max={Math.max(1, duration || session.durationSeconds)} step="0.1" value={Math.min(position, Math.max(1, duration || session.durationSeconds))} onChange={event => { const next = Number(event.target.value); if (video.current) video.current.currentTime = next; setPosition(next); }} /></label><div className="control-row"><label className="volume">음량 <input type="range" min="0" max="1" step="0.05" value={volume} onChange={event => { const next = Number(event.target.value); setVolume(next); if (video.current) video.current.volume = next; }} /></label><button className="button small" onClick={() => void video.current?.requestFullscreen()}>전체 화면</button></div></div>}
    </section></>;
}
