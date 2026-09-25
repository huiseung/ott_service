import { AnalyticsDashboard } from "@/features/analytics/AnalyticsDashboard";
import "@/features/analytics/analytics.css";

export default async function AnalyticsPage({ searchParams }: { searchParams: Promise<{ contentId?: string }> }) {
  const params = await searchParams;
  const id = Number(params.contentId);
  const today = new Date();
  const from = new Date(today.getTime() - 6 * 86400000);
  return <AnalyticsDashboard initialContentId={Number.isSafeInteger(id) && id > 0 ? id : null}
    initialFrom={from.toISOString().slice(0, 10)} initialTo={today.toISOString().slice(0, 10)} />;
}
