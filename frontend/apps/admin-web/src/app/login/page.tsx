import { redirect } from "next/navigation";
import { LoginForm } from "@/shared/components/LoginForm";
import { readAdminSession } from "@/shared/lib/adminSession";
export default async function LoginPage() {
  if (await readAdminSession()) redirect("/admin");
  return <LoginForm />;
}
