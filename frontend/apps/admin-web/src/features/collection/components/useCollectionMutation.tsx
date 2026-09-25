"use client";
import { useRef, useState } from "react";
import { adminCollectionApi, type CollectionDetail } from "../api/adminCollectionApi";
import { collectionError } from "../api/collectionError";
import { executeCollectionMutation } from "../api/collectionMutation";

export function useCollectionMutation(id: number, onChange: (data: CollectionDetail) => void) {
  const lock = useRef(false);
  const [pending, setPending] = useState(false);
  const [synchronized, setSynchronized] = useState(true);
  const [feedback, setFeedback] = useState({ scope: "", error: "", success: "" });
  const [itemsRevision, setItemsRevision] = useState(0);

  async function run(scope: string, hint: string, action: () => Promise<CollectionDetail>, itemChange = false) {
    if (lock.current || !synchronized) return false;
    lock.current = true;
    setPending(true);
    setFeedback({ scope, error: "", success: "" });
    try {
      const result = await executeCollectionMutation(action, () => adminCollectionApi.get(id));
      if (result.status === "saved") {
        onChange(result.collection);
        if (itemChange) setItemsRevision(value => value + 1);
        setFeedback({ scope, error: "", success: `${scope} 완료되었습니다.` });
        return true;
      }
      let recovery: string;
      if (result.status === "recovered") {
        onChange(result.collection);
        setItemsRevision(value => value + 1);
        recovery = " 서버의 최신 상태를 다시 불러왔습니다.";
      } else {
        setSynchronized(false);
        recovery = " 서버 상태를 확인하지 못했습니다. 다시 동기화한 후 편집하세요.";
      }
      setFeedback({ scope, error: collectionError(result.error, hint) + recovery, success: "" });
      return false;
    } finally { lock.current = false; setPending(false); }
  }

  async function resync() {
    if (lock.current) return;
    lock.current = true; setPending(true);
    try {
      onChange(await adminCollectionApi.get(id));
      setItemsRevision(value => value + 1); setSynchronized(true);
      setFeedback({ scope: "동기화", error: "", success: "서버의 최신 상태를 불러왔습니다." });
    } catch (error) {
      setSynchronized(false);
      setFeedback({ scope: "동기화", error: collectionError(error, "잠시 후 다시 시도하세요."), success: "" });
    } finally { lock.current = false; setPending(false); }
  }
  return { pending, synchronized, itemsRevision, ...feedback, run, resync };
}
export type CollectionMutation = ReturnType<typeof useCollectionMutation>;

export function CollectionMutationFeedback({ mutation }: { mutation: CollectionMutation }) {
  return <div aria-live="polite">
    {mutation.pending && <p role="status">{mutation.scope || "요청"} 처리 중…</p>}
    {mutation.error && <p className="error" role="alert">{mutation.scope}: {mutation.error}</p>}
    {mutation.success && <p className="mutation-success" role="status">{mutation.success}</p>}
    {!mutation.synchronized && <button className="button" disabled={mutation.pending} onClick={() => void mutation.resync()}>서버 상태 다시 동기화</button>}
  </div>;
}
