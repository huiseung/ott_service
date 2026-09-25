"use client";
import { useEffect, useState } from "react";
import { ApiError, userError } from "@/shared/lib/apiClient";

// Call with a stable loader; resource owners are keyed by their resource ID.
export function useAdminResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then(value => {
      if (!controller.signal.aborted) { setData(value); setError(""); setLoading(false); }
    }).catch(reason => {
      if (!controller.signal.aborted) {
        setError(reason instanceof ApiError && reason.status === 404 ? "요청한 대상을 찾을 수 없습니다." : userError(reason));
        setLoading(false);
      }
    });
    return () => controller.abort();
  }, [load, revision]);
  function reload() { setLoading(true); setError(""); setRevision(value => value + 1); }
  return { data, setData, loading, error, reload };
}

export function ResourceFeedback({ resource, label }: { resource: { loading: boolean; error: string; reload: () => void }; label: string }) {
  if (resource.loading) return <p className="muted" role="status">{label} 불러오는 중…</p>;
  if (resource.error) return <div role="alert"><p className="error">{label}: {resource.error}</p><button className="button small" onClick={resource.reload}>다시 시도</button></div>;
  return null;
}

