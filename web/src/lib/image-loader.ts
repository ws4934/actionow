/**
 * Custom loader for next/image that bypasses server-side optimization
 * for internal URLs (e.g. Docker minio) that the Next.js server can't reach.
 * Used via the `loader` prop on individual Image components.
 */
export function passthroughLoader({ src }: { src: string }): string {
  return src;
}

/** Check if a URL points to an internal service unreachable by the Next.js server */
export function isInternalUrl(src: string): boolean {
  return src.startsWith("http://minio") || src.startsWith("http://localhost:");
}
