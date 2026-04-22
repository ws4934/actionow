"use client";

import { useOnboardingStore } from "@/lib/stores/onboarding-store";
import type { OnboardingStep } from "@/lib/stores/onboarding-store";

export function OnboardingStepIndicator({ currentStep }: { currentStep: OnboardingStep }) {
  const needsWorkspace = useOnboardingStore((s) => s.needsWorkspace);
  const totalSteps = needsWorkspace ? 4 : 3;

  // Map current step to visual index (skip workspace step visually when not needed)
  const visualIndex = !needsWorkspace && currentStep === 3 ? 2 : currentStep;

  return (
    <div className="flex items-center justify-center gap-1.5 py-4">
      {Array.from({ length: totalSteps }, (_, i) => (
        <div
          key={i}
          className={`h-1.5 rounded-full transition-all duration-300 ${
            i === visualIndex
              ? "w-6 bg-accent"
              : "w-2 bg-foreground/20"
          }`}
        />
      ))}
    </div>
  );
}
