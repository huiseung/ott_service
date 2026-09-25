"use client";
import Link from "next/link";
import { useEffect, useRef } from "react";
import type { ContentItem } from "@/features/api";
import { trackEvent } from "./analytics";
import { SubscriptionButton } from "@/features/subscription/SubscriptionButton";

export function ContentCard({ content, loggedIn }: { content: ContentItem; loggedIn: boolean }) {
  const link = useRef<HTMLAnchorElement>(null);
  const seen = useRef(false);
  useEffect(() => {
    if (!link.current || seen.current || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(entries => {
      if (seen.current || !entries.some(entry => entry.isIntersecting && entry.intersectionRatio >= 0.5)) return;
      seen.current = true;
      trackEvent({ eventType: "CONTENT_IMPRESSION", contentId: content.contentId, videoId: content.videoId,
        payload: { surface: "home" } });
      observer.disconnect();
    }, { threshold: 0.5 });
    observer.observe(link.current);
    return () => observer.disconnect();
  }, [content.contentId, content.videoId]);
  const watch = `/watch/${content.videoId}`;
  return <div><Link ref={link} className="video-item" href={loggedIn ? watch : `/login?next=${encodeURIComponent(watch)}`}
    onClick={() => trackEvent({ eventType: "CONTENT_CLICK", contentId: content.contentId, videoId: content.videoId,
      payload: { surface: "home" } })}>
    <span className="video-icon">▶</span><strong>{content.title}</strong>
    <span className="muted">{content.type === "SERIES" ? "시리즈 · 첫 공개 에피소드 시청" : "영화 · 시청하기"} →</span>
  </Link>{loggedIn && <SubscriptionButton contentId={content.contentId} />}</div>;
}
