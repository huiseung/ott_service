"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { uploadScheduler } from "@/features/video-upload/core/UploadScheduler";
import { UploadTask, savedUploads, type SavedUpload } from "@/features/video-upload/core/UploadTask";
import { useUploadProcessing } from "@/features/video-upload/components/useUploadProcessing";
import { ProgressBar, StatusBadge } from "@/shared/components/StatusBadge";
import type { ProcessingJobSummary } from "@/features/media-processing/api/mediaProcessingApi";

const formatBytes = (bytes: number) => bytes >= 1024 ** 3 ? `${(bytes / 1024 ** 3).toFixed(2)} GB` : bytes >= 1024 ** 2 ? `${(bytes / 1024 ** 2).toFixed(1)} MB` : `${Math.round(bytes / 1024)} KB`;

function UploadCard({ task, job, processingError, onRefresh }: { task: UploadTask; job?: ProcessingJobSummary; processingError: string; onRefresh: () => void }) {
  const state = task.snapshot();
  const sourceComplete = state.status === "completed";
  const sourcePercent = sourceComplete ? 100 : state.fileSize ? state.uploadedBytes / state.fileSize * 100 : 0;
  return <div className="panel task">
    <div className="section-head"><div><h3>{state.title}</h3><p className="muted">{state.fileName} · {formatBytes(state.fileSize)}</p></div><StatusBadge value={state.status.toUpperCase()} /></div>
    <div className="upload-stage"><div className="section-head"><strong>1. 원본 업로드</strong><span>{Math.floor(sourcePercent)}%</span></div><ProgressBar value={sourcePercent} label={`${state.title} 원본 업로드 진행률`} /><div className="task-meta"><span>{formatBytes(sourceComplete ? state.fileSize : state.uploadedBytes)} / {formatBytes(state.fileSize)}</span><span>{state.confirmedParts} / {state.totalParts || "—"} parts</span>{!sourceComplete && <><span>{formatBytes(state.speed)}/s</span><span>{state.speed > 0 ? `약 ${Math.ceil((state.fileSize - state.uploadedBytes) / state.speed)}초 남음` : "남은 시간 계산 중"}</span><span>재시도 {state.retryCount}</span></>}</div></div>
    <div className="upload-stage"><div className="section-head"><strong>2. HLS 인코딩</strong>{job && <StatusBadge value={job.status} />}</div><ProgressBar value={job?.progressPercent ?? 0} label={`${state.title} HLS 예상 처리 진행률`} /><div className="task-meta">{!sourceComplete ? <span>원본 업로드 완료 후 시작</span> : job ? <><span>{job.stage.replaceAll("_", " ")}</span><span>예상 진행률 {job.progressPercent}%</span><span>시도 {job.attempt}</span></> : <span>처리 작업 확인 중…</span>}</div>{job?.errorCode && <p className="error">{job.errorCode}</p>}{processingError && sourceComplete && <p className="error" role="alert">인코딩 상태를 불러오지 못했습니다. {processingError}</p>}</div>
    {state.error && <p className="error" role="alert">{state.error}</p>}
    <div className="actions">{state.status === "uploading" && <button className="button small" onClick={() => task.pause()}>일시정지</button>}{(state.status === "paused" || state.status === "failed") && <button className="button small" onClick={() => task.resume()}>재개</button>}{sourceComplete && <button className="button small" onClick={onRefresh}>인코딩 상태 새로고침</button>}{state.videoId && <Link className="button small" href={`/admin/videos/${state.videoId}`}>영상 상세</Link>}</div>
  </div>;
}

export default function UploadPage() {
  const [tick, setTick] = useState(0);
  const [pending, setPending] = useState<{ file: File; title: string; saved?: SavedUpload }[]>([]);
  const [saved, setSaved] = useState<SavedUpload[]>([]);
  const [error, setError] = useState("");
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { const timer = setTimeout(() => setSaved(savedUploads()), 0); const unsubscribe = uploadScheduler.subscribe(() => setTick(value => value + 1)); return () => { clearTimeout(timer); unsubscribe(); }; }, []);
  function addFiles(files: FileList | File[]) { setPending(previous => [...previous, ...Array.from(files).filter(file => file.size > 0).map(file => ({ file, title: file.name.replace(/\.[^.]+$/, "") }))]); }
  function queue() { for (const item of pending) { if (!item.title.trim() || item.title.length > 300) { setError("제목은 1~300자로 입력하세요."); return; } uploadScheduler.add(new UploadTask(item.file, item.title, uploadScheduler.notify, item.saved)); } setPending([]); setError(""); }
  const tasks = uploadScheduler.getTasks(); void tick;
  const completedIds = tasks.filter(task => task.status === "completed" && task.videoId).map(task => task.videoId!);
  const processing = useUploadProcessing(completedIds);
  return <>
    <div className="page-heading"><div><span className="eyebrow">INGEST / MULTIPART</span><h1>원본 영상 업로드</h1><p>원본 업로드와 HLS 인코딩을 한곳에서 확인합니다.</p></div><span className="muted">최대 3개 파일 · 6개 파트 동시 전송</span></div>
    <section className="panel"><div className="dropzone" onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); addFiles(event.dataTransfer.files); }}><span className="drop-icon">↑</span><h2>영상 파일을 여기에 놓으세요</h2><p>여러 파일 선택 가능 · 업로드 전 제목을 수정할 수 있습니다.</p><input ref={input} className="sr-only" type="file" accept="video/*" multiple aria-label="영상 파일 선택" onChange={event => { if (event.target.files) addFiles(event.target.files); event.target.value = ""; }} /><button className="button" onClick={() => input.current?.click()}>파일 선택</button></div>
      {pending.length > 0 && <div className="pending"><div className="section-head"><h2>업로드 대기 · {pending.length}</h2><button className="button primary" onClick={queue}>업로드 시작</button></div>{pending.map((item, index) => <div className="pending-row" key={`${item.file.name}-${index}`}><div><strong>{item.file.name}</strong><p className="muted">{formatBytes(item.file.size)}{item.saved ? " · 기존 업로드 재개" : ""}</p></div><label>영상 제목<input value={item.title} onChange={event => setPending(items => items.map((candidate, i) => i === index ? { ...candidate, title: event.target.value } : candidate))} /></label><button className="button small" aria-label={`${item.file.name} 제거`} onClick={() => setPending(items => items.filter((_, i) => i !== index))}>제거</button></div>)}</div>}
    </section>
    {error && <p className="error" role="alert">{error}</p>}
    {saved.length > 0 && <section className="panel"><div className="section-head"><h2>완료되지 않은 업로드</h2><span className="muted">같은 로컬 파일을 다시 선택하세요</span></div>{saved.map(item => <div className="saved-row" key={item.videoFileId}><div><strong>{item.title}</strong><p className="muted">{item.fileName} · {formatBytes(item.fileSize)} · VideoFile #{item.videoFileId}</p></div><label className="button small">파일 다시 선택<input className="sr-only" type="file" onChange={event => { const file = event.target.files?.[0]; if (file) { setPending(items => [...items, { file, title: item.title, saved: item }]); setSaved(items => items.filter(value => value.videoFileId !== item.videoFileId)); } }} /></label></div>)}</section>}
    {tasks.length > 0 && <section><div className="section-head"><h2>전송 및 인코딩 현황</h2><span className="muted">{tasks.length}개 파일</span></div>{tasks.map(task => <UploadCard key={task.id} task={task} job={task.videoId ? processing.jobs[task.videoId] : undefined} processingError={processing.error} onRefresh={processing.refresh} />)}</section>}
  </>;
}
