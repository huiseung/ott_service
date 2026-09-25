"use client";
import { useCallback } from "react";
import { adminContentApi, type ContentImageType, type EpisodeImageType } from "../api/adminContentApi";
import { ArtworkManager, type ArtworkSlot } from "./ArtworkManager";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";

const contentSlots: ArtworkSlot<ContentImageType>[] = [
  { type: "POSTER", label: "POSTER", ratio: "2:3", minimum: "600x900" },
  { type: "LANDSCAPE", label: "LANDSCAPE", ratio: "16:9", minimum: "1280x720" },
  { type: "HERO", label: "HERO", ratio: "16:9", minimum: "1920x1080" },
];
const episodeSlots: ArtworkSlot<EpisodeImageType>[] = [{ type: "THUMBNAIL", label: "THUMBNAIL", ratio: "16:9", minimum: "1280x720" }];

export function ContentArtwork({ id }: { id: number }) {
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminContentApi.images(id, signal), [id]));
  return <section className="panel" id="artwork"><div className="section-head"><h2>Content Artwork</h2></div>
    <ResourceFeedback resource={resource} label="Content Artwork" />
    {!resource.loading && !resource.error && resource.data && <>
      <ArtworkManager slots={contentSlots} images={resource.data.images} onRefresh={resource.reload}
        onUpload={async (type, file) => { const image = await adminContentApi.uploadImage(id, type, file); resource.setData(current => ({ images: [...(current?.images ?? []).filter(value => value.type !== type), image] })); }}
        onDelete={async type => { await adminContentApi.deleteImage(id, type); resource.setData(current => ({ images: (current?.images ?? []).filter(value => value.type !== type) })); }} />
    </>}
  </section>;
}

export function EpisodeArtwork({ id }: { id: number }) {
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminContentApi.episodeImages(id, signal), [id]));
  return <section className="panel"><h2>Episode Thumbnail</h2>
    <ResourceFeedback resource={resource} label="Episode Thumbnail" />
    {!resource.loading && !resource.error && resource.data && <ArtworkManager slots={episodeSlots} images={resource.data.images} onRefresh={resource.reload}
      onUpload={async (type, file) => { const image = await adminContentApi.uploadEpisodeImage(id, type, file); resource.setData({ images: [image] }); }}
      onDelete={async type => { await adminContentApi.deleteEpisodeImage(id, type); resource.setData({ images: [] }); }} />}
  </section>;
}

