"use client";
import { useEffect, useState } from "react";
import { mediaProcessingApi, type ProcessingJobSummary } from "@/features/media-processing/api/mediaProcessingApi";
import { userError } from "@/shared/lib/apiClient";

const terminal = new Set(["COMPLETED", "FAILED", "SUPERSEDED"]);

// One paginated Job query stream serves every upload card on this page.
export function useUploadProcessing(videoIds: number[]) {
  const idsKey = [...new Set(videoIds)].sort((a, b) => a - b).join(",");
  const [jobs, setJobs] = useState<Record<number, ProcessingJobSummary>>({});
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  useEffect(() => {
    if (!idsKey) return;
    const wanted = new Set(idsKey.split(",").map(Number));
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let busy = false;
    const load = async (): Promise<boolean> => {
      if (cancelled || busy || document.hidden) return true;
      busy = true;
      try {
        const found: Record<number, ProcessingJobSummary> = {};
        // The API sorts newest first. Stop after three batch pages; older jobs remain available in detail.
        for (let page = 0; page < 3; page++) {
          const response = await mediaProcessingApi.listJobs({ page, size: 100 });
          for (const job of response.content) {
            if (wanted.has(job.videoId) && !found[job.videoId]) found[job.videoId] = job;
          }
          if (response.last || Object.keys(found).length === wanted.size) break;
        }
        if (!cancelled) { setJobs(previous => ({ ...previous, ...found })); setError(""); }
        return [...wanted].some(id => !found[id] || !terminal.has(found[id].status));
      } catch (reason) { if (!cancelled) setError(userError(reason)); return true; }
      finally { busy = false; }
    };
    const schedule = () => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(async () => { const needsMore = await load(); if (!cancelled && needsMore) schedule(); }, 3000);
    };
    void load().then(needsMore => { if (needsMore && !cancelled) schedule(); });
    const visible = () => { if (!document.hidden) void load().then(needsMore => { if (needsMore && !cancelled) schedule(); }); };
    document.addEventListener("visibilitychange", visible);
    return () => { cancelled = true; if (timer) clearTimeout(timer); document.removeEventListener("visibilitychange", visible); };
  }, [idsKey, refreshKey]);
  return { jobs, error, refresh: () => setRefreshKey(value => value + 1) };
}
