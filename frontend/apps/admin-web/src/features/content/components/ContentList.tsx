"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { adminContentApi, type ContentListItem, type ContentStatus, type ContentType } from "@/features/content/api/adminContentApi";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { userError } from "@/shared/lib/apiClient";
import type { PageResponse } from "@/shared/lib/page";

const types: ContentType[] = ["MOVIE", "SERIES"];
const statuses: ContentStatus[] = ["DRAFT", "PUBLISHED", "ARCHIVED"];

export function ContentList() {
  const searchParams = useSearchParams();
  // Remount request state and form defaults together on navigation, including back/forward.
  return <ContentListView key={searchParams.toString()} params={new URLSearchParams(searchParams.toString())} />;
}

function ContentListView({ params }: { params: URLSearchParams }) {
  const router = useRouter();
  const query = params.get("query") ?? "";
  const type = types.find(value => value === params.get("type"));
  const status = statuses.find(value => value === params.get("status"));
  const genre = params.get("genre") ?? "";
  const country = params.get("country") ?? "";
  const rawPage = Number(params.get("page") ?? 0);
  const page = Number.isInteger(rawPage) && rawPage >= 0 && rawPage <= 2147483647 ? rawPage : 0;
  const rawSize = Number(params.get("size") ?? 20);
  const size = [20, 50, 100].includes(rawSize) ? rawSize : 20;
  const [result, setResult] = useState<PageResponse<ContentListItem> | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    adminContentApi.list({ query, type, status, genre, country, page, size }, controller.signal)
      .then(data => {
        if (!controller.signal.aborted) { setResult(data); setError(""); setLoading(false); }
      })
      .catch(reason => {
        if (!controller.signal.aborted) { setError(userError(reason)); setLoading(false); }
      });
    return () => controller.abort();
  }, [query, type, status, genre, country, page, size, revision]);

  function retry() {
    setLoading(true);
    setError("");
    setRevision(value => value + 1);
  }

  function navigate(next: URLSearchParams) {
    router.push(`/admin/contents${next.size ? `?${next}` : ""}`, { scroll: false });
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const name of ["query", "type", "genre", "status", "country", "size"]) {
      const value = String(data.get(name) ?? "").trim();
      if (value) next.set(name, name === "country" ? value.toUpperCase() : value);
    }
    navigate(next);
  }

  function goToPage(value: number) {
    const next = new URLSearchParams(params);
    next.set("page", String(value));
    navigate(next);
  }

  return <section className="panel">
    <div className="section-head"><h2>Content 목록</h2><button className="button small" onClick={retry} disabled={loading}>새로고침</button></div>
    <form className="filters content-filters" key={params.toString()} onSubmit={submit}>
      <label>Content ID / Title<input name="query" defaultValue={query} placeholder="ID 또는 제목 검색" aria-describedby="content-search-help" /></label>
      <label>Type<select name="type" defaultValue={type ?? ""}><option value="">전체</option>{types.map(value => <option key={value}>{value}</option>)}</select></label>
      <label>Genre 코드<input name="genre" defaultValue={genre} placeholder="장르 코드 입력" /></label>
      <label>Status<select name="status" defaultValue={status ?? ""}><option value="">전체</option>{statuses.map(value => <option key={value}>{value}</option>)}</select></label>
      <label>Country<input name="country" defaultValue={country} placeholder="예: KR" pattern="[A-Za-z]{2}" maxLength={2} title="두 자리 국가 코드를 입력하세요." aria-describedby="content-country-help" /></label>
      <label>페이지 크기<select name="size" defaultValue={size}>{[20, 50, 100].map(value => <option key={value} value={value}>{value}개</option>)}</select></label>
      <button className="button primary" type="submit">검색</button>
      <button className="button" type="reset" onClick={() => navigate(new URLSearchParams())}>초기화</button>
    </form>
    <p className="muted" id="content-search-help">숫자만 입력하면 Content ID 일치 검색, 그 외에는 제목 부분 검색을 수행합니다.</p>
    <p className="muted" id="content-country-help">Country는 Availability에 등록된 국가를 검색합니다. 현재 서비스 가능 여부를 의미하지 않습니다.</p>
    <p className="muted" id="content-metadata-help">Title과 Genre는 현재 목록 API에서 제공되지 않습니다.</p>
    {loading ? <p className="muted empty" role="status">Content 목록을 불러오는 중입니다…</p> :
      error ? <div className="error" role="alert">{error} <button className="button small" onClick={retry}>다시 시도</button></div> :
      result && <>
        {result.content.length === 0 ? <p className="muted empty" role="status">{page > 0 ? "이 페이지에 Content가 없습니다. 첫 페이지로 이동하거나 검색 조건을 변경하세요." : "검색 조건에 해당하는 Content가 없습니다."}</p> :
          <div className="table-wrap"><table className="content-table" aria-describedby="content-metadata-help">
            <caption className="sr-only">Content 검색 결과</caption>
            <thead><tr>{["LANDSCAPE", "Content ID", "Title", "Type", "Status", "Genre", "Updated At", "작업"].map(label => <th scope="col" key={label}>{label}</th>)}</tr></thead>
            <tbody>{result.content.map(content => <tr key={content.id}>
              <td><LandscapeArtwork key={content.landscapeImageUrl} url={content.landscapeImageUrl} /></td>
              <td><Link className="content-id-link" href={`/admin/contents/${content.id}`}>#{content.id}</Link></td>
              <td className="muted">미제공</td><td>{content.type}</td><td><StatusBadge value={content.status} /></td><td className="muted">미제공</td>
              <td><time dateTime={content.updatedAt}>{new Date(content.updatedAt).toLocaleString("ko-KR")}</time></td>
              <td><Link className="button small" href={`/admin/contents/${content.id}`} aria-label={`Content #${content.id} 상세`}>상세</Link></td>
            </tr>)}</tbody>
          </table></div>}
        <div className="pagination">
          <span>{result.totalElements}개 · {result.page + 1} / {Math.max(1, result.totalPages)} 페이지</span>
          <div>
            <button className="button small" disabled={result.first} onClick={() => goToPage(0)}>처음</button>
            <button className="button small" disabled={result.first} onClick={() => goToPage(Math.max(0, page - 1))}>이전</button>
            <button className="button small" disabled={result.last} onClick={() => goToPage(page + 1)}>다음</button>
          </div>
        </div>
      </>}
  </section>;
}

function LandscapeArtwork({ url }: { url: string | null }) {
  const [failed, setFailed] = useState(false);
  return <div className="artwork-preview content-landscape">
    {url && !failed ?
      // Backend supplies signed URLs; load directly without the Next image proxy.
      // eslint-disable-next-line @next/next/no-img-element
      <img src={url} alt="" loading="lazy" onError={() => setFailed(true)} /> :
      <span>{failed ? "이미지 로드 실패" : "LANDSCAPE 없음"}</span>}
  </div>;
}
