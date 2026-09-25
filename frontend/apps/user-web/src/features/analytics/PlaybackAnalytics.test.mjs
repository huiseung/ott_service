import test from "node:test";
import assert from "node:assert/strict";
import { PlaybackAnalytics } from "./PlaybackAnalytics.ts";

const sample = (positionMs, playbackRate = 1) => ({ positionMs, playbackRate, durationMs: 60000 });
function fixture() {
  let now = 0;
  const events = [];
  const analytics = new PlaybackAnalytics("session", 7, event => events.push(event), () => now);
  return { analytics, events, advance: ms => { now += ms; } };
}

test("heartbeats measure advancing wall time and seek distance contributes no watch time", () => {
  const { analytics, events, advance } = fixture();
  analytics.playing(sample(0));
  advance(1000); analytics.sample(sample(1000));
  analytics.seeking(sample(40000));
  advance(500); analytics.seeked(sample(40000));
  assert.equal(events.at(-1).payload.playedMsSincePreviousEvent, 1000);
  assert.equal(events.at(-1).payload.fromPositionMs, 1000);
  assert.equal(events.at(-1).payload.toPositionMs, 40000);
  advance(1000); analytics.record("HEARTBEAT", sample(41000));
  assert.equal(events.at(-1).payload.playedMsSincePreviousEvent, 1000);
  assert.deepEqual(events.map(e => e.sequence), [1, 2, 3]);
});

test("pause and buffering do not add watch time", () => {
  const { analytics, events, advance } = fixture();
  analytics.playing(sample(0));
  advance(1000); analytics.waiting(sample(1000));
  advance(5000); analytics.playing(sample(1000));
  assert.equal(events.at(-1).eventType, "BUFFER_ENDED");
  assert.equal(events.at(-1).payload.bufferingDurationMs, 5000);
  assert.equal(events.at(-1).payload.playedMsSincePreviousEvent, 0);
  analytics.pause(sample(1000));
  advance(5000); analytics.playing(sample(1000));
  assert.equal(events.at(-1).payload.playedMsSincePreviousEvent, 0);
});

test("2x playback stores actual elapsed viewing time and the observed rate", () => {
  const { analytics, events, advance } = fixture();
  analytics.playing(sample(0, 2));
  advance(1000); analytics.record("HEARTBEAT", sample(2000, 2));
  assert.equal(events.at(-1).payload.playedMsSincePreviousEvent, 1000);
  assert.equal(events.at(-1).payload.playbackRate, 2);
});

test("startup excludes idle time and initial waiting; one measurement per first play", () => {
  const { analytics, events, advance } = fixture();
  advance(9000); analytics.playRequested();
  analytics.waiting(sample(0));
  advance(1200); analytics.playRequested(); analytics.playing(sample(0));
  assert.equal(events[0].payload.startupTimeMs, 1200);
  assert.equal(events[0].payload.qoeVersion, 1);
  assert.equal(events.filter(e => e.eventType === "BUFFER_STARTED").length, 0);
  analytics.pause(sample(0)); advance(10000); analytics.playRequested(); analytics.playing(sample(0));
  assert.equal(events.at(-1).payload.startupTimeMs, undefined);
});

test("cancelled startup excludes paused time; missing play request has no fabricated zero", () => {
  const { analytics, events, advance } = fixture();
  analytics.playRequested(); advance(500); analytics.pause(sample(0));
  advance(5000); analytics.playRequested(); advance(100); analytics.playing(sample(0));
  assert.equal(events.at(-1).payload.startupTimeMs, 100);
  const other = fixture(); other.analytics.playing(sample(0));
  assert.equal(other.events[0].payload.startupTimeMs, undefined);
});

for (const action of ["pause", "seeking", "ended", "stop", "error"]) {
  test(`buffer closes once on ${action} and does not include later idle time`, () => {
    const { analytics, events, advance } = fixture();
    analytics.playing(sample(0)); advance(1000); analytics.waiting(sample(1000));
    analytics.waiting(sample(1000)); advance(2000);
    if (action === "error") analytics.error(sample(1000), "hls", "networkError", true);
    else analytics[action](sample(1000), "pagehide");
    advance(9000); analytics.stop(sample(1000), "unmount"); analytics.stop(sample(1000), "unmount");
    const buffers = events.filter(e => e.eventType === "BUFFER_ENDED");
    assert.equal(buffers.length, 1);
    assert.equal(buffers[0].payload.bufferingDurationMs, 2000);
    assert.equal(events.filter(e => e.eventType === "BUFFER_STARTED").length, 1);
    assert.equal(events.filter(e => e.eventType === "PLAYBACK_SESSION_ENDED").length, 1);
    assert.equal(events.reduce((sum, e) => sum + e.payload.playedMsSincePreviousEvent, 0), 1000);
  });
}

test("waiting while paused or seeking is excluded; recoverable errors preserve buffering", () => {
  const { analytics, events, advance } = fixture();
  analytics.playing(sample(0)); analytics.pause(sample(0)); analytics.waiting(sample(0));
  analytics.playing(sample(0)); analytics.seeking(sample(10000)); analytics.waiting(sample(10000));
  assert.equal(events.filter(e => e.eventType === "BUFFER_STARTED").length, 0);
  analytics.seeked(sample(10000)); analytics.playing(sample(10000)); analytics.waiting(sample(10000));
  advance(500); analytics.error(sample(10000), "hls", "bufferStalledError", false);
  advance(500); analytics.playing(sample(10000));
  assert.equal(events.at(-1).payload.bufferingDurationMs, 1000);
});
