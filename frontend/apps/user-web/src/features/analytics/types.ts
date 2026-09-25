export type PlaybackEventType = "PLAYBACK_SESSION_STARTED" | "PLAY" | "PAUSE" | "HEARTBEAT" | "SEEK"
  | "BUFFER_STARTED" | "BUFFER_ENDED" | "QUALITY_CHANGED" | "PLAYBACK_RATE_CHANGED"
  | "PLAYBACK_ENDED" | "PLAYBACK_ERROR" | "PLAYBACK_SESSION_ENDED";
export type BehaviorEventType = "CONTENT_IMPRESSION" | "CONTENT_CLICK" | "CONTENT_DETAIL_VIEW" | "SEARCH"
  | "WISHLIST_ADD" | "TRAILER_PLAY" | "SUBSCRIPTION_CTA_CLICK";
export type Facts = Record<string, string | number | boolean>;
export interface EventInput {
  eventType: PlaybackEventType | BehaviorEventType;
  playbackSessionId?: string;
  sequence?: number;
  contentId?: number;
  videoId?: number;
  payload: Facts;
}
export interface AnalyticsEvent extends EventInput {
  eventId: string;
  eventVersion: 1;
  occurredAt: string;
  anonymousId: string;
  sessionId: string;
  producer: "user-web";
  platform: "WEB";
}
