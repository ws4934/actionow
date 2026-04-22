/**
 * Get a proxied URL for images that may have CORS restrictions
 * @param url - The original image URL
 * @returns The proxied URL if needed, or the original URL for same-origin
 */
export function getProxiedImageUrl(url: string): string {
  if (!url) return url;

  try {
    const parsedUrl = new URL(url, window.location.origin);

    // If same origin, no need to proxy
    if (parsedUrl.origin === window.location.origin) {
      return url;
    }

    // If it's a data URL or blob URL, return as-is
    if (url.startsWith("data:") || url.startsWith("blob:")) {
      return url;
    }

    // Proxy external URLs
    return `/api/proxy?url=${encodeURIComponent(url)}`;
  } catch {
    // If URL parsing fails, return original
    return url;
  }
}

