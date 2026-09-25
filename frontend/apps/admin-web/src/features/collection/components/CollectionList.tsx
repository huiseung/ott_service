"use client";
import Link from "next/link";
import { useCallback, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { adminCollectionApi, type CollectionListItem } from "../api/adminCollectionApi";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";

export function CollectionList() {
  const params = useSearchParams();
  return <CollectionListView key={params.toString()} params={new URLSearchParams(params.toString())} />;
}
function CollectionListView({ params }: { params: URLSearchParams }) {
  const router = useRouter();
  const query = params.get("query") ?? "";
  const country = params.get("country") ?? "";
  const rawStatus = params.get("status");
  const status = rawStatus === "DRAFT" || rawStatus === "PUBLISHED" || rawStatus === "ARCHIVED" ? rawStatus : undefined;
  const rawPage = Number(params.get("page") ?? 0);
  const page = Number.isInteger(rawPage) && rawPage >= 0 && rawPage <= 2147483647 ? rawPage : 0;
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminCollectionApi.list({ query, country, status, page, size: 10 }, signal), [query, country, status, page]));
  function navigate(next: URLSearchParams) { router.push(`/admin/collections${next.size ? `?${next}` : ""}`, { scroll: false }); }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const name of ["query", "status", "country"]) {
      const value = String(data.get(name) ?? "").trim();
      if (value) next.set(name, name === "country" ? value.toUpperCase() : value);
    }
    navigate(next);
  }
  function goTo(value: number) { const next = new URLSearchParams(params); next.set("page", String(value)); navigate(next); }
  return <section className="panel">
    <div className="section-head"><h2>Collection 목록</h2><button className="button small" disabled={resource.loading} onClick={resource.reload}>새로고침</button></div>
    <form className="filters content-filters" onSubmit={submit}>
      <label>Title 검색<input name="query" defaultValue={query} placeholder="Collection 제목" /></label>
      <label>Status<select name="status" defaultValue={status ?? ""}><option value="">전체</option><option>DRAFT</option><option>PUBLISHED</option><option>ARCHIVED</option></select></label>
      <label>Country<input name="country" pattern="[A-Za-z]{2}" maxLength={2} placeholder="KR" defaultValue={country} /></label>
      <button className="button primary">검색</button><button type="reset" className="button" onClick={() => navigate(new URLSearchParams())}>초기화</button>
    </form>
    <p className="muted">Country는 Availability 국가 등록 여부를 검색합니다.</p>
    <ResourceFeedback resource={resource} label="Collection 목록" />
    {!resource.loading && !resource.error && resource.data && <>
      {resource.data.content.length === 0 ? <p className="muted empty">이 페이지에 검색 조건과 일치하는 Collection이 없습니다.</p> :
        <div className="table-wrap"><table><caption className="sr-only">Collection 검색 결과</caption>
          <thead><tr>{["ID", "Title", "Status", "Content Count", "minVisibleItems", "Updated At"].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
          <tbody>{resource.data.content.map(item => <CollectionRow key={item.id} item={item} />)}</tbody>
        </table></div>}
      <div className="pagination"><span>{resource.data.totalElements}개 · {resource.data.page + 1} / {Math.max(1, resource.data.totalPages)} 페이지</span><div>
        <button className="button small" disabled={resource.data.first} onClick={() => goTo(0)}>처음</button>
        <button className="button small" disabled={resource.data.first} onClick={() => goTo(Math.max(0, page - 1))}>이전</button>
        <button className="button small" disabled={resource.data.last} onClick={() => goTo(page + 1)}>다음</button>
      </div></div>
    </>}
  </section>;
}
function CollectionRow({ item }: { item: CollectionListItem }) {
  // The list DTO has no title/count; fetch only the ten visible rows' details.
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminCollectionApi.get(item.id, signal), [item.id]));
  const current = resource.data ?? item;
  return <tr>
    <td><Link className="content-id-link" href={`/admin/collections/${item.id}`}>#{item.id}</Link></td>
    <td>{resource.loading ? <span role="status">제목 조회 중…</span> : resource.error ? <ResourceFeedback resource={resource} label="제목·개수" /> :
      resource.data?.localizations.length ? resource.data.localizations.map(locale => <div key={locale.id}><Link href={`/admin/collections/${item.id}`}>{locale.title}</Link><small>{locale.locale}</small></div>) : "제목 미등록"}</td>
    <td><StatusBadge value={current.status} /></td>
    <td>{resource.loading ? "조회 중…" : resource.error ? "조회 실패" : resource.data?.items.length}</td>
    <td>{current.minVisibleItems}</td><td><time dateTime={current.updatedAt}>{new Date(current.updatedAt).toLocaleString("ko-KR")}</time></td>
  </tr>;
}
