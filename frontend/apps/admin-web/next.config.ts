import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    const backend = process.env.ADMIN_API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_ADMIN_API_BASE_URL ?? "http://localhost:8080";
    return [
      { source: "/api/auth/:path*", destination: `${backend}/api/auth/:path*` },
      { source: "/api/user/:path*", destination: `${backend}/api/user/:path*` },
    ];
  },
};

export default nextConfig;
