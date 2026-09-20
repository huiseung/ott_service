"use client";
import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { userApi, type CurrentUser } from "@/features/api";
type AuthState = { user: CurrentUser | null; loading: boolean; epoch: number; login: (id: string, password: string) => Promise<void>; logout: () => Promise<void> };
const Context = createContext<AuthState | null>(null);
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [epoch, setEpoch] = useState(0);
  useEffect(() => { let active = true; userApi.me().then(value => { if (active) setUser(value); }).catch(() => {}).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, []);
  const login = useCallback(async (id: string, password: string) => { const value = await userApi.login(id, password); setUser(value); setEpoch(value => value + 1); }, []);
  const logout = useCallback(async () => { setEpoch(value => value + 1); setUser(null); await userApi.logout(); }, []);
  return <Context.Provider value={{ user, loading, epoch, login, logout }}>{children}</Context.Provider>;
}
export function useAuth() { const value = useContext(Context); if (!value) throw new Error("AuthProvider is missing"); return value; }
