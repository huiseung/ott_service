"use client";
import { useRef, useState } from "react";
import { userApi } from "@/features/api";
import { trackEvent } from "@/features/analytics/analytics";
import { ApiError, errorMessage } from "@/shared/apiClient";

export function SubscriptionButton({ contentId }: { contentId: number }) {
  const attempt = useRef<{ requestId: string; ctaEventId: string | null } | null>(null);
  const busy = useRef(false);
  const [pending, setPending] = useState(false);
  const [done, setDone] = useState(false);
  const [message, setMessage] = useState("");
  async function subscribe() {
    if (busy.current || done) return;
    busy.current = true; setPending(true); setMessage("");
    try {
      attempt.current ??= { requestId: crypto.randomUUID(), ctaEventId: trackEvent({
        eventType: "SUBSCRIPTION_CTA_CLICK", contentId, payload: { surface: "home", activationSource: "LOCAL_TEST" },
      }) ?? null };
      const checkout = await userApi.createCheckout({ ...attempt.current, contentId });
      const membership = await userApi.activateLocalSubscription(checkout.checkoutId);
      if (membership.status !== "ACTIVE") throw new Error("현재 구독 상태를 확인해 주세요.");
      setDone(true); setMessage("테스트 구독이 완료되었습니다. 실제 결제는 발생하지 않았습니다.");
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        const membership = await userApi.subscription().catch(() => null);
        if (membership?.status === "ACTIVE") { setDone(true); setMessage("이미 구독 중입니다."); }
        else { attempt.current = null; setMessage("신청이 만료되었거나 상태가 변경되었습니다. 다시 시도해 주세요."); }
      } else setMessage(error instanceof ApiError && error.status === 404
        ? "테스트 구독을 사용할 수 없습니다." : errorMessage(error));
    } finally { busy.current = false; setPending(false); }
  }
  return <div>
    <button type="button" className="button small" disabled={pending || done} onClick={() => void subscribe()}>
      {pending ? "처리 중…" : done ? "구독 완료" : "테스트 구독하기 (결제 없음)"}
    </button>
    {message && <p role="status" className="muted">{message}</p>}
  </div>;
}
