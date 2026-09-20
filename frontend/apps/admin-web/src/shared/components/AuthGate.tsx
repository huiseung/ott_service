"use client";
import { useState } from "react";
import { hasAdminCredentials, setAdminCredentials } from "@/shared/lib/apiClient";
export function AuthGate({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(hasAdminCredentials);
  const [username, setUsername] = useState(""); const [password, setPassword] = useState("");
  if (ready) return children;
  return <div className="auth-wrap"><form className="panel auth-panel" onSubmit={event => { event.preventDefault(); setAdminCredentials(username, password); setReady(true); }}><span className="eyebrow">OTT CONTROL PLANE</span><h1>관리자 연결</h1><p className="muted">현재 백엔드의 HTTP Basic 계정을 입력하세요. 입력값은 이 탭의 메모리에만 보관됩니다.</p><label>사용자 이름<input required autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} /></label><label>비밀번호<input required type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} /></label><button className="button primary" type="submit">콘솔 열기</button></form></div>;
}
