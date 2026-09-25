export function statusTone(value: string): "success" | "danger" | "progress" | "neutral" {
  if (value === "PUBLISHED" || value === "AVAILABLE") return "success";
  // Preserve the existing Video/Upload/Encoding tone rules.
  return /READY|COMPLETED/.test(value) ? "success"
    : /FAILED|ABORTED|EXPIRED/.test(value) ? "danger"
    : /PROCESSING|UPLOADING|TRANSCODING|PROBING/.test(value) ? "progress"
    : "neutral";
}
