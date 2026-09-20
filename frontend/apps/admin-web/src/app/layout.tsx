import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = { title: "OTT Admin", description: "OTT 운영 콘솔" };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><body>{children}</body></html>;
}
