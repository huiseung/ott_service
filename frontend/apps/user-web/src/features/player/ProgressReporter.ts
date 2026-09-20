import { userApi, type ProgressEvent } from "@/features/api";
import { config } from "@/shared/config";

export class ProgressReporter {
  private sequence = 0;
  private active = true;
  private sending = false;
  private pending: ProgressEvent | null = null;
  constructor(private videoId: number, private playbackSessionId: number, private durationMs: number) {}
  report(seconds: number) {
    if (!this.active || !Number.isFinite(seconds)) return;
    this.pending = { playbackSessionId: this.playbackSessionId, clientEventSeq: ++this.sequence,
      positionMs: Math.max(0, Math.min(Math.round(seconds * 1000), this.durationMs)), durationMs: this.durationMs };
    void this.drain();
  }
  private async drain() {
    if (this.sending || !this.active) return;
    this.sending = true;
    while (this.pending && this.active) {
      const event = this.pending; this.pending = null;
      try { await userApi.progress(this.videoId, event); }
      catch {
        if (!this.active) break;
        await new Promise(resolve => setTimeout(resolve, 1200));
        if (!this.active) break;
        try { await userApi.progress(this.videoId, event); } catch { /* Next event may still be saved. */ }
      }
    }
    this.sending = false;
  }
  leave(seconds: number) {
    if (!this.active || !Number.isFinite(seconds)) return;
    const event: ProgressEvent = { playbackSessionId: this.playbackSessionId, clientEventSeq: ++this.sequence,
      positionMs: Math.max(0, Math.min(Math.round(seconds * 1000), this.durationMs)), durationMs: this.durationMs };
    this.active = false; this.pending = null;
    void fetch(`${config.apiBaseUrl}/api/user/videos/${this.videoId}/progress`, {
      method: "POST", credentials: "include", keepalive: true,
      headers: { "Content-Type": "application/json" }, body: JSON.stringify(event),
    }).catch(() => {});
  }
  cancel() { this.active = false; this.pending = null; }
}
