"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { useAuthStore } from "@/lib/stores/auth-store";

export default function WorkspaceHomePage() {
  const router = useRouter();
  const locale = useLocale();
  const t = useTranslations("workspace.empty");
  const tokenWorkspaceId = useAuthStore((state) => state.tokenBundle?.workspaceId ?? null);

  useEffect(() => {
    if (!tokenWorkspaceId) return;
    router.replace(`/${locale}/workspace/projects`);
  }, [locale, router, tokenWorkspaceId]);

  if (!tokenWorkspaceId) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
        <h2 className="text-xl font-semibold text-foreground">{t("title")}</h2>
        <p className="max-w-md text-sm text-muted">{t("description")}</p>
      </div>
    );
  }

  return null;
}
