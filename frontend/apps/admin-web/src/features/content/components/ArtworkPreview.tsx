"use client";
import { useState } from "react";

export function ArtworkPreview({ url, label, poster = false }: { url?: string; label: string; poster?: boolean }) {
  const [failed, setFailed] = useState(false);
  return <div className={`artwork-preview artwork-fit ${poster ? "poster" : ""}`}>
    {url && !failed ?
      // The backend provides signed image URLs; preserve those URLs without an image proxy.
      // eslint-disable-next-line @next/next/no-img-element
      <img src={url} alt={`${label} Preview`} loading="lazy" onError={() => setFailed(true)} /> :
      <span>{failed ? "이미지 로드 실패 · 새로고침하세요" : `${label} 없음`}</span>}
  </div>;
}
