import { statusTone } from "@/shared/lib/statusPresentation";

export function StatusBadge({ value }: { value: string }) {
  const tone = statusTone(value);
  return <span className={`badge ${tone}`}><span className="badge-dot" />{value.replaceAll("_", " ")}</span>;
}
export function ProgressBar({ value, label }: { value: number; label: string }) { return <div className="progress" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(value)}><span style={{ width: `${Math.min(100, Math.max(0, value))}%` }} /></div>; }
