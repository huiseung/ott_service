export function metric(value: number | null, digits = 0): string {
  return value === null || !Number.isFinite(value) ? "—" : value.toLocaleString("ko-KR", { maximumFractionDigits: digits });
}
export function percent(value: number | null): string { return value === null || !Number.isFinite(value) ? "—" : `${metric(value * 100, 1)}%`; }
export function validRange(from: string, to: string): boolean {
  const start = Date.parse(`${from}T00:00:00Z`), end = Date.parse(`${to}T00:00:00Z`);
  return /^\d{4}-\d{2}-\d{2}$/.test(from) && /^\d{4}-\d{2}-\d{2}$/.test(to)
    && Number.isFinite(start) && Number.isFinite(end) && start >= 0 && end >= start
    && new Date(start).toISOString().slice(0, 10) === from && new Date(end).toISOString().slice(0, 10) === to
    && (end - start) / 86400000 < 31 && to <= "2149-06-06";
}
export function daysBetween(from: string, to: string): string[] {
  if (!validRange(from, to)) return [];
  const days: string[] = [];
  for (let time = Date.parse(`${from}T00:00:00Z`); time <= Date.parse(`${to}T00:00:00Z`); time += 86400000)
    days.push(new Date(time).toISOString().slice(0, 10));
  return days;
}
