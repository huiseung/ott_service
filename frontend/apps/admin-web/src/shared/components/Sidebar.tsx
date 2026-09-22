"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
const links = [{ href: "/admin", label: "Dashboard" }, { href: "/admin/contents", label: "Contents" }, { href: "/admin/collections", label: "Collections" }, { href: "/admin/videos", label: "Videos" }, { href: "/admin/videos/upload", label: "Upload" }, { href: "/admin/processing", label: "Processing" }];
export function Sidebar() {
  const pathname = usePathname();
  return <aside className="sidebar"><Link href="/admin" className="brand"><span className="brand-mark">O</span> OTT <small>CONTROL</small></Link><nav aria-label="관리 메뉴">{links.map(link => <Link key={link.href} href={link.href} className={pathname === link.href || (link.href === "/admin/videos" && /^\/admin\/videos\/[^/]+$/.test(pathname)) ? "active" : ""}>{link.label}</Link>)}</nav><p className="sidebar-foot">ADMIN CONSOLE<br/>LOCAL ENVIRONMENT</p></aside>;
}
