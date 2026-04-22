"use client";

import { useEffect, useState } from "react";
import { useOnboardingStore } from "@/lib/stores/onboarding-store";
import { WelcomeStep } from "./welcome-step";
import { FeatureTourStep } from "./feature-tour-step";
import { CreateWorkspaceStep } from "./create-workspace-step";
import { CreateProjectStep } from "./create-project-step";
import { OnboardingStepIndicator } from "./onboarding-step-indicator";
import type { WorkspaceDTO } from "@/lib/api/dto";

interface OnboardingModalProps {
  onWorkspaceCreated?: (workspace: WorkspaceDTO) => void;
}

export function OnboardingModal({ onWorkspaceCreated }: OnboardingModalProps) {
  const hasCompleted = useOnboardingStore((s) => s.hasCompletedOnboarding);
  const currentStep = useOnboardingStore((s) => s.currentStep);
  const needsWorkspace = useOnboardingStore((s) => s.needsWorkspace);

  const [isHydrated, setIsHydrated] = useState(false);

  useEffect(() => {
    const unsub = useOnboardingStore.persist.onFinishHydration(() => {
      setIsHydrated(true);
    });
    if (useOnboardingStore.persist.hasHydrated()) {
      setIsHydrated(true);
    }
    return unsub;
  }, []);

  if (!isHydrated || hasCompleted) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="flex h-[min(95vh,720px)] w-[min(95vw,1000px)] flex-col overflow-hidden rounded-2xl bg-overlay shadow-2xl">
        <div className="grid-subtle relative flex-1 overflow-hidden">
          {currentStep === 0 && <WelcomeStep />}
          {currentStep === 1 && <FeatureTourStep />}
          {currentStep === 2 && needsWorkspace && onWorkspaceCreated && (
            <CreateWorkspaceStep onWorkspaceCreated={onWorkspaceCreated} />
          )}
          {currentStep === 3 && <CreateProjectStep />}
        </div>
        <OnboardingStepIndicator currentStep={currentStep} />
      </div>
    </div>
  );
}
