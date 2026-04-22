/**
 * Onboarding Store
 * Tracks first-login onboarding flow state using Zustand + persist
 */

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { createPersistStorage } from "./persist-storage";

export type OnboardingStep = 0 | 1 | 2 | 3;
export type UserRole = "director" | "screenwriter" | "producer" | "explorer";

interface OnboardingStoreState {
  hasCompletedOnboarding: boolean;
  currentStep: OnboardingStep;
  userRole: UserRole | null;
  skippedOnboarding: boolean;
  dismissedTips: string[];
  needsWorkspace: boolean;

  nextStep: () => void;
  prevStep: () => void;
  setUserRole: (role: UserRole) => void;
  setNeedsWorkspace: (needs: boolean) => void;
  completeOnboarding: () => void;
  skipOnboarding: () => void;
  resetOnboarding: () => void;
  dismissTip: (tipId: string) => void;
}

export const useOnboardingStore = create<OnboardingStoreState>()(
  persist(
    (set) => ({
      hasCompletedOnboarding: false,
      currentStep: 0,
      userRole: null,
      skippedOnboarding: false,
      dismissedTips: [],
      needsWorkspace: false,

      nextStep: () =>
        set((state) => {
          const next = state.currentStep + 1;
          // Skip workspace step (2) when user already has workspaces
          if (next === 2 && !state.needsWorkspace) {
            return { currentStep: 3 as OnboardingStep };
          }
          return { currentStep: Math.min(next, 3) as OnboardingStep };
        }),

      prevStep: () =>
        set((state) => {
          const prev = state.currentStep - 1;
          // Skip workspace step (2) when user already has workspaces
          if (prev === 2 && !state.needsWorkspace) {
            return { currentStep: 1 as OnboardingStep };
          }
          return { currentStep: Math.max(prev, 0) as OnboardingStep };
        }),

      setUserRole: (role) => set({ userRole: role }),

      setNeedsWorkspace: (needs) => set({ needsWorkspace: needs }),

      completeOnboarding: () =>
        set({
          hasCompletedOnboarding: true,
          currentStep: 3,
        }),

      skipOnboarding: () =>
        set({
          hasCompletedOnboarding: true,
          skippedOnboarding: true,
          currentStep: 3,
        }),

      resetOnboarding: () =>
        set({
          hasCompletedOnboarding: false,
          currentStep: 0,
          userRole: null,
          skippedOnboarding: false,
          needsWorkspace: false,
        }),

      dismissTip: (tipId) =>
        set((state) => ({
          dismissedTips: state.dismissedTips.includes(tipId)
            ? state.dismissedTips
            : [...state.dismissedTips, tipId],
        })),
    }),
    {
      name: "actionow_onboarding_store",
      storage: createPersistStorage<Pick<OnboardingStoreState, "hasCompletedOnboarding" | "userRole" | "skippedOnboarding" | "dismissedTips">>(),
      partialize: (state) => ({
        hasCompletedOnboarding: state.hasCompletedOnboarding,
        userRole: state.userRole,
        skippedOnboarding: state.skippedOnboarding,
        dismissedTips: state.dismissedTips,
      }),
    }
  )
);

export function hasCompletedOnboarding(): boolean {
  return useOnboardingStore.getState().hasCompletedOnboarding;
}

export function isTipDismissed(tipId: string): boolean {
  return useOnboardingStore.getState().dismissedTips.includes(tipId);
}

export function resetOnboarding(): void {
  useOnboardingStore.getState().resetOnboarding();
}
