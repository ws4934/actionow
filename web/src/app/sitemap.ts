import type { MetadataRoute } from "next";

const SITE_URL = "https://actionow.ai";

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();

  return [
    // Landing pages (highest priority)
    {
      url: `${SITE_URL}/en`,
      lastModified: now,
      changeFrequency: "weekly",
      priority: 1.0,
      alternates: {
        languages: { en: `${SITE_URL}/en`, "zh-CN": `${SITE_URL}/zh` },
      },
    },
    {
      url: `${SITE_URL}/zh`,
      lastModified: now,
      changeFrequency: "weekly",
      priority: 1.0,
      alternates: {
        languages: { en: `${SITE_URL}/en`, "zh-CN": `${SITE_URL}/zh` },
      },
    },
    // Auth pages
    {
      url: `${SITE_URL}/en/login`,
      lastModified: now,
      changeFrequency: "monthly",
      priority: 0.5,
      alternates: {
        languages: { en: `${SITE_URL}/en/login`, "zh-CN": `${SITE_URL}/zh/login` },
      },
    },
    {
      url: `${SITE_URL}/zh/login`,
      lastModified: now,
      changeFrequency: "monthly",
      priority: 0.5,
      alternates: {
        languages: { en: `${SITE_URL}/en/login`, "zh-CN": `${SITE_URL}/zh/login` },
      },
    },
    {
      url: `${SITE_URL}/en/register`,
      lastModified: now,
      changeFrequency: "monthly",
      priority: 0.5,
      alternates: {
        languages: { en: `${SITE_URL}/en/register`, "zh-CN": `${SITE_URL}/zh/register` },
      },
    },
    {
      url: `${SITE_URL}/zh/register`,
      lastModified: now,
      changeFrequency: "monthly",
      priority: 0.5,
      alternates: {
        languages: { en: `${SITE_URL}/en/register`, "zh-CN": `${SITE_URL}/zh/register` },
      },
    },
  ];
}
