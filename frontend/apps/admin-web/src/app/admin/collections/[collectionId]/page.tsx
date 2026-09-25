import { notFound } from "next/navigation";
import { CollectionDetailEditor } from "@/features/collection/components/CollectionDetailEditor";

export default async function CollectionDetailPage({ params }: { params: Promise<{ collectionId: string }> }) {
  const { collectionId } = await params;
  const id = Number(collectionId);
  if (!/^[1-9]\d*$/.test(collectionId) || !Number.isSafeInteger(id)) notFound();
  return <CollectionDetailEditor key={id} id={id} />;
}
