import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Emits a minimal standalone server bundle for the Docker image.
  output: "standalone",
  reactStrictMode: true,
  // The browser never calls the platform directly; all traffic goes through
  // this app's own /api routes (the BFF), so no CORS and no token in JS.
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
        ],
      },
    ];
  },
};

export default nextConfig;
