/**
 * Preferences Store
 * Manages UI preferences using Zustand + persist
 */

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { createPersistStorage } from "./persist-storage";

export type ViewMode = "list" | "grid" | "detail";
export type TabKey = "details" | "episodes" | "storyboards" | "characters" | "scenes" | "props" | "assets";

export interface ScriptPanelPreferences {
  viewModes: Record<TabKey, ViewMode>;
  sidebarCollapsed: Record<TabKey, boolean>;
}

export interface Preferences {
  scriptPanel: ScriptPanelPreferences;
}

const DEFAULT_VIEW_MODES: Record<TabKey, ViewMode> = {
  details: "list",
  episodes: "list",
  storyboards: "list",
  characters: "list",
  scenes: "list",
  props: "list",
  assets: "grid",
};

const DEFAULT_SIDEBAR_COLLAPSED: Record<TabKey, boolean> = {
  details: true,
  episodes: true,
  storyboards: true,
  characters: true,
  scenes: true,
  props: true,
  assets: true,
};

export type ActivePanelPreference = "agent" | "ai-generation";

interface PreferencesStoreState {
  scriptPanel: ScriptPanelPreferences;
  scriptActiveTabs: Record<string, TabKey>;
  commentPanelOpen: boolean;
  commentPanelWidth: number;
  activePanel: ActivePanelPreference;
  leftPanelWidth: number;
  setViewMode: (tabKey: TabKey, mode: ViewMode) => void;
  setSidebarCollapsed: (tabKey: TabKey, collapsed: boolean) => void;
  setScriptActiveTab: (scriptId: string, tabKey: TabKey) => void;
  setCommentPanelOpen: (open: boolean) => void;
  setCommentPanelWidth: (width: number) => void;
  setActivePanel: (panel: ActivePanelPreference) => void;
  setLeftPanelWidth: (width: number) => void;
  clearPreferences: () => void;
  clearScriptTabs: () => void;
}

function getDefaultScriptPanel(): ScriptPanelPreferences {
  return {
    viewModes: { ...DEFAULT_VIEW_MODES },
    sidebarCollapsed: { ...DEFAULT_SIDEBAR_COLLAPSED },
  };
}

export const usePreferencesStore = create<PreferencesStoreState>()(
  persist(
    (set) => ({
      scriptPanel: getDefaultScriptPanel(),
      scriptActiveTabs: {},
      commentPanelOpen: false,
      commentPanelWidth: 320,
      activePanel: "agent" as ActivePanelPreference,
      leftPanelWidth: 30,
      setViewMode: (tabKey, mode) =>
        set((state) => ({
          scriptPanel: {
            ...state.scriptPanel,
            viewModes: {
              ...DEFAULT_VIEW_MODES,
              ...state.scriptPanel.viewModes,
              [tabKey]: mode,
            },
          },
        })),
      setSidebarCollapsed: (tabKey, collapsed) =>
        set((state) => ({
          scriptPanel: {
            ...state.scriptPanel,
            sidebarCollapsed: {
              ...DEFAULT_SIDEBAR_COLLAPSED,
              ...state.scriptPanel.sidebarCollapsed,
              [tabKey]: collapsed,
            },
          },
        })),
      setScriptActiveTab: (scriptId, tabKey) =>
        set((state) => ({
          scriptActiveTabs: {
            ...state.scriptActiveTabs,
            [scriptId]: tabKey,
          },
        })),
      setCommentPanelOpen: (open) => set({ commentPanelOpen: open }),
      setCommentPanelWidth: (width) => set({ commentPanelWidth: width }),
      setActivePanel: (panel) => set({ activePanel: panel }),
      setLeftPanelWidth: (width) => set({ leftPanelWidth: width }),
      clearPreferences: () =>
        set((state) => ({
          ...state,
          scriptPanel: getDefaultScriptPanel(),
          commentPanelOpen: false,
          commentPanelWidth: 320,
          activePanel: "agent" as ActivePanelPreference,
          leftPanelWidth: 30,
        })),
      clearScriptTabs: () =>
        set((state) => ({
          ...state,
          scriptActiveTabs: {},
        })),
    }),
    {
      name: "actionow_preferences_store",
      storage: createPersistStorage<Pick<PreferencesStoreState, "scriptPanel" | "scriptActiveTabs" | "commentPanelOpen" | "commentPanelWidth" | "activePanel" | "leftPanelWidth">>(),
      partialize: (state) => ({
        scriptPanel: state.scriptPanel,
        scriptActiveTabs: state.scriptActiveTabs,
        commentPanelOpen: state.commentPanelOpen,
        commentPanelWidth: state.commentPanelWidth,
        activePanel: state.activePanel,
        leftPanelWidth: state.leftPanelWidth,
      }),
    }
  )
);

// Get view mode for a specific tab
export function getViewMode(tabKey: TabKey): ViewMode {
  const state = usePreferencesStore.getState();
  return state.scriptPanel.viewModes[tabKey] ?? DEFAULT_VIEW_MODES[tabKey];
}

// Set view mode for a specific tab
export function setViewMode(tabKey: TabKey, mode: ViewMode): void {
  usePreferencesStore.getState().setViewMode(tabKey, mode);
}

// Get sidebar collapsed state for a specific tab
export function getSidebarCollapsed(tabKey: TabKey): boolean {
  const state = usePreferencesStore.getState();
  return state.scriptPanel.sidebarCollapsed[tabKey] ?? DEFAULT_SIDEBAR_COLLAPSED[tabKey];
}

// Set sidebar collapsed state for a specific tab
export function setSidebarCollapsed(tabKey: TabKey, collapsed: boolean): void {
  usePreferencesStore.getState().setSidebarCollapsed(tabKey, collapsed);
}

// Clear all preferences
export function clearPreferences(): void {
  usePreferencesStore.getState().clearPreferences();
}

export function clearScriptTabs(): void {
  usePreferencesStore.getState().clearScriptTabs();
}

// Get active tab for a specific script
export function getScriptActiveTab(scriptId: string): TabKey {
  return usePreferencesStore.getState().scriptActiveTabs[scriptId] ?? "details";
}

// Set active tab for a specific script
export function setScriptActiveTab(scriptId: string, tabKey: TabKey): void {
  usePreferencesStore.getState().setScriptActiveTab(scriptId, tabKey);
}
