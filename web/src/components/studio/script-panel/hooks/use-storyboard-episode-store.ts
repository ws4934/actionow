/**
 * Storyboard Episode Store
 * Shares the currently-selected storyboard episode with the parent ScriptPanel
 * so the tab label can render "分镜 · 剧集title".
 */

import { create } from "zustand";

export interface StoryboardEpisodeInfo {
  id: string;
  sequence: number;
  title: string;
}

interface StoryboardEpisodeState {
  byScript: Record<string, StoryboardEpisodeInfo | undefined>;
  setEpisode: (scriptId: string, info: StoryboardEpisodeInfo | undefined) => void;
}

export const useStoryboardEpisodeStore = create<StoryboardEpisodeState>()((set) => ({
  byScript: {},
  setEpisode: (scriptId, info) =>
    set((state) => ({
      byScript: { ...state.byScript, [scriptId]: info },
    })),
}));

export function useStoryboardEpisode(scriptId: string): StoryboardEpisodeInfo | undefined {
  return useStoryboardEpisodeStore((s) => s.byScript[scriptId]);
}
