"use client";
import Link from "next/link";
import { useCallback, useId, useState } from "react";
import { adminCollectionApi, type CollectionDetail } from "../api/adminCollectionApi";
import { collectionPreviewReasonLabel } from "../api/collectionPreviewReason";
import { ArtworkPreview } from "@/features/content/components/ArtworkPreview";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";

export function CollectionTerritoryPreview({ collection, updating, synchronized }: {
  collection: CollectionDetail; updating: boolean; synchronized: boolean;
}) {
  const [country, setCountry] = useState("KR");
  const countryListId = useId();
  const countries = [...new Set(["KR", "JP", "US", ...collection.availabilities.map(item => item.countryCode)])];
  const validCountry = /^[A-Z]{2}$/.test(country);
  return <section className="panel" id="collection-preview">
    <h2>Territory Preview</h2>
    <div className="filters"><label>Country<input aria-describedby="territory-preview-help" list={countryListId} value={country} onChange={event => setCountry(event.target.value.trim().toUpperCase())} maxLength={2} pattern="[A-Z]{2}" placeholder="KR" /></label>
      <datalist id={countryListId}>{countries.map(value => <option key={value} value={value} />)}</datalist>
    </div>
    <p className="muted" id="territory-preview-help">두 자리 국가 코드를 선택하거나 입력하세요. 서버에 저장된 편성과 조회 시점의 정책으로 확인합니다. 아직 저장하지 않은 변경은 반영되지 않습니다.</p>
    <p className="muted">Collection Visible은 Collection 자체의 공개 여부, Content Visible은 개별 Content의 공개 여부입니다. 최종 Collection 노출 여부는 Displayable로 확인하세요.</p>
    {!validCountry ? <p role="status">KR, JP, US와 같은 두 자리 국가 코드를 입력하세요.</p> :
      updating ? <p className="muted" role="status">변경 처리 후 Preview를 다시 조회합니다…</p> :
      !synchronized ? <p className="error" role="status">서버 상태를 동기화한 후 Preview를 확인하세요.</p> :
      <TerritoryResult key={JSON.stringify([country, collection])} collection={collection} country={country} />}
  </section>;
}

function TerritoryResult({ collection, country }: { collection: CollectionDetail; country: string }) {
  const id = collection.id;
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminCollectionApi.preview(id, country, signal), [id, country]));
  const metadata = new Map(collection.items.map(item => [item.contentId, item]));
  const preview = resource.data;
  return <>
    <ResourceFeedback resource={resource} label={`${country} Territory Preview`} />
    {!resource.loading && !resource.error && preview && <>
      <div className="section-head"><p>조회 국가: <strong>{preview.countryCode}</strong></p><button className="button small" onClick={resource.reload}>Preview 새로고침</button></div>
      <dl className="preview-summary">
        <div><dt>Collection Visible</dt><dd><VisibilityBadge visible={preview.collectionVisible} /></dd></div>
        <div><dt>등록 Content 수</dt><dd>{preview.totalItems}</dd></div>
        <div><dt>Eligible Content 수</dt><dd>{preview.visibleItems}</dd></div>
        <div><dt>minVisibleItems</dt><dd>{preview.minVisibleItems}</dd></div>
        <div><dt>Displayable</dt><dd><span className={`badge ${preview.displayable ? "success" : "neutral"}`}>{preview.displayable ? "Yes · 노출 가능" : "No · 노출 불가"}</span></dd></div>
      </dl>
      <p>Collection Reason: {collectionPreviewReasonLabel(preview.collectionReason)}</p>
      {preview.items.length === 0 ? <p className="muted empty" role="status">등록된 Content가 없습니다.</p> :
        <div className="table-wrap"><table><caption className="sr-only">{preview.countryCode} Content별 공개 여부</caption>
          <thead><tr>{["displayOrder", "Artwork", "Title / Content ID", "Visible / Not Visible", "Reason"].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
          <tbody>{preview.items.map(item => {
            const content = metadata.get(item.contentId);
            return <tr key={item.contentId}>
              <td>{item.displayOrder}</td>
              <td><div className="episode-thumbnail"><ArtworkPreview key={content?.landscapeImageUrl} url={content?.landscapeImageUrl ?? undefined} label="LANDSCAPE" /></div></td>
              <td><Link className="content-id-link" href={`/admin/contents/${item.contentId}`}>{content ? content.title ?? "제목 미등록" : "상세 정보 미조회"}</Link><small>Content #{item.contentId}</small></td>
              <td><VisibilityBadge visible={item.visible} /></td>
              <td>{collectionPreviewReasonLabel(item.reason)}{item.reason && <small>{item.reason}</small>}</td>
            </tr>;
          })}</tbody>
        </table></div>}
    </>}
  </>;
}

function VisibilityBadge({ visible }: { visible: boolean }) {
  return <span className={`badge ${visible ? "success" : "neutral"}`}>{visible ? "Visible · 공개" : "Not Visible · 비공개"}</span>;
}
