"use client";
import { useCallback, useState, type FormEvent } from "react";
import { adminContentApi, type ContentStatus, type ContentType } from "@/features/content/api/adminContentApi";
import { ArtworkPreview } from "@/features/content/components/ArtworkPreview";
import { StatusBadge } from "@/shared/components/StatusBadge";
import { ResourceFeedback, useAdminResource } from "@/shared/components/useAdminResource";

interface Search { query: string; type?: ContentType; status?: ContentStatus; genre: string; country: string }
const emptySearch: Search = { query: "", genre: "", country: "" };

export function CollectionContentPicker({ includedIds, disabled, onAdd }: {
  includedIds: number[]; disabled: boolean; onAdd: (id: number) => void;
}) {
  const [search, setSearch] = useState<Search>(emptySearch);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const type = data.get("type");
    const status = data.get("status");
    setSearch({
      query: String(data.get("query")).trim(),
      type: type === "MOVIE" || type === "SERIES" ? type : undefined,
      status: status === "DRAFT" || status === "PUBLISHED" || status === "ARCHIVED" ? status : undefined,
      genre: String(data.get("genre")).trim(),
      country: String(data.get("country")).trim().toUpperCase(),
    });
    setPage(0); setRevision(value => value + 1);
  }
  return <div className="metadata-entry"><h3>Content Picker</h3>
    <form className="filters content-filters" onSubmit={submit}>
      <label>Title / ID<input name="query" placeholder="제목 또는 ID" /></label>
      <label>Type<select name="type"><option value="">전체</option><option>MOVIE</option><option>SERIES</option></select></label>
      <label>Genre 코드<input name="genre" maxLength={50} /></label>
      <label>Status<select name="status"><option value="">전체</option><option>DRAFT</option><option>PUBLISHED</option><option>ARCHIVED</option></select></label>
      <label>Country<input name="country" pattern="[A-Za-z]{2}" maxLength={2} placeholder="KR" /></label>
      <button className="button primary">검색</button>
      <button className="button" type="reset" onClick={() => { setSearch(emptySearch); setPage(0); setRevision(value => value + 1); }}>초기화</button>
    </form>
    <p className="muted">숫자는 ID 일치 검색, 그 외에는 제목 부분 검색입니다. Country는 Availability 국가 등록 여부를 조회합니다.</p>
    <p className="muted">검색 응답에는 Title·Genre가 없어 후보에서 미제공으로 표시합니다.</p>
    <PickerResults key={JSON.stringify([search, page, revision])} search={search} page={page} onPage={setPage} includedIds={includedIds} disabled={disabled} onAdd={onAdd} />
  </div>;
}

function PickerResults({ search, page, onPage, includedIds, disabled, onAdd }: {
  search: Search; page: number; onPage: (page: number) => void; includedIds: number[]; disabled: boolean; onAdd: (id: number) => void;
}) {
  const { query, type, status, genre, country } = search;
  const resource = useAdminResource(useCallback((signal: AbortSignal) => adminContentApi.list({ query, type, status, genre, country, page, size: 10 }, signal), [query, type, status, genre, country, page]));
  const included = new Set(includedIds);
  return <><ResourceFeedback resource={resource} label="Content 검색" />
    {!resource.loading && !resource.error && resource.data && <>
      {resource.data.content.length === 0 ? <p className="muted empty">검색 결과가 없습니다.</p> :
        <div className="content-grid">{resource.data.content.map(content => <div className="content-card" key={content.id}>
          <ArtworkPreview key={content.landscapeImageUrl} url={content.landscapeImageUrl ?? undefined} label="LANDSCAPE" />
          <strong>Content #{content.id}</strong><small>Title: 미제공 · Genre: 미제공</small><span>{content.type}</span><StatusBadge value={content.status} />
          <button className="button small" disabled={disabled || included.has(content.id)} onClick={() => { if (!included.has(content.id)) onAdd(content.id); }}>{included.has(content.id) ? "이미 편성됨" : "추가"}</button>
        </div>)}</div>}
      <div className="pagination"><span>{resource.data.totalElements}개 · {resource.data.page + 1} / {Math.max(1, resource.data.totalPages)} 페이지</span><div>
        <button className="button small" disabled={resource.data.first} onClick={() => onPage(0)}>처음</button>
        <button className="button small" disabled={resource.data.first} onClick={() => onPage(Math.max(0, page - 1))}>이전</button>
        <button className="button small" disabled={resource.data.last} onClick={() => onPage(page + 1)}>다음</button>
      </div></div>
    </>}
  </>;
}
