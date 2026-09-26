import "server-only";
import { cookies } from "next/headers";
import type { AdminSession } from "./apiClient";

export async function readAdminSession(): Promise<AdminSession | null> {
  const session = (await cookies()).get("OTT_ADMIN_SESSION");
  if (!session) return null;
  const baseUrl = process.env.ADMIN_API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_ADMIN_API_BASE_URL ?? "http://localhost:8080";
  try {
    const response = await fetch(`${baseUrl}/api/admin/auth/session`, {
      headers: { Cookie: `OTT_ADMIN_SESSION=${encodeURIComponent(session.value)}` },
      cache: "no-store", signal: AbortSignal.timeout(5000),
    });
    if (!response.ok) return null;
    return await response.json() as AdminSession;
  } catch { return null; }
}
