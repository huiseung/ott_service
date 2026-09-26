import { NextRequest, NextResponse } from "next/server";
export function proxy(request: NextRequest) {
  // Routing only; the server layout and backend verify the session itself.
  if (!request.cookies.has("OTT_ADMIN_SESSION")) return NextResponse.redirect(new URL("/login", request.url));
  const response = NextResponse.next();
  response.headers.set("Cache-Control", "no-store");
  return response;
}
export const config = { matcher: "/admin/:path*" };
