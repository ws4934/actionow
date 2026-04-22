/**
 * Script Store
 * Manages the currently active/open script per workspace using Zustand + persist
 */

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { getTokenWorkspaceId } from "./auth-store";
import { createPersistStorage } from "./persist-storage";

export interface ActiveScript {
  id: string;
  title: string;
  coverUrl?: string;
  status?: string;
  openedAt: number; // timestamp
  workspaceId: string;
}

interface ScriptStoreState {
  activeScriptsByWorkspace: Record<string, ActiveScript>;
  setActiveScript: (script: ActiveScript) => void;
  clearActiveScript: (workspaceId: string) => void;
  clearAllActiveScripts: () => void;
}

export const useScriptStore = create<ScriptStoreState>()(
  persist(
    (set) => ({
      activeScriptsByWorkspace: {},
      setActiveScript: (script) =>
        set((state) => ({
          activeScriptsByWorkspace: {
            ...state.activeScriptsByWorkspace,
            [script.workspaceId]: script,
          },
        })),
      clearActiveScript: (workspaceId) =>
        set((state) => {
          const next = { ...state.activeScriptsByWorkspace };
          delete next[workspaceId];
          return { activeScriptsByWorkspace: next };
        }),
      clearAllActiveScripts: () => set({ activeScriptsByWorkspace: {} }),
    }),
    {
      name: "actionow_script_store",
      storage: createPersistStorage<Pick<ScriptStoreState, "activeScriptsByWorkspace">>(),
      partialize: (state) => ({
        activeScriptsByWorkspace: state.activeScriptsByWorkspace,
      }),
    }
  )
);

function dispatchActiveScriptChanged(script: ActiveScript | null): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent("activeScriptChanged", { detail: script }));
}

function resolveWorkspaceId(workspaceId?: string | null): string | null {
  return workspaceId || getTokenWorkspaceId();
}

export function getActiveScript(workspaceId?: string | null): ActiveScript | null {
  const wsId = resolveWorkspaceId(workspaceId);
  if (!wsId) return null;
  return useScriptStore.getState().activeScriptsByWorkspace[wsId] ?? null;
}

export function getActiveScriptId(workspaceId?: string | null): string | null {
  const script = getActiveScript(workspaceId);
  return script?.id ?? null;
}

export function setActiveScript(script: ActiveScript): void {
  useScriptStore.getState().setActiveScript(script);
  dispatchActiveScriptChanged(script);
}

export function openScript(
  id: string,
  title: string,
  workspaceId: string,
  coverUrl?: string,
  status?: string
): void {
  setActiveScript({
    id,
    title,
    coverUrl,
    status,
    openedAt: Date.now(),
    workspaceId,
  });
}

export function clearActiveScript(workspaceId?: string | null): void {
  const wsId = resolveWorkspaceId(workspaceId);
  if (!wsId) return;
  useScriptStore.getState().clearActiveScript(wsId);
  dispatchActiveScriptChanged(null);
}

export function clearAllActiveScripts(): void {
  useScriptStore.getState().clearAllActiveScripts();
  dispatchActiveScriptChanged(null);
}

export function hasActiveScript(workspaceId?: string | null): boolean {
  return !!getActiveScriptId(workspaceId);
}
