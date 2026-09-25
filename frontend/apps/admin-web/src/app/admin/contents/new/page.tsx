import Link from "next/link";
import { ContentCreate } from "@/features/content/components/ContentCreate";

export default function NewContentPage() {
  return <><div className="page-heading"><div><span className="eyebrow">CMS / CONTENT</span><h1>Content 생성</h1></div><Link className="button" href="/admin/contents">목록</Link></div><ContentCreate /></>;
}
