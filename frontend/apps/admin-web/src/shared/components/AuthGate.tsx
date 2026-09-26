"use client";
import { createContext, useContext, useEffect, useState } from "react";
import { ApiError, getAdminSession, logoutAdmin, sessionExpiredEvent, userError } from "@/shared/lib/apiClient";

const AdminUsername = createContext("");
export function AuthGate({ username, children }: { username: string; children: React.ReactNode }) {
  const [active, setActive] = useState(true);
  useEffect(() => {
    const expire = () => { setActive(false); window.location.replace("/login"); };
    const verify = () => {
      void getAdminSession().catch(error => {
        if (error instanceof ApiError && [401, 403].includes(error.status)) expire();
      });
    };
    window.addEventListener(sessionExpiredEvent, expire);
    window.addEventListener("focus", verify);
    window.addEventListener("pageshow", verify);
    return () => {
      window.removeEventListener(sessionExpiredEvent, expire);
      window.removeEventListener("focus", verify);
      window.removeEventListener("pageshow", verify);
    };
  }, []);
  if (!active) return <div className="auth-wrap" role="status">로그인 화면으로 이동 중…</div>;
  return <AdminUsername.Provider value={username}>{children}</AdminUsername.Provider>;
}

export function AdminAccount() {
  const username = useContext(AdminUsername);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function logout() {
    setBusy(true); setError("");
    try { await logoutAdmin(); window.location.replace("/login"); }
    catch (reason) { setError(userError(reason)); setBusy(false); }
  }
  return <div className="admin-account">
    <span className="online"><i />{username} · 로그인 중</span>
    <button className="button small" disabled={busy} onClick={() => void logout()}>{busy ? "로그아웃 중…" : "로그아웃"}</button>
    {error && <span className="error" role="alert">{error}</span>}
  </div>;
}
