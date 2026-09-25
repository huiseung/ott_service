import { notFound } from "next/navigation";
import { ContentDetailEditor } from "@/features/content/components/ContentDetailEditor";

export default async function ContentDetailPage({ params }: { params: Promise<{ contentId: string }> }) {
  const { contentId } = await params;
  const id = Number(contentId);
  if (!/^[1-9]\d*$/.test(contentId) || !Number.isSafeInteger(id)) notFound();
  return <ContentDetailEditor key={id} id={id} />;
}
