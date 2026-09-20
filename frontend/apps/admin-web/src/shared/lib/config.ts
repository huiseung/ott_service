export const config = {
  adminApiBaseUrl: process.env.NEXT_PUBLIC_ADMIN_API_BASE_URL ?? "http://localhost:8080",
  maxActiveFiles: 3,
  maxInflightParts: 6,
  maxPartsPerFile: 2,
  ackBatchSize: 5,
  ackDelayMs: 2000,
  maxPartRetries: 4,
};
