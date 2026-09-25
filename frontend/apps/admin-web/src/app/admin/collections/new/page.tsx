import Link from "next/link";
import { CollectionCreate } from "@/features/collection/components/CollectionCreate";
export default function NewCollectionPage() {
  return <><div className="page-heading"><div><span className="eyebrow">CMS / COLLECTION</span><h1>Collection 생성</h1></div><Link className="button" href="/admin/collections">목록</Link></div><CollectionCreate /></>;
}
