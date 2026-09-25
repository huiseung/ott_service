"use client";
import { useRef, useState } from "react";
import { contentMutationError } from "../api/contentMutationError";

export function useContentMutation() {
  const lock = useRef(false);
  const [state, setState] = useState({ pending: false, scope: "", error: "", success: "" });
  async function run(scope: string, hint: string, action: () => Promise<void>) {
    if (lock.current) return false;
    lock.current = true;
    setState({ pending: true, scope, error: "", success: "" });
    try {
      await action();
      setState({ pending: false, scope, error: "", success: `${scope} 완료되었습니다.` });
      return true;
    } catch (error) {
      setState({ pending: false, scope, error: contentMutationError(error, hint), success: "" });
      return false;
    } finally { lock.current = false; }
  }
  return { ...state, run };
}

export type ContentMutation = ReturnType<typeof useContentMutation>;

export function MutationFeedback({ mutation }: { mutation: ContentMutation }) {
  return <div aria-live="polite">
    {mutation.pending && <p role="status">{mutation.scope} 처리 중입니다…</p>}
    {mutation.error && <p className="error" role="alert">{mutation.scope}: {mutation.error}</p>}
    {mutation.success && <p className="mutation-success" role="status">{mutation.success}</p>}
  </div>;
}
