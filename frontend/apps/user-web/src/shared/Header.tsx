"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/AuthProvider";
export function Header() {
  const { user, loading, logout } = useAuth(); const router = useRouter();
  return <header className="topbar"><div className="topbar-inner"><Link href="/" className="brand"><span>O</span> OTT</Link><nav aria-label="주 메뉴"><Link href="/">영상 목록</Link></nav><div className="account">{loading ? <span className="muted">확인 중…</span> : user ? <><span>{user.displayName}</span><button className="button small" onClick={() => void logout().finally(() => router.replace("/"))}>로그아웃</button></> : <Link className="button small" href="/login">로그인</Link>}</div></div></header>;
}
