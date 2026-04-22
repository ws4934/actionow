/**
 * Collaboration Store
 * Normalized Zustand store shape:
 * - state: data only
 * - actions: mutations only
 * - selectors: derived read helpers
 */

import { create } from "zustand";
import type { CollabUser, CollabTab } from "@/lib/websocket/types";

// ============================================================================
// Types
// ============================================================================

export interface EntityCollabState {
  viewers: CollabUser[];
  editor: CollabUser | null;
  lockedAt: number | null;
}

export interface CollaborationStoreState {
  currentScriptId: string | null;
  currentTab: CollabTab;
  users: CollabUser[];
  tabUserCounts: Partial<Record<CollabTab, number>>;
  entityStates: Map<string, EntityCollabState>;
}

export interface CollaborationStoreActions {
  setCurrentScript: (scriptId: string | null) => void;
  setCurrentTab: (tab: CollabTab) => void;
  setUsers: (users: CollabUser[], tabUserCounts: Partial<Record<CollabTab, number>>) => void;
  addUser: (user: CollabUser) => void;
  removeUser: (userId: string) => void;
  updateUser: (user: CollabUser) => void;
  setEntityCollaboration: (
    entityType: string,
    entityId: string,
    viewers: CollabUser[],
    editor: CollabUser | null
  ) => void;
  clearEntityCollaboration: (entityType: string, entityId: string) => void;
  cleanStaleEditors: (maxAgeMs?: number) => void;
  reset: () => void;
}

export type CollaborationStore = CollaborationStoreState & CollaborationStoreActions;

// ============================================================================
// Constants
// ============================================================================

const INITIAL_STATE: CollaborationStoreState = {
  currentScriptId: null,
  currentTab: "DETAIL",
  users: [],
  tabUserCounts: {},
  entityStates: new Map(),
};

// ============================================================================
// Helpers
// ============================================================================

function makeEntityKey(entityType: string, entityId: string): string {
  return `${entityType.toUpperCase()}:${entityId}`;
}

function isSameUser(a: CollabUser, b: CollabUser): boolean {
  return a.userId === b.userId;
}

// ============================================================================
// Selectors (read-only helpers)
// ============================================================================

export const collaborationSelectors = {
  currentScriptId: (state: CollaborationStore) => state.currentScriptId,
  currentTab: (state: CollaborationStore) => state.currentTab,
  users: (state: CollaborationStore) => state.users,
  tabUserCounts: (state: CollaborationStore) => state.tabUserCounts,
  entityStates: (state: CollaborationStore) => state.entityStates,
  getUsersInTab: (state: CollaborationStoreState, tab: CollabTab): CollabUser[] =>
    state.users.filter((user) => user.tab === tab),
  getEntityState: (
    state: CollaborationStoreState,
    entityType: string,
    entityId: string
  ): EntityCollabState | null => state.entityStates.get(makeEntityKey(entityType, entityId)) || null,
  isEntityLocked: (
    state: CollaborationStoreState,
    entityType: string,
    entityId: string,
    currentUserId: string
  ): boolean => {
    const entityState = state.entityStates.get(makeEntityKey(entityType, entityId));
    return !!(entityState?.editor && entityState.editor.userId !== currentUserId);
  },
  getEntityEditor: (
    state: CollaborationStoreState,
    entityType: string,
    entityId: string
  ): CollabUser | null =>
    state.entityStates.get(makeEntityKey(entityType, entityId))?.editor || null,
};

// ============================================================================
// Store
// ============================================================================

