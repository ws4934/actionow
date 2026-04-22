/**
 * Comment Panel Store
 * Manages the open/close state of the comment side panel and
 * the currently linked entity (target).
 *
 * Design contract:
 * - setTarget() updates the target WITHOUT opening the panel
 *   (used by entity tabs for silent auto-link when panel is already open)
 * - open(target?) opens the panel and optionally sets a new target
 *   (used by the comment icon on sidebar items for explicit navigation)
 * - Panel remembers the last target when closed so re-opening restores context
 */

import { create } from "zustand";
import type { CommentTargetType } from "@/lib/api/dto/comment.dto";
import { usePreferencesStore } from "./preferences-store";

// ============================================================================
// Types
// ============================================================================

export interface CommentTarget {
  type: CommentTargetType;
  id: string;
  /** Display name shown in the panel header */
  name: string;
  scriptId: string;
}

export interface CommentPanelStoreState {
  isOpen: boolean;
  target: CommentTarget | null;
}

export interface CommentPanelStoreActions {
  /** Open the panel, optionally switching to a new target */
  open: (target?: CommentTarget) => void;
  /** Close the panel (target is preserved for next open) */
  close: () => void;
  /** Toggle open/closed */
  toggle: () => void;
  /**
   * Update the current target without changing isOpen.
   * Called by entity tabs when the panel is already open and user selects
   * a different entity — the panel silently follows the selection.
   */
  setTarget: (target: CommentTarget) => void;
  /** Clear the target without changing isOpen (used on script/page change) */
  clearTarget: () => void;
  /** Full reset (on workspace/script change) */
  reset: () => void;
}

export type CommentPanelStore = CommentPanelStoreState & CommentPanelStoreActions;

// ============================================================================
// Store
// ============================================================================

const INITIAL_STATE: CommentPanelStoreState = {
  isOpen: false,
  target: null,
};

export const useCommentPanelStore = create<CommentPanelStore>((set) => ({
  ...INITIAL_STATE,
  // Hydrate from persisted preferences (SSR-safe)
  isOpen:
    typeof window !== "undefined"
      ? usePreferencesStore.getState().commentPanelOpen
      : false,

  open: (target) =>
    set((state) => {
      usePreferencesStore.getState().setCommentPanelOpen(true);
      return { isOpen: true, target: target ?? state.target };
    }),

  close: () => {
    usePreferencesStore.getState().setCommentPanelOpen(false);
    set({ isOpen: false });
  },

  toggle: () =>
    set((state) => {
      const next = !state.isOpen;
      usePreferencesStore.getState().setCommentPanelOpen(next);
      return { isOpen: next };
    }),

  setTarget: (target) => set({ target }),

  clearTarget: () => set({ target: null }),

  reset: () => {
    usePreferencesStore.getState().setCommentPanelOpen(false);
    set(INITIAL_STATE);
  },
}));

// ============================================================================
// Selectors
// ============================================================================

export const commentPanelSelectors = {
  isOpen: (s: CommentPanelStore) => s.isOpen,
  target: (s: CommentPanelStore) => s.target,
};

// ============================================================================
// Non-hook helpers (for use outside React — e.g., route change handlers)
// ============================================================================

export function getCommentPanelStore() {
  return useCommentPanelStore.getState();
}
