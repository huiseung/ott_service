import type { Metadata } from "next";
import { AuthProvider } from "@/features/auth/AuthProvider";
import { Header } from "@/shared/Header";
import { AnalyticsLifecycle } from "@/features/analytics/AnalyticsLifecycle";
import "./globals.css";
export const metadata: Metadata = { title: "OTT", description: "영상 시청" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><body><AuthProvider><AnalyticsLifecycle /><Header /><main className="content">{children}</main></AuthProvider></body></html>;
}
