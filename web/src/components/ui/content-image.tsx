"use client";

import NextImage, { type ImageProps } from "next/image";
import { passthroughLoader } from "@/lib/image-loader";

/**
 * Drop-in replacement for next/image for user-uploaded content.
 * Always bypasses Next.js image optimization because content URLs
 * can come from arbitrary external hosts (AI providers, CDNs, etc.)
 * that aren't configured in next.config remotePatterns.
 */
export default function ContentImage(props: ImageProps) {
  return <NextImage {...props} loader={passthroughLoader} unoptimized />;
}
