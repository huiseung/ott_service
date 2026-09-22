import { userApi, type WatchEventRequest, type WatchEventType } from "@/features/api";
import { config } from "@/shared/config";
import { getAccessToken } from "@/shared/apiClient";

export class ProgressReporter {
  private sequence = 0;
  private active = true;
  private sending = false;
  private pending: WatchEventRequest | null = null;
  constructor(private playbackSessionId: string, private durationSeconds: number) {}
  report(seconds: number, eventType: WatchEventType = "PROGRESS") {
    if (!this.active || !Number.isFinite(seconds)) return;
    this.pending = { eventType, sequence: ++this.sequence,
      positionSeconds: Math.max(0, Math.min(Math.round(seconds), this.durationSeconds)) };
    void this.drain();
  }
  private async drain() {
    if (this.sending || !this.active) return;
    this.sending = true;
    while (this.pending && this.active) {
      const event = this.pending; this.pending = null;
      try { await userApi.event(this.playbackSessionId, event); }
      catch {
        if (!this.active) break;
        await new Promise(resolve => setTimeout(resolve, 1200));
        if (!this.active) break;
        try { await userApi.event(this.playbackSessionId, event); } catch { /* Next event may still be saved. */ }
      }
    }
    this.sending = false;
  }
  leave(seconds: number, eventType: WatchEventType = "SESSION_END") {
    if (!this.active || !Number.isFinite(seconds)) return;
    const event: WatchEventRequest = { eventType, sequence: ++this.sequence,
      positionSeconds: Math.max(0, Math.min(Math.round(seconds), this.durationSeconds)) };
    this.active = false; this.pending = null;
    void fetch(`${config.apiBaseUrl}/api/playback/sessions/${this.playbackSessionId}/events`, {
      method: "POST", credentials: "include", keepalive: true,
      headers: { "Content-Type": "application/json", ...(getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {}) }, body: JSON.stringify(event),
    }).catch(() => {});
  }
  cancel() { this.active = false; this.pending = null; }
}
