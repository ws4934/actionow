"use client";

import { useTranslations } from "next-intl";
import { Film } from "lucide-react";
import { useOnboardingStore } from "@/lib/stores/onboarding-store";

export function CreateProjectStep() {
  const t = useTranslations("onboarding.createProject");
  const { completeOnboarding } = useOnboardingStore();

  const handleCreate = () => {
    completeOnboarding();
    window.dispatchEvent(new CustomEvent("onboarding:createProject"));
  };

  const handleExplore = () => {
    completeOnboarding();
  };

  return (
    <div className="onboarding-step-enter flex h-full flex-col items-center justify-center px-8 py-10 text-center md:px-12">
      <div className="mb-6 flex size-20 items-center justify-center rounded-full bg-accent/10">
        <Film className="size-10 text-accent" />
      </div>

      <h1 className="mb-3 text-3xl font-bold tracking-tight md:text-4xl">
        {t("title")}
      </h1>
      <p className="mb-8 max-w-md text-lg text-foreground/60">
        {t("subtitle")}
      </p>

      <button
        type="button"
        onClick={handleCreate}
        className="mb-3 rounded-lg bg-accent px-8 py-3 font-black uppercase tracking-wider text-accent-foreground transition-transform hover:scale-[1.02] active:scale-[0.98]"
      >
        {t("createFirst")}
      </button>

      <button
        type="button"
        onClick={handleExplore}
        className="text-sm text-foreground/40 transition-colors hover:text-foreground/60"
      >
        {t("exploreDashboard")}
      </button>
    </div>
  );
}
