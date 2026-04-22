"use client";

import { useState, useEffect } from "react";
import { Github } from "lucide-react";
import { oauthService } from "@/lib/api";
import type { LoginConfigDTO } from "@/lib/api/dto";

// Module-level cache to avoid duplicate fetches across components
let cachedPromise: Promise<LoginConfigDTO> | null = null;
let cachedResult: LoginConfigDTO | null = null;

function fetchLoginConfig(): Promise<LoginConfigDTO> {
  if (!cachedPromise) {
    cachedPromise = oauthService.getLoginConfig().then((data) => {
      cachedResult = data;
      return data;
    }).catch((err) => {
      cachedPromise = null; // Allow retry on failure
      throw err;
    });
  }
  return cachedPromise;
}

export function useLoginConfig(): LoginConfigDTO | null {
  const [config, setConfig] = useState<LoginConfigDTO | null>(cachedResult);

  useEffect(() => {
    if (cachedResult) {
      setConfig(cachedResult);
      return;
    }
    fetchLoginConfig().then(setConfig).catch(() => {});
  }, []);

  return config;
}

/**
 * Maps provider icon key to a React node.
 * Known icons: github, google, apple, wechat, linux_do
 * Unknown: first letter of displayName as fallback
 */
export function getOAuthIcon(icon: string, displayName?: string) {
  switch (icon) {
    case "github":
      return <Github className="size-5" />;
    case "google":
      return (
        <svg className="size-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z" />
        </svg>
      );
    case "apple":
      return (
        <svg className="size-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z" />
        </svg>
      );
    case "wechat":
      return (
        <svg className="size-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 0 1 .213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.326.326 0 0 0 .167-.054l1.903-1.114a.864.864 0 0 1 .717-.098 10.16 10.16 0 0 0 2.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178A1.17 1.17 0 0 1 4.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 0 1-1.162 1.178 1.17 1.17 0 0 1-1.162-1.178c0-.651.52-1.18 1.162-1.18zm5.34 2.867c-1.797-.052-3.746.512-5.28 1.786-1.72 1.428-2.687 3.72-1.78 6.22.942 2.453 3.666 4.229 6.884 4.229.826 0 1.622-.12 2.361-.336a.722.722 0 0 1 .598.082l1.584.926a.272.272 0 0 0 .14.047c.134 0 .24-.111.24-.247 0-.06-.023-.12-.038-.177l-.327-1.233a.582.582 0 0 1-.023-.156.49.49 0 0 1 .201-.398C23.024 18.48 24 16.82 24 14.98c0-3.21-2.931-5.847-7.062-6.122zM14.53 13.39c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.97-.982zm4.844 0c.535 0 .969.44.969.982a.976.976 0 0 1-.969.983.976.976 0 0 1-.969-.983c0-.542.434-.982.969-.982z" />
        </svg>
      );
    case "linux_do":
      return (
        <svg className="size-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <circle cx="12" cy="12" r="11" fill="none" stroke="currentColor" strokeWidth="2" />
          <text x="12" y="16.5" textAnchor="middle" fontSize="14" fontWeight="bold" fill="currentColor">L</text>
        </svg>
      );
    default:
      return (
        <span className="flex size-5 items-center justify-center rounded-full bg-muted text-xs font-bold text-foreground">
          {(displayName || icon || "?").charAt(0).toUpperCase()}
        </span>
      );
  }
}
