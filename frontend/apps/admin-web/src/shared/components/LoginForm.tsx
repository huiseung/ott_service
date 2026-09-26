"use client";
import { useState } from "react";
import { ApiError, loginAdmin, userError } from "@/shared/lib/apiClient";

export function LoginForm() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function login(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError("");
    try {
      await loginAdmin(username, password); setPassword("");
      window.location.replace("/admin");
    } catch (reason) {
      setError(reason instanceof ApiError && reason.status === 401
        ? "사용자 이름 또는 비밀번호가 올바르지 않습니다." : userError(reason));
      setPassword(""); setBusy(false);
    }
  }
  return <div className="auth-wrap"><form className="panel auth-panel" onSubmit={event => void login(event)}>
    <span className="eyebrow">OTT CONTROL PLANE</span><h1>관리자 로그인</h1>
    <p className="muted">관리자 계정으로 로그인해 주세요.</p>
    <label>사용자 이름<input required autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} disabled={busy} /></label>
    <label>비밀번호<input required type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} disabled={busy} /></label>
    {error && <p className="error" role="alert">{error}</p>}
    <button className="button primary" type="submit" disabled={busy}>{busy ? "로그인 중…" : "로그인"}</button>
  </form></div>;
}
