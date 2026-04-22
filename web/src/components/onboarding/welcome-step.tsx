"use client";

import { useTranslations } from "next-intl";
import { useSyncExternalStore } from "react";
import NextImage from "next/image";
import { Clapperboard, PenTool, Film, Sparkles, ArrowRight } from "lucide-react";
import { useOnboardingStore, type UserRole } from "@/lib/stores/onboarding-store";
import { useTheme } from "@/components/providers/theme-provider";

const emptySubscribe = () => () => {};
function useMounted() {
  return useSyncExternalStore(emptySubscribe, () => true, () => false);
}

const ROLES: { key: UserRole; icon: typeof Clapperboard; bgColor: string; iconColor: string }[] = [
  { key: "director", icon: Clapperboard, bgColor: "bg-amber-500/15", iconColor: "text-amber-500" },
  { key: "screenwriter", icon: PenTool, bgColor: "bg-purple-500/15", iconColor: "text-purple-500" },
  { key: "producer", icon: Film, bgColor: "bg-blue-500/15", iconColor: "text-blue-500" },
  { key: "explorer", icon: Sparkles, bgColor: "bg-emerald-500/15", iconColor: "text-emerald-500" },
];

export function WelcomeStep() {
  const t = useTranslations("onboarding.welcome");
  const tProfile = useTranslations("onboarding.profile");
  const { userRole, setUserRole, nextStep, skipOnboarding } = useOnboardingStore();
  const { resolvedTheme } = useTheme();
  const mounted = useMounted();

  const logoSrc =
    mounted && resolvedTheme === "dark"
      ? "/full-logo-dark.png"
      : "/full-logo.png";

  return (
    <div className="onboarding-step-enter flex h-full flex-col items-center justify-center px-6 py-8 sm:px-12 sm:py-10">
      {/* Logo */}
      <NextImage
        key={logoSrc}
        src={logoSrc}
        alt="ActioNow"
        width={160}
        height={36}
        priority
        className="mb-6"
      />

      {/* Title + Subtitle */}
      <h1 className="mb-2 text-center text-2xl font-bold tracking-tight sm:text-3xl">
        {t("title")}
      </h1>
      <p className="mb-8 max-w-md text-center text-sm text-foreground/60 sm:text-base">
        {t("subtitle")}
      </p>

      {/* Role selection label */}
      <p className="mb-4 text-sm font-medium text-foreground/70">
        {tProfile("title")}
      </p>

      {/* Role cards — 4 in a row on desktop, 2×2 on mobile */}
      <div className="mb-8 grid w-full max-w-2xl grid-cols-2 gap-3 sm:grid-cols-4">
        {ROLES.map(({ key, icon: Icon, bgColor, iconColor }) => {
          const isSelected = userRole === key;
          return (
            <button
              key={key}
              type="button"
              onClick={() => setUserRole(key)}
              className={`flex flex-col items-center gap-2 rounded-xl border-2 px-3 py-4 transition-all ${
                isSelected
                  ? "border-accent bg-accent/5 shadow-lg shadow-accent/10"
                  : "border-foreground/10 bg-surface/50 hover:border-accent/30 hover:bg-surface/80"
              }`}
            >
              <div className={`flex size-10 items-center justify-center rounded-xl ${bgColor}`}>
                <Icon className={`size-5 ${isSelected ? "text-accent" : iconColor}`} />
              </div>
              <span className="text-sm font-semibold text-foreground">
                {tProfile(`roles.${key}` as "roles.director")}
              </span>
              <span className="text-[11px] leading-tight text-foreground/50">
                {tProfile(`roles.${key}Desc` as "roles.directorDesc")}
              </span>
            </button>
          );
        })}
      </div>

      {/* CTA */}
      <button
        type="button"
        onClick={nextStep}
        disabled={!userRole}
        className="group mb-3 flex items-center gap-2 rounded-lg bg-accent px-8 py-3 font-black uppercase tracking-wider text-accent-foreground transition-transform enabled:hover:scale-[1.02] enabled:active:scale-[0.98] disabled:opacity-40"
      >
        {t("getStarted")}
        <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
      </button>

      <button
        type="button"
        onClick={skipOnboarding}
        className="text-sm text-foreground/40 transition-colors hover:text-foreground/60"
      >
        {t("skip")}
      </button>
    </div>
  );
}
