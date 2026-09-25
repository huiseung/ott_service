import type { EventInput, Facts, PlaybackEventType } from "./types";

interface Sample { positionMs: number; durationMs: number; playbackRate: number; ended?: boolean }

/** Accumulate observed advancing playback time, excluding seeks, pauses and buffering. */
export class PlaybackAnalytics {
  private sequence = 0;
  private previousPositionMs = 0;
  private lastPositionMs = 0;
  private lastTime = 0;
  private playedMs = 0;
  private advancing = false;
  private startedAt: number | null = null;
  private stopped = false;
  private firstPlay = true;
  private bufferStartedAt: number | null = null;
  private seekFrom: number | null = null;
  private sessionId: string;
  private videoId: number;
  private emit: (event: EventInput) => unknown;
  private now: () => number;

  constructor(sessionId: string, videoId: number, emit: (event: EventInput) => unknown, now = () => performance.now()) {
    this.sessionId = sessionId; this.videoId = videoId; this.emit = emit; this.now = now;
  }

  sample(value: Sample) {
    const now = this.now();
    if (this.advancing && this.seekFrom === null && this.bufferStartedAt === null) {
      const elapsed = Math.max(0, now - this.lastTime);
      const mediaElapsed = Math.max(0, value.positionMs - this.lastPositionMs) / value.playbackRate;
      this.playedMs += Math.min(elapsed, mediaElapsed);
    }
    this.lastTime = now;
    this.lastPositionMs = value.positionMs;
  }

  record(type: PlaybackEventType, value: Sample, facts: Facts = {}) {
    if (this.stopped) return;
    this.sample(value);
    const payload = { positionMs: Math.round(value.positionMs), previousPositionMs: Math.round(this.previousPositionMs),
      playedMsSincePreviousEvent: Math.round(this.playedMs), durationMs: Math.round(value.durationMs),
      playbackRate: value.playbackRate, ended: value.ended ?? false, ...facts, qoeVersion: 1 };
    this.emit({ eventType: type, playbackSessionId: this.sessionId, sequence: ++this.sequence,
      videoId: this.videoId, payload });
    this.previousPositionMs = value.positionMs;
    this.playedMs = 0;
  }

  playing(value: Sample) {
    this.stopped = false;
    this.closeBuffer(value, "playing");
    if (!this.advancing) this.record("PLAY", value, this.firstPlay && this.startedAt !== null
      ? { startupTimeMs: Math.round(this.now() - this.startedAt) } : {});
    this.firstPlay = false;
    this.advancing = true;
  }

  playRequested() {
    this.stopped = false;
    if (this.firstPlay && this.startedAt === null) this.startedAt = this.now();
  }

  pause(value: Sample) {
    this.closeBuffer(value, "pause");
    this.record("PAUSE", value); this.advancing = false;
    if (this.firstPlay) this.startedAt = null;
  }
  waiting(value: Sample) {
    if (this.firstPlay || !this.advancing || this.seekFrom !== null || this.bufferStartedAt !== null || this.stopped) return;
    this.record("BUFFER_STARTED", value);
    this.bufferStartedAt = this.now();
  }
  seeking(value: Sample) {
    this.closeBuffer(value, "seek");
    if (this.seekFrom === null) this.seekFrom = this.lastPositionMs;
    this.lastTime = this.now();
    this.lastPositionMs = value.positionMs;
  }
  seeked(value: Sample) {
    const fromPositionMs = this.seekFrom ?? this.lastPositionMs;
    this.record("SEEK", value, { fromPositionMs: Math.round(fromPositionMs), toPositionMs: Math.round(value.positionMs) });
    this.seekFrom = null;
  }
  stop(value: Sample, reason: string) {
    this.closeBuffer(value, reason);
    this.record("PLAYBACK_SESSION_ENDED", value, { reason });
    this.advancing = false;
    this.stopped = true;
  }
  ended(value: Sample) {
    this.closeBuffer(value, "ended");
    this.record("PLAYBACK_ENDED", value);
    this.advancing = false;
  }
  error(value: Sample, source: string, code: string, fatal: boolean) {
    if (fatal) this.closeBuffer(value, "error");
    this.record("PLAYBACK_ERROR", value, { source, code, fatal });
    if (fatal) this.advancing = false;
  }
  private closeBuffer(value: Sample, reason: string) {
    if (this.bufferStartedAt === null) return;
    this.record("BUFFER_ENDED", value, {
      bufferingDurationMs: Math.max(0, Math.round(this.now() - this.bufferStartedAt)), reason });
    this.bufferStartedAt = null;
  }
}
