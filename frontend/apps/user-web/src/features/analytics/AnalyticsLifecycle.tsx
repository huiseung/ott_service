"use client";
import { useEffect } from "react";
import { startAnalytics } from "./analytics";

export function AnalyticsLifecycle() {
  useEffect(startAnalytics, []);
  return null;
}
