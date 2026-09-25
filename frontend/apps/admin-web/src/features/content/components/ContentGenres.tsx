"use client";
import { useState, type FormEvent } from "react";
import { adminContentApi, type ContentDetail } from "../api/adminContentApi";
import { MutationFeedback, type ContentMutation } from "./useContentMutation";

export function ContentGenres({ content, onChange, mutation }: {
  content: ContentDetail; onChange: (content: ContentDetail) => void; mutation: ContentMutation;
}) {
  const [code, setCode] = useState("");
  const hint = "이미 등록된 Genre 코드(최대 50자)를 입력하세요. 존재하지 않는 코드는 연결할 수 없습니다.";
  async function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = code.trim().toUpperCase();
    const ok = await mutation.run("Genres 추가", hint, async () => {
      if (content.genreCodes.includes(normalized)) throw new Error("이미 연결된 Genre입니다.");
      onChange(await adminContentApi.replaceGenres(content.id, [...content.genreCodes, normalized]));
    });
    if (ok) setCode("");
  }
  function remove(code: string) {
    if (!window.confirm(`이 Content에서 Genre ${code} 연결을 제거하시겠습니까?`)) return;
    void mutation.run("Genres 제거", hint, async () => {
      onChange(await adminContentApi.replaceGenres(content.id, content.genreCodes.filter(value => value !== code)));
    });
  }
  return <section className="panel" id="genres">
    <h2>Genres</h2><p className="muted">기존 Genre 코드를 입력해 작품에 연결합니다. 제거하면 이 작품과의 연결만 해제됩니다.</p>
    {content.genreCodes.length ? <ul className="genre-list">{content.genreCodes.map(value => <li key={value}><span>{value}</span><button className="button small danger" disabled={mutation.pending} onClick={() => remove(value)} aria-label={`${value} Genre 연결 제거`}>제거</button></li>)}</ul> : <p className="muted">연결된 Genre가 없습니다.</p>}
    <form onSubmit={add}><fieldset className="metadata-fields" disabled={mutation.pending}>
      <label>Genre 코드<input required pattern=".*\S.*" maxLength={50} value={code} onChange={event => setCode(event.target.value)} /></label>
      <div className="actions"><button className="button primary">Genre 추가</button></div>
    </fieldset></form>
    {mutation.scope.startsWith("Genres") && <MutationFeedback mutation={mutation} />}
  </section>;
}
