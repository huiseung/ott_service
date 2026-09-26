import { Sidebar } from "@/shared/components/Sidebar";
import { AuthGate, AdminAccount } from "@/shared/components/AuthGate";
import { readAdminSession } from "@/shared/lib/adminSession";
import { redirect } from "next/navigation";
export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const session = await readAdminSession();
  if (!session) redirect("/login");
  return <AuthGate username={session.username}><div className="shell"><Sidebar /><main className="main"><header className="topbar"><span>OTT / OPERATIONS</span><AdminAccount /></header><div className="content">{children}</div></main></div></AuthGate>;
}
