import { videoUploadApi, type AckPart } from "../api/videoUploadApi";
import { createFileFingerprint } from "../utils/createFileFingerprint";
import { AckBuffer } from "./AckBuffer";
import { config } from "@/shared/lib/config";

export type TaskStatus = "pending" | "preparing" | "uploading" | "paused" | "completing" | "completed" | "failed";
export interface UploadSnapshot { id: string; title: string; fileName: string; fileSize: number; videoId?: number; videoFileId?: number; status: TaskStatus; confirmedParts: number; totalParts: number; uploadedBytes: number; speed: number; retryCount: number; error?: string }
export interface SavedUpload { videoId: number; videoFileId: number; title: string; fileName: string; fileSize: number; fingerprint: string }
const STORAGE_KEY = "ott-admin-uploads";
export function savedUploads(): SavedUpload[] { try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]") as SavedUpload[]; } catch { return []; } }
function saveUpload(upload: SavedUpload) { localStorage.setItem(STORAGE_KEY, JSON.stringify([...savedUploads().filter(item => item.videoFileId !== upload.videoFileId), upload])); }
function removeUpload(id: number) { localStorage.setItem(STORAGE_KEY, JSON.stringify(savedUploads().filter(item => item.videoFileId !== id))); }
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

export class UploadTask {
  readonly id = crypto.randomUUID();
  status: TaskStatus = "pending";
  videoId?: number; videoFileId?: number; partSize = 0; totalParts = 0; retryCount = 0; error?: string;
  private confirmed = new Set<number>(); private pendingAck = new Set<number>(); private sent = new Map<number, number>(); private inflight = new Set<number>();
  private buffer?: AckBuffer; private starting?: Promise<void>; private completed = false;
  private samples: { at: number; bytes: number }[] = [];
  constructor(public file: File, public title: string, private changed: () => void, private saved?: SavedUpload) {}
  get activeParts() { return this.inflight.size; }
  get canSchedule() { return this.status === "uploading" && this.inflight.size < config.maxPartsPerFile && this.nextPart() !== null; }
  snapshot(): UploadSnapshot {
    const confirmedBytes = [...this.confirmed].reduce((sum, n) => sum + this.partLength(n), 0);
    const pendingBytes = [...this.pendingAck].reduce((sum, n) => sum + this.partLength(n), 0);
    const uploadedBytes = Math.min(this.file.size, confirmedBytes + pendingBytes + [...this.sent.values()].reduce((a, b) => a + b, 0));
    const now = Date.now();
    if (!this.samples.length || now - this.samples[this.samples.length - 1].at >= 200) this.samples.push({ at: now, bytes: uploadedBytes });
    this.samples = this.samples.filter(sample => now - sample.at <= 5000);
    const first = this.samples[0];
    const speed = first && now > first.at ? Math.max(0, uploadedBytes - first.bytes) / ((now - first.at) / 1000) : 0;
    return { id: this.id, title: this.title, fileName: this.file.name, fileSize: this.file.size, videoId: this.videoId, videoFileId: this.videoFileId, status: this.status, confirmedParts: this.confirmed.size, totalParts: this.totalParts, uploadedBytes, speed, retryCount: this.retryCount, error: this.error };
  }
  private emit() { this.changed(); }
  private partLength(n: number) { return Math.max(0, Math.min(this.partSize, this.file.size - (n - 1) * this.partSize)); }
  private nextPart() { for (let n = 1; n <= this.totalParts; n++) if (!this.confirmed.has(n) && !this.pendingAck.has(n) && !this.inflight.has(n)) return n; return null; }
  async start() {
    if (this.starting) return this.starting;
    this.starting = this.prepare();
    try { await this.starting; } finally { this.starting = undefined; }
  }
  private async prepare() {
    this.status = "preparing"; this.error = undefined; this.emit();
    try {
      const fingerprint = await createFileFingerprint(this.file);
      if (this.saved && (this.saved.fingerprint !== fingerprint || this.saved.fileSize !== this.file.size)) throw new Error("기존 업로드에 사용된 파일과 일치하지 않습니다.");
      if (this.saved) { this.videoId = this.saved.videoId; this.videoFileId = this.saved.videoFileId; }
      else {
        const created = await videoUploadApi.create(this.title.trim(), this.file, fingerprint, this.id);
        this.videoId = created.videoId; this.videoFileId = created.videoFileId;
        saveUpload({ videoId: created.videoId, videoFileId: created.videoFileId, title: this.title, fileName: this.file.name, fileSize: this.file.size, fingerprint });
      }
      const state = await videoUploadApi.status(this.videoFileId!);
      if (state.fingerprint !== fingerprint) throw new Error("기존 업로드에 사용된 파일과 일치하지 않습니다.");
      this.partSize = state.partSize; this.totalParts = state.totalParts; this.confirmed = new Set(state.confirmedParts);
      if (state.status === "COMPLETED") { this.status = "completed"; removeUpload(this.videoFileId!); }
      else if (state.status !== "UPLOADING" && state.status !== "COMPLETING") throw new Error(`업로드를 재개할 수 없는 상태입니다: ${state.status}`);
      else {
        this.buffer = new AckBuffer(this.videoFileId!, parts => { parts.forEach(part => { this.pendingAck.delete(part.partNumber); this.confirmed.add(part.partNumber); }); this.emit(); void this.tryComplete(); });
        this.status = state.status === "COMPLETING" ? "completing" : "uploading";
        if (this.status === "completing") await this.complete();
      }
    } catch (error) { this.fail(error); }
    this.emit();
  }
  pause() { if (this.status === "uploading") { this.status = "paused"; this.emit(); } }
  resume() { if (this.status === "paused") { this.status = "uploading"; this.emit(); void this.tryComplete(); } else if (this.status === "failed") void this.start(); }
  private fail(error: unknown) { this.status = "failed"; this.error = error instanceof Error ? error.message : String(error); this.emit(); }
  async uploadNext(): Promise<void> {
    const n = this.nextPart(); if (n === null || !this.canSchedule || !this.videoFileId) return;
    this.inflight.add(n); this.emit();
    try {
      const size = this.partLength(n);
      let uploaded: AckPart | null = null;
      for (let attempt = 0; attempt <= config.maxPartRetries; attempt++) {
        try {
          const presigned = await videoUploadApi.presign(this.videoFileId, [n]);
          uploaded = await this.put(presigned.parts[0].url, n, size);
          break;
        } catch (error) {
          if (attempt === config.maxPartRetries) throw error;
          this.retryCount++; this.emit(); await delay(500 * 2 ** attempt + Math.random() * 250);
        }
      }
      if (uploaded) { this.sent.delete(n); this.pendingAck.add(n); this.buffer?.add(uploaded); await this.tryComplete(); }
    } catch (error) { this.fail(error); }
    finally { this.inflight.delete(n); this.sent.delete(n); this.emit(); await this.tryComplete(); }
  }
  private put(url: string, n: number, size: number): Promise<AckPart> {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest(); xhr.open("PUT", url);
      xhr.upload.onprogress = event => { this.sent.set(n, event.loaded); this.emit(); };
      xhr.onerror = () => reject(new Error("네트워크 연결이 불안정합니다."));
      xhr.onload = () => {
        if (xhr.status < 200 || xhr.status >= 300) return reject(new Error(`파트 ${n} 전송 실패 (HTTP ${xhr.status})`));
        const etag = xhr.getResponseHeader("ETag");
        if (!etag) return reject(new Error("Object Storage CORS 설정에서 ETag response header 노출을 확인하세요."));
        resolve({ partNumber: n, etag, size });
      };
      xhr.send(this.file.slice((n - 1) * this.partSize, Math.min(n * this.partSize, this.file.size)));
    });
  }
  private async tryComplete() {
    if (this.completed || this.status !== "uploading" || this.inflight.size || this.confirmed.size !== this.totalParts) return;
    this.completed = true; this.status = "completing"; this.emit();
    try { await this.buffer?.flush(); await this.complete(); } catch (error) { this.completed = false; this.fail(error); }
  }
  private async complete() {
    try { await videoUploadApi.complete(this.videoFileId!); }
    catch (error) { const state = await videoUploadApi.status(this.videoFileId!); if (state.status !== "COMPLETED") throw error; }
    this.status = "completed"; removeUpload(this.videoFileId!); this.buffer?.dispose(); this.emit();
  }
}
