"use client";

import Link from "next/link";
import { use, useEffect, useRef, useState } from "react";
import { adminVideoApi, type AdminVideoDetail } from "@/features/video/api/adminVideoApi";
import { apiRequest, userError } from "@/shared/lib/apiClient";
import { config } from "@/shared/lib/config";

type Playback = { manifestUrl: string; durationMs: number; signedUrlTtlSeconds: number };

export default function WatchPage({ params }: { params: Promise<{ videoId: string }> }) {
  const { videoId } = use(params);
  const id = Number(videoId);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [video, setVideo] = useState<AdminVideoDetail | null>(null);
  const [playback, setPlayback] = useState<Playback | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [playbackHint, setPlaybackHint] = useState("");

  useEffect(() => {
    if (!Number.isSafeInteger(id) || id < 1) return;
    adminVideoApi.getVideo(id).then(setVideo).catch(reason => setError(userError(reason)));
  }, [id]);

  async function start() {
    setBusy(true);
    setError("");
    setPlaybackHint("영상 연결 중…");
    try {
      const session = await apiRequest<Playback>(`/api/admin/videos/${id}/preview`, { method: "POST" });
      setPlayback(session);
    } catch (reason) {
      setPlaybackHint("");
      setError(userError(reason));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    const element = videoRef.current;
    if (!playback || !element) return;
    let active = true;
    let hls: import("hls.js").default | null = null;
    let refreshTimer: ReturnType<typeof setTimeout> | undefined;
    const manifest = new URL(playback.manifestUrl, config.adminApiBaseUrl).toString();
    void import("hls.js").then(({ default: Hls }) => {
      if (!active) return;
      if (!Hls.isSupported()) { setError("이 브라우저는 인증이 필요한 HLS 재생을 지원하지 않습니다."); return; }
      const load = (resumeAt = 0) => {
        if (!active) return;
        const shouldPlay = resumeAt === 0 || !element.paused;
        hls?.destroy();
        hls = new Hls({
          startPosition: resumeAt,
          xhrSetup: (xhr, url) => {
            xhr.open("GET", url, true);
            xhr.withCredentials = new URL(url, manifest).origin === new URL(config.adminApiBaseUrl).origin;
          },
        });
        hls.on(Hls.Events.ERROR, (_event, data) => {
          if (data.fatal && active) {
            setPlaybackHint("");
            setError(`HLS 재생 오류: ${data.details}. 다시 연결해 주세요.`);
          }
        });
        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          if (!active || !shouldPlay) return;
          setPlaybackHint("영상 준비 중…");
          void element.play().catch(() => {
            if (active) setPlaybackHint("브라우저가 자동 재생을 차단했습니다. 영상의 ▶ 버튼을 눌러주세요.");
          });
        });
        hls.loadSource(`${manifest}?renew=${Date.now()}`);
        hls.attachMedia(element);
        refreshTimer = setTimeout(() => load(element.currentTime), Math.max(5000, playback.signedUrlTtlSeconds * 700));
      };
      load();
    }).catch(() => { if (active) setError("HLS 플레이어를 불러오지 못했습니다."); });
    return () => {
      active = false;
      clearTimeout(refreshTimer);
      hls?.destroy();
      element.removeAttribute("src");
      element.load();
    };
  }, [playback]);

  if (!Number.isSafeInteger(id) || id < 1) return <p className="error">유효하지 않은 Video ID입니다.</p>;
  return <>
    <div className="page-heading"><div><span className="eyebrow">HLS PREVIEW / #{id}</span><h1>{video?.title ?? "영상 시청"}</h1><p>시간 막대를 클릭하거나 드래그해 원하는 시점으로 이동할 수 있습니다.</p></div><Link className="button" href={`/admin/videos/${id}`}>영상 상세</Link></div>
    {error && <div className="panel error" role="alert">{error}</div>}
    {video && video.status !== "READY" ? <section className="panel"><p>HLS 처리가 완료된 뒤 시청할 수 있습니다. 현재 상태: {video.status}</p></section> : <section className="panel watch-panel">
      <video ref={videoRef} controls playsInline preload="metadata" onPlaying={() => setPlaybackHint("")} onWaiting={() => setPlaybackHint("영상 버퍼링 중…")} />
      {playbackHint && <p className="muted" role="status">{playbackHint}</p>}
      <div className="watch-actions"><button className="button primary" disabled={busy} onClick={() => void start()}>{busy ? "연결 중…" : playback ? "재생 다시 시작" : "재생 시작"}</button>{!playback && <span className="muted">관리자 미리보기는 시청 기록을 저장하지 않습니다.</span>}</div>
    </section>}
  </>;
}
