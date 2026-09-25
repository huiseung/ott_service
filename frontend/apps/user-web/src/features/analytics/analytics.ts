import { config } from "@/shared/config";
import { getAccessToken, setAccessToken } from "@/shared/apiClient";
import { AnalyticsClient } from "./AnalyticsClient";
import { createAnalyticsTransport } from "./AnalyticsTransport";
import type { EventInput } from "./types";

let client: AnalyticsClient | undefined;
function storedId(kind: "localStorage" | "sessionStorage", key: string): string {
  const fallback = crypto.randomUUID();
  try {
    const storage = window[kind];
    const value = storage.getItem(key);
    if (value && /^[0-9a-f-]{36}$/i.test(value)) return value;
    storage.setItem(key, fallback);
  } catch { /* Storage may be disabled; this page can still collect events. */ }
  return fallback;
}

export function getAnalytics(): AnalyticsClient | undefined {
  if (typeof window === "undefined") return;
  try {
    client ??= new AnalyticsClient({
      anonymousId: storedId("localStorage", "ott_analytics_anonymous_id"),
      sessionId: storedId("sessionStorage", "ott_analytics_session_id"),
      token: getAccessToken,
      transport: createAnalyticsTransport({ apiBaseUrl: config.apiBaseUrl, getToken: getAccessToken,
        setToken: setAccessToken, fetch: (...args) => fetch(...args) }),
    });
    return client;
  } catch { return; }
}

export function trackEvent(event: EventInput, capturedToken?: string | null) {
  if (typeof window === "undefined") return;
  return getAnalytics()?.track({ ...event, payload: { ...event.payload, userAgent: navigator.userAgent.slice(0, 512) } }, capturedToken);
}

export function startAnalytics(): () => void {
  const analytics = getAnalytics();
  if (!analytics) return () => {};
  const timer = window.setInterval(() => void analytics.flush(), 25_000);
  const pagehide = () => {
    // Collect final player facts before forming the unload batch, regardless of listener order.
    window.dispatchEvent(new Event("ott:analytics-pagehide"));
    void analytics.flushOnPageHide();
  };
  window.addEventListener("pagehide", pagehide);
  return () => { clearInterval(timer); window.removeEventListener("pagehide", pagehide); };
}
