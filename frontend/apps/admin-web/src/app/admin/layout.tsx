import { Sidebar } from "@/shared/components/Sidebar";
import { AuthGate } from "@/shared/components/AuthGate";
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <AuthGate><div className="shell"><Sidebar /><main className="main"><header className="topbar"><span>OTT / OPERATIONS</span><span className="online"><i /> ADMIN API</span></header><div className="content">{children}</div></main></div></AuthGate>;
}
