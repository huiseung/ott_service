import type { AnalyticsEvent, EventInput } from "./types";

type Transport = (body: string, token: string | null, keepalive: boolean) => Promise<number>;
interface Options {
  anonymousId: string;
  sessionId: string;
  token: () => string | null;
  transport: Transport;
  now?: () => number;
  uuid?: () => string;
  maxEvents?: number;
  maxBytes?: number;
  maxQueueEvents?: number;
  maxQueueBytes?: number;
  maxAttempts?: number;
  maxAgeMs?: number;
}
interface Entry { event: AnalyticsEvent; json: string; bytes: number; token: string | null; attempts: number; createdAt: number }
const encode = new TextEncoder();
const wrapperBytes = encode.encode('{"events":[]}').length;

/** Memory-only, bounded, at-least-once attempts. A logical event never changes on retry. */
export class AnalyticsClient {
  private queue: Entry[] = [];
  private inFlight: Entry[] = [];
  private retryAt = 0;
  private options: Options;
  private now: () => number;
  private uuid: () => string;
  readonly stats = { dropped: 0, failedBatches: 0, accepted: 0 };

  constructor(options: Options) {
    this.options = options;
    this.now = options.now ?? Date.now;
    this.uuid = options.uuid ?? (() => crypto.randomUUID());
  }

  track(input: EventInput, capturedToken?: string | null): string | null {
    try {
      const now = this.now();
      const event: AnalyticsEvent = { ...input, payload: { ...input.payload }, eventId: this.uuid(), eventVersion: 1,
        occurredAt: new Date(now).toISOString(), anonymousId: this.options.anonymousId,
        sessionId: this.options.sessionId, producer: "user-web", platform: "WEB" };
      const json = JSON.stringify(event);
      const bytes = encode.encode(json).length;
      const entry = { event, json, bytes, token: capturedToken === undefined ? this.options.token() : capturedToken,
        attempts: 0, createdAt: now };
      this.expire();
      if (bytes + wrapperBytes > this.maxBytes || this.pendingCount >= (this.options.maxQueueEvents ?? 500)
          || this.pendingBytes + bytes > (this.options.maxQueueBytes ?? 512 * 1024)) {
        this.stats.dropped++; return null;
      }
      this.queue.push(entry);
      if (this.queue.length >= this.maxEvents || this.pendingBytes >= this.maxBytes) void this.flush();
      return event.eventId;
    } catch { this.stats.dropped++; return null; }
  }

  get pendingCount() { return this.queue.length + this.inFlight.length; }
  private get pendingBytes() { return [...this.queue, ...this.inFlight].reduce((sum, entry) => sum + entry.bytes, 0); }
  private get maxEvents() { return this.options.maxEvents ?? 50; }
  private get maxBytes() { return this.options.maxBytes ?? 48 * 1024; }

  async flush(): Promise<void> {
    if (this.inFlight.length || this.now() < this.retryAt) return;
    this.expire();
    const batch = this.takeBatch(this.queue);
    if (!batch.length) return;
    this.queue.splice(0, batch.length);
    this.inFlight = batch;
    batch.forEach(entry => entry.attempts++);
    let status = 0;
    try { status = await this.options.transport(this.body(batch), batch[0].token, false); }
    catch { /* Network failures have the same bounded retry policy as 503. */ }
    this.inFlight = [];
    if (status === 202) {
      this.stats.accepted += batch.length;
      this.retryAt = 0;
      // Drain a backlog with sequential batches, never a request per track().
      if (this.queue.length) void this.flush();
      return;
    }
    this.stats.failedBatches++;
    const retryable = status === 0 || status === 408 || status === 429 || status >= 500;
    const retry = retryable ? batch.filter(entry => entry.attempts < (this.options.maxAttempts ?? 5)
      && this.now() - entry.createdAt < (this.options.maxAgeMs ?? 300_000)) : [];
    this.stats.dropped += batch.length - retry.length;
    this.queue.unshift(...retry);
    this.retryAt = retry.length ? this.now() + Math.min(30_000, 1000 * 2 ** (batch[0].attempts - 1)) : 0;
  }

  /** One sub-64KiB request; replay in-flight IDs if their acknowledgement may be lost. */
  async flushOnPageHide(): Promise<void> {
    this.expire();
    const batch = this.takeBatch([...this.inFlight, ...this.queue]);
    if (!batch.length) return;
    try {
      if (await this.options.transport(this.body(batch), batch[0].token, true) === 202) {
        const accepted = new Set(batch.map(entry => entry.event.eventId));
        this.queue = this.queue.filter(entry => !accepted.has(entry.event.eventId));
      }
    } catch { /* Browser termination is best effort, not a durable acknowledgement. */ }
  }

  private takeBatch(entries: Entry[]): Entry[] {
    const batch: Entry[] = [];
    let bytes = wrapperBytes;
    for (const entry of entries) {
      if (batch.length >= this.maxEvents || (batch.length && entry.token !== batch[0].token)
          || bytes + entry.bytes + (batch.length ? 1 : 0) > this.maxBytes) break;
      bytes += entry.bytes + (batch.length ? 1 : 0);
      batch.push(entry);
    }
    return batch;
  }

  private body(entries: Entry[]) { return `{"events":[${entries.map(entry => entry.json).join(",")}]}`; }
  private expire() {
    const size = this.queue.length;
    this.queue = this.queue.filter(entry => this.now() - entry.createdAt < (this.options.maxAgeMs ?? 300_000));
    this.stats.dropped += size - this.queue.length;
  }
}
