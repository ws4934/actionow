import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
  output: "standalone",
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "asset.actionow.ai",
      },
      {
        protocol: "https",
        hostname: "*.actionow.ai",
      },
      {
        protocol: "https",
        hostname: "asset.alienworm.top",
      },
      {
        protocol: "http",
        hostname: "minio",
        port: "9000",
      },
    ],
  },
  // API proxying is handled by /api/[...path]/route.ts instead of rewrites
  // This is more reliable in Cloudflare Workers environment
};

export default withNextIntl(nextConfig);
