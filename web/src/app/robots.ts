import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: ["/api/", "/workspace/", "/admin/"],
      },
    ],
    sitemap: "https://actionow.ai/sitemap.xml",
  };
}
