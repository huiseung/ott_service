"use client";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError, errorMessage } from "@/shared/apiClient";

function LoginForm() {
  const router = useRouter(); const params = useSearchParams(); const { login } = useAuth();
  const [loginId, setLoginId] = useState(""); const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false); const [error, setError] = useState("");
  const requested = params.get("next") ?? "/";
  const destination = /^\/watch\/[1-9]\d*$/.test(requested) ? requested : "/";
  async function submit(event: React.FormEvent) {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError("");
    try { await login(loginId, password); router.replace(destination); }
    catch (reason) { setError(reason instanceof ApiError && reason.status === 401 ? "로그인 ID 또는 비밀번호가 올바르지 않습니다." : errorMessage(reason)); }
    finally { setBusy(false); }
  }
  return <div className="auth-wrap"><form className="panel auth-panel" onSubmit={submit}><span className="eyebrow">OTT ACCOUNT</span><h1>로그인</h1><p className="muted">영상을 시청하려면 로그인하세요.</p><label>로그인 ID<input value={loginId} onChange={event => setLoginId(event.target.value)} autoComplete="username" required maxLength={100} /></label><label>비밀번호<input value={password} onChange={event => setPassword(event.target.value)} type="password" autoComplete="current-password" required /></label>{error && <p className="error" role="alert">{error}</p>}<button className="button primary" disabled={busy} type="submit">{busy ? "로그인 중…" : "로그인"}</button></form></div>;
}
export default function LoginPage() { return <Suspense fallback={<p className="muted">로그인 화면을 불러오는 중입니다…</p>}><LoginForm /></Suspense>; }