export const useCollaborationStore = create<CollaborationStore>((set) => ({
  ...INITIAL_STATE,

  setCurrentScript: (scriptId) =>
    set({
      currentScriptId: scriptId,
      currentTab: INITIAL_STATE.currentTab,
      users: [],
      tabUserCounts: {},
      entityStates: new Map(),
    }),

  setCurrentTab: (tab) => set({ currentTab: tab }),

  setUsers: (users, tabUserCounts) => set({ users, tabUserCounts }),

  addUser: (user) =>
    set((state) => {
      if (state.users.some((existing) => isSameUser(existing, user))) {
        return state;
      }

      const users = [...state.users, user];
      const tabUserCounts = { ...state.tabUserCounts };
      if (user.tab) {
        tabUserCounts[user.tab] = (tabUserCounts[user.tab] || 0) + 1;
      }

      return { users, tabUserCounts };
    }),

  removeUser: (userId) =>
    set((state) => {
      const existing = state.users.find((user) => user.userId === userId);
      if (!existing) return state;

      const users = state.users.filter((user) => user.userId !== userId);
      const tabUserCounts = { ...state.tabUserCounts };
      if (existing.tab && tabUserCounts[existing.tab]) {
        tabUserCounts[existing.tab] = Math.max(0, (tabUserCounts[existing.tab] || 0) - 1);
      }

      const entityStates = new Map(state.entityStates);
      entityStates.forEach((entityState, key) => {
        const viewers = entityState.viewers.filter((viewer) => viewer.userId !== userId);
        const editor = entityState.editor?.userId === userId ? null : entityState.editor;
        const lockedAt = editor ? entityState.lockedAt : null;

        if (viewers.length !== entityState.viewers.length || editor !== entityState.editor) {
          entityStates.set(key, { viewers, editor, lockedAt });
        }
      });

      return { users, tabUserCounts, entityStates };
    }),

  updateUser: (user) =>
    set((state) => {
      const existing = state.users.find((item) => item.userId === user.userId);
      if (!existing) {
        return { users: [...state.users, user] };
      }

      const tabUserCounts = { ...state.tabUserCounts };
      if (existing.tab !== user.tab) {
        if (existing.tab) {
          tabUserCounts[existing.tab] = Math.max(0, (tabUserCounts[existing.tab] || 0) - 1);
        }
        if (user.tab) {
          tabUserCounts[user.tab] = (tabUserCounts[user.tab] || 0) + 1;
        }
      }

      return {
        users: state.users.map((item) => (item.userId === user.userId ? user : item)),
        tabUserCounts,
      };
    }),

  setEntityCollaboration: (entityType, entityId, viewers, editor) =>
    set((state) => {
      const key = makeEntityKey(entityType, entityId);
      const previous = state.entityStates.get(key);
      const entityStates = new Map(state.entityStates);

      let lockedAt: number | null = null;
      if (editor) {
        lockedAt = previous?.editor?.userId === editor.userId ? previous.lockedAt : Date.now();
      }

      entityStates.set(key, { viewers, editor, lockedAt });
      return { entityStates };
    }),

  clearEntityCollaboration: (entityType, entityId) =>
    set((state) => {
      const entityStates = new Map(state.entityStates);
      entityStates.delete(makeEntityKey(entityType, entityId));
      return { entityStates };
    }),

  cleanStaleEditors: (maxAgeMs = 10 * 60 * 1000) =>
    set((state) => {
      const now = Date.now();
      const entityStates = new Map(state.entityStates);
      let hasChanges = false;

      entityStates.forEach((entityState, key) => {
        if (entityState.editor && entityState.lockedAt && now - entityState.lockedAt > maxAgeMs) {
          entityStates.set(key, {
            ...entityState,
            editor: null,
            lockedAt: null,
          });
          hasChanges = true;
        }
      });

      return hasChanges ? { entityStates } : state;
    }),

  reset: () =>
    set({
      ...INITIAL_STATE,
      entityStates: new Map(),
    }),
}));

export function useCollaborationState() {
  const currentScriptId = useCollaborationStore(collaborationSelectors.currentScriptId);
  const currentTab = useCollaborationStore(collaborationSelectors.currentTab);
  const users = useCollaborationStore(collaborationSelectors.users);
  const tabUserCounts = useCollaborationStore(collaborationSelectors.tabUserCounts);
  const entityStates = useCollaborationStore(collaborationSelectors.entityStates);

  return {
    currentScriptId,
    currentTab,
    users,
    tabUserCounts,
    entityStates,
  };
}

export function useCollaborationActions() {
  const setCurrentScript = useCollaborationStore((state) => state.setCurrentScript);
  const setCurrentTab = useCollaborationStore((state) => state.setCurrentTab);
  const setUsers = useCollaborationStore((state) => state.setUsers);
  const addUser = useCollaborationStore((state) => state.addUser);
  const removeUser = useCollaborationStore((state) => state.removeUser);
  const updateUser = useCollaborationStore((state) => state.updateUser);
  const setEntityCollaboration = useCollaborationStore((state) => state.setEntityCollaboration);
  const clearEntityCollaboration = useCollaborationStore((state) => state.clearEntityCollaboration);
  const cleanStaleEditors = useCollaborationStore((state) => state.cleanStaleEditors);
  const reset = useCollaborationStore((state) => state.reset);

  return {
    setCurrentScript,
    setCurrentTab,
    setUsers,
    addUser,
    removeUser,
    updateUser,
    setEntityCollaboration,
    clearEntityCollaboration,
    cleanStaleEditors,
    reset,
  };
}

export default useCollaborationStore;

