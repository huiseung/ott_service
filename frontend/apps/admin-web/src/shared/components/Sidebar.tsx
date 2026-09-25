"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";

const groups = [
  { label: "Insights", links: [{ href: "/admin/analytics", label: "Analytics" }] },
  { label: "CMS", links: [{ href: "/admin/contents", label: "Content" }, { href: "/admin/collections", label: "Collections" }] },
  { label: "Media", links: [{ href: "/admin/videos", label: "Videos" }, { href: "/admin/videos/upload", label: "Upload" }, { href: "/admin/processing", label: "Processing" }] },
];

export function Sidebar() {
  const pathname = usePathname();
  function active(href: string) {
    if (href === "/admin/contents" && pathname.startsWith("/admin/episodes/")) return true;
    if (href === "/admin/videos" && pathname === "/admin/videos/upload") return false;
    return pathname === href || pathname.startsWith(`${href}/`);
  }
  return <aside className="sidebar">
    <Link href="/admin" className="brand"><span className="brand-mark">O</span> OTT <small>CONTROL</small></Link>
    <nav aria-label="관리 메뉴">
      <Link href="/admin" className={pathname === "/admin" ? "active" : ""} aria-current={pathname === "/admin" ? "page" : undefined}>Dashboard</Link>
      {groups.map(group => <div className="nav-group" key={group.label}>
        <span className="nav-group-label">{group.label}</span>
        {group.links.map(link => <Link key={link.href} href={link.href} className={active(link.href) ? "active" : ""} aria-current={active(link.href) ? "page" : undefined}>{link.label}</Link>)}
      </div>)}
    </nav>
    <p className="sidebar-foot">ADMIN CONSOLE<br />LOCAL ENVIRONMENT</p>
  </aside>;
}
