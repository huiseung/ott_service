import { config } from "@/shared/lib/config";
import { videoUploadApi, type AckPart } from "../api/videoUploadApi";
export class AckBuffer {
  private pending = new Map<number, AckPart>();
  private timer: ReturnType<typeof setTimeout> | null = null;
  private current: Promise<void> = Promise.resolve();
  constructor(private videoFileId: number, private onConfirmed: (parts: AckPart[]) => void) {}
  add(part: AckPart) {
    this.pending.set(part.partNumber, part);
    if (this.pending.size >= config.ackBatchSize) void this.flush().catch(() => {});
    else if (!this.timer) this.timer = setTimeout(() => { void this.flush().catch(() => {}); }, config.ackDelayMs);
  }
  flush(): Promise<void> {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.current = this.current.catch(() => {}).then(async () => {
      if (!this.pending.size) return;
      const parts = [...this.pending.values()];
      try { await videoUploadApi.ack(this.videoFileId, parts); }
      catch (error) { if (!this.timer) this.timer = setTimeout(() => { void this.flush().catch(() => {}); }, config.ackDelayMs); throw error; }
      for (const part of parts) if (this.pending.get(part.partNumber) === part) this.pending.delete(part.partNumber);
      this.onConfirmed(parts);
    });
    return this.current;
  }
  dispose() { if (this.timer) clearTimeout(this.timer); }
}
