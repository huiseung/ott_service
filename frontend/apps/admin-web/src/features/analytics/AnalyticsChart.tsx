import { metric } from "./presentation";

export function AnalyticsChart({ title, points, percent = false }: {
  title: string; points: { label: string; value: number }[]; percent?: boolean;
}) {
  if (!points.length) return <p className="muted">표시할 데이터가 없습니다.</p>;
  const max = percent ? 100 : Math.max(1, ...points.map(point => point.value));
  const x = (index: number) => 50 + index * 590 / Math.max(1, points.length - 1);
  const y = (value: number) => 185 - value / max * 145;
  return <svg viewBox="0 0 680 225" role="img" aria-label={`${title}. 상세 수치는 아래 데이터 표에서 확인할 수 있습니다.`} className="analytics-chart">
    {[0, 0.5, 1].map(step => <g key={step}>
      <line x1="50" x2="640" y1={y(step * max)} y2={y(step * max)} stroke="var(--border)" />
      <text x="42" y={y(step * max) + 4} textAnchor="end">{metric(step * max, 1)}{percent ? "%" : ""}</text>
    </g>)}
    <polyline points={points.map((point, index) => `${x(index)},${y(point.value)}`).join(" ")} fill="none" stroke="var(--mint)" strokeWidth="2.5" />
    {points.map((point, index) => <circle key={point.label} cx={x(index)} cy={y(point.value)} r="4" fill="var(--mint)">
      <title>{point.label}: {metric(point.value, 1)}{percent ? "%" : ""}</title>
    </circle>)}
    <text x="50" y="214">{points[0].label}</text><text x="640" y="214" textAnchor="end">{points.at(-1)?.label}</text>
  </svg>;
}
