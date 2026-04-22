"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Users, Layers, FolderOpen, Wand2, ArrowRight } from "lucide-react";
import { useOnboardingStore } from "@/lib/stores/onboarding-store";

const FEATURES = [
  { icon: Users, color: "bg-blue-500/15 text-blue-500" },
  { icon: Layers, color: "bg-purple-500/15 text-purple-500" },
  { icon: FolderOpen, color: "bg-emerald-500/15 text-emerald-500" },
  { icon: Wand2, color: "bg-amber-500/15 text-amber-500" },
] as const;

export function FeatureTourStep() {
  const t = useTranslations("onboarding.featureTour");
  const { nextStep, skipOnboarding } = useOnboardingStore();
  const [slideIndex, setSlideIndex] = useState(0);

  const isLast = slideIndex === FEATURES.length - 1;
  const { icon: Icon, color } = FEATURES[slideIndex];

  const handleNext = () => {
    if (isLast) {
      nextStep();
    } else {
      setSlideIndex((i) => i + 1);
    }
  };

  return (
    <div className="onboarding-step-enter flex h-full flex-col px-8 py-10 md:px-12">
      <div className="flex flex-1 items-center">
        <div
          key={slideIndex}
          className="grid w-full gap-8 md:grid-cols-[1fr_auto_1fr] md:items-center md:gap-12"
          style={{ animation: "onboarding-fade-in 0.25s ease-out" }}
        >
          {/* Left — number + label */}
          <div className="flex flex-col items-start gap-3">
            <span className="text-6xl font-black tabular-nums text-foreground/10 md:text-7xl">
              {String(slideIndex + 1).padStart(2, "0")}
            </span>
            <span className="rounded-full border border-foreground/10 bg-surface/50 px-4 py-1.5 text-xs font-semibold uppercase tracking-wider text-foreground/60">
              {t(`slides.${slideIndex}.label` as "slides.0.label")}
            </span>
          </div>

          {/* Center — title + desc */}
          <div className="max-w-sm">
            <h2 className="mb-3 text-2xl font-bold leading-tight md:text-3xl">
              {t(`slides.${slideIndex}.title` as "slides.0.title")}
            </h2>
            <p className="text-foreground/60">
              {t(`slides.${slideIndex}.description` as "slides.0.description")}
            </p>
          </div>

          {/* Right — icon */}
          <div className="hidden justify-end md:flex">
            <div className={`flex size-24 items-center justify-center rounded-2xl ${color.split(" ")[0]}`}>
              <Icon className={`size-12 ${color.split(" ")[1]}`} />
            </div>
          </div>
        </div>
      </div>

      {/* Bottom controls */}
      <div className="flex items-center justify-between pt-4">
        <button
          type="button"
          onClick={skipOnboarding}
          className="text-sm text-foreground/40 transition-colors hover:text-foreground/60"
        >
          {t("skipTour")}
        </button>

        <div className="flex items-center gap-4">
          {/* Dot indicators */}
          <div className="flex gap-1.5">
            {FEATURES.map((_, i) => (
              <div
                key={i}
                className={`size-1.5 rounded-full transition-colors ${
                  i === slideIndex ? "bg-accent" : "bg-foreground/20"
                }`}
              />
            ))}
          </div>

          <button
            type="button"
            onClick={handleNext}
            className="group flex items-center gap-2 rounded-lg bg-accent px-5 py-2.5 text-sm font-bold uppercase tracking-wider text-accent-foreground transition-transform hover:scale-[1.02] active:scale-[0.98]"
          >
            {isLast ? t("gotIt") : t("next")}
            <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
          </button>
        </div>
      </div>
    </div>
  );
}
