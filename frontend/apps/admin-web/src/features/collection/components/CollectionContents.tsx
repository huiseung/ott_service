"use client";
import Link from "next/link";
import { useState } from "react";
import { adminCollectionApi, type CollectionDetail } from "../api/adminCollectionApi";
import { ArtworkPreview } from "@/features/content/components/ArtworkPreview";
import { StatusBadge } from "@/shared/components/StatusBadge";
import type { CollectionMutation } from "./useCollectionMutation";
import { CollectionContentPicker } from "./CollectionContentPicker";

export function CollectionContents({ collection, disabled, mutation }: {
  collection: CollectionDetail; disabled: boolean; mutation: CollectionMutation;
}) {
  const items = collection.items;
  const signature = JSON.stringify([mutation.itemsRevision, items.map(item => [item.contentId, item.displayOrder])]);
  const [draft, setDraft] = useState<{ signature: string; ids: number[] } | null>(null);
  const draftIds = draft?.signature === signature ? draft.ids : null;
  const byId = new Map(items.map(item => [item.contentId, item]));
  const ordered = draftIds ? draftIds.flatMap(id => byId.get(id) ?? []) : items;
  const dirty = ordered.some((item, index) => item.contentId !== items[index]?.contentId);
  function move(index: number, offset: number) {
    const ids = ordered.map(item => item.contentId);
    const target = index + offset;
    if (target < 0 || target >= ids.length) return;
    [ids[index], ids[target]] = [ids[target], ids[index]];
    setDraft({ signature, ids });
  }
  return <section className="panel" id="collection-contents"><h2>Contents · {items.length}개</h2>
    <p className="muted">위·아래 버튼으로 순서를 편집한 뒤 ‘순서 저장’을 누르세요. 추가·제거 전에 순서 변경을 저장하거나 취소하세요.</p>
    {dirty && <p role="status" className="mutation-success">저장하지 않은 순서 변경이 있습니다.</p>}
    {items.length === 0 ? <p className="muted empty">편성된 Content가 없습니다.</p> :
      <div className="table-wrap"><table><caption className="sr-only">Collection 편성 순서</caption>
        <thead><tr>{["LANDSCAPE", "Title / Content ID", "Type", "Status", "displayOrder", "순서", "작업"].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
        <tbody>{ordered.map((item, index) => <tr key={item.contentId}>
          <td><div className="episode-thumbnail"><ArtworkPreview key={item.landscapeImageUrl} url={item.landscapeImageUrl ?? undefined} label="LANDSCAPE" /></div></td>
          <td><Link className="content-id-link" href={`/admin/contents/${item.contentId}`}>{item.title ?? "제목 미등록"}</Link><small>Content #{item.contentId}</small></td>
          <td>{item.type}</td><td><StatusBadge value={item.status} /></td><td>{dirty ? index + 1 : item.displayOrder}{dirty && <small>저장 전</small>}</td>
          <td><div className="actions"><button className="button small" disabled={disabled || index === 0} aria-label={`Content ${item.contentId} 위로 이동`} onClick={() => move(index, -1)}>위</button><button className="button small" disabled={disabled || index === ordered.length - 1} aria-label={`Content ${item.contentId} 아래로 이동`} onClick={() => move(index, 1)}>아래</button></div></td>
          <td><button className="button small danger" disabled={disabled || dirty} onClick={() => {
            if (!window.confirm(`Content #${item.contentId}를 편성에서 제거하시겠습니까?`)) return;
            void mutation.run("Content 제거", "편성 내용을 확인하세요.", () => adminCollectionApi.removeItem(collection.id, item.contentId), true);
          }}>제거</button></td>
        </tr>)}</tbody>
      </table></div>}
    <div className="actions"><button className="button primary" disabled={disabled || !dirty} onClick={() => void mutation.run("순서 저장", "모든 편성 Content가 한 번씩 포함되어야 합니다.", () => adminCollectionApi.reorder(collection.id, ordered.map(item => item.contentId)), true)}>순서 저장</button>
      <button className="button" disabled={disabled || !dirty} onClick={() => setDraft(null)}>순서 변경 취소</button>
      <button className="button" disabled={mutation.pending} onClick={() => { if (!dirty || window.confirm("저장하지 않은 순서를 취소하고 서버 상태를 다시 불러오시겠습니까?")) void mutation.resync(); }}>서버 상태 새로고침</button>
    </div>
    <CollectionContentPicker includedIds={items.map(item => item.contentId)} disabled={disabled || dirty} onAdd={id => {
      if (items.some(item => item.contentId === id)) return;
      void mutation.run("Content 추가", "Content가 이미 편성되어 있는지 확인하세요.", () => adminCollectionApi.addItems(collection.id, [id]), true);
    }} />
  </section>;
}
