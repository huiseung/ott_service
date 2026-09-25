interface Options {
  apiBaseUrl: string;
  getToken: () => string | null;
  setToken: (token: string) => void;
  fetch: typeof fetch;
}

// Only used to avoid mixing accounts on the client; the collector verifies the signature.
function subject(token: string | null): string | null {
  if (!token) return null;
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const value = JSON.parse(atob(payload));
    return typeof value.sub === "string" ? value.sub : null;
  } catch { return null; }
}

export function createAnalyticsTransport(options: Options) {
  return async (body: string, capturedToken: string | null, keepalive: boolean): Promise<number> => {
    const capturedSubject = subject(capturedToken);
    const current = options.getToken();
    const sameAccount = (token: string | null) => capturedSubject !== null && subject(token) === capturedSubject;
    const token = sameAccount(current) ? current : capturedToken;
    const send = (bearer: string | null) => options.fetch(`${options.apiBaseUrl}/api/analytics/events/batch`, {
      method: "POST", credentials: "omit", keepalive, body,
      headers: { "Content-Type": "application/json", ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}) },
      signal: keepalive ? undefined : AbortSignal.timeout(15_000),
    });
    const response = await send(token);
    if (response.status !== 401 || keepalive || !sameAccount(options.getToken())) return response.status;

    // Long playback must survive token expiry. Never log out or switch identity for telemetry.
    const refreshed = await options.fetch(`${options.apiBaseUrl}/api/auth/refresh`, {
      method: "POST", credentials: "include", signal: AbortSignal.timeout(10_000),
    });
    if (!refreshed.ok) return refreshed.status;
    const result: unknown = await refreshed.json();
    if (!result || typeof result !== "object" || !("accessToken" in result)
        || typeof result.accessToken !== "string" || !sameAccount(result.accessToken)
        || !sameAccount(options.getToken())) return 401;
    options.setToken(result.accessToken);
    return (await send(result.accessToken)).status;
  };
}
