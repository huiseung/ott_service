import { notFound } from "next/navigation";
import { EpisodeDetailEditor } from "@/features/content/components/EpisodeDetailEditor";

export default async function EpisodePage({ params }: { params: Promise<{ episodeId: string }> }) {
  const { episodeId } = await params;
  const id = Number(episodeId);
  if (!/^[1-9]\d*$/.test(episodeId) || !Number.isSafeInteger(id)) notFound();
  return <EpisodeDetailEditor key={id} id={id} />;
}
