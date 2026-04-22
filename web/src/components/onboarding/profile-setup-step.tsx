"use client";

import { useTranslations } from "next-intl";
import { Clapperboard, PenTool, Film, Sparkles, ArrowRight } from "lucide-react";
import { useOnboardingStore, type UserRole } from "@/lib/stores/onboarding-store";

const ROLES: { key: UserRole; icon: typeof Clapperboard; bgColor: string; iconColor: string }[] = [
  { key: "director", icon: Clapperboard, bgColor: "bg-amber-500/15", iconColor: "text-amber-500" },
  { key: "screenwriter", icon: PenTool, bgColor: "bg-purple-500/15", iconColor: "text-purple-500" },
  { key: "producer", icon: Film, bgColor: "bg-blue-500/15", iconColor: "text-blue-500" },
  { key: "explorer", icon: Sparkles, bgColor: "bg-emerald-500/15", iconColor: "text-emerald-500" },
];

export function ProfileSetupStep() {
  const t = useTranslations("onboarding.profile");
  const { userRole, setUserRole, nextStep, prevStep } = useOnboardingStore();

  return (
    <div className="onboarding-step-enter flex h-full items-center px-8 py-10 md:px-12">
      <div className="grid w-full gap-10 md:grid-cols-2 md:gap-12">
        {/* Left column — heading */}
        <div className="flex flex-col justify-center">
          <h1 className="mb-3 text-3xl font-bold leading-tight tracking-tight md:text-4xl">
            {t("title")}
          </h1>
          <p className="max-w-sm text-lg text-foreground/60">
            {t("subtitle")}
          </p>
        </div>

        {/* Right column — role grid */}
        <div className="flex flex-col justify-center">
          <div className="grid grid-cols-2 gap-3">
            {ROLES.map(({ key, icon: Icon, bgColor, iconColor }) => {
              const isSelected = userRole === key;
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => setUserRole(key)}
                  className={`flex flex-col items-center gap-2.5 rounded-xl border-2 p-5 transition-all ${
                    isSelected
                      ? "border-accent bg-accent/5 shadow-lg shadow-accent/10"
                      : "border-foreground/10 bg-surface/50 hover:border-accent/30 hover:bg-surface/80"
                  }`}
                >
                  <div className={`flex size-12 items-center justify-center rounded-xl ${bgColor}`}>
                    <Icon className={`size-6 ${isSelected ? "text-accent" : iconColor}`} />
                  </div>
                  <span className="font-semibold text-foreground">
                    {t(`roles.${key}` as "roles.director")}
                  </span>
                  <span className="text-xs text-foreground/50">
                    {t(`roles.${key}Desc` as "roles.directorDesc")}
                  </span>
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={nextStep}
            disabled={!userRole}
            className="group mt-6 flex w-fit items-center gap-2 self-end rounded-lg bg-accent px-6 py-3 font-black uppercase tracking-wider text-accent-foreground transition-transform enabled:hover:scale-[1.02] enabled:active:scale-[0.98] disabled:opacity-40"
          >
            {t("continue")}
            <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
          </button>

          <button
            type="button"
            onClick={prevStep}
            className="mt-3 w-fit self-end text-sm text-foreground/40 transition-colors hover:text-foreground/60"
          >
            {t("back")}
          </button>
        </div>
      </div>
    </div>
  );
}
