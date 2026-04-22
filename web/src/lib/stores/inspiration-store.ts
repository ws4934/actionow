/**
 * Inspiration Store
 * Normalized Zustand store following task-store.ts pattern.
 */

import { create } from "zustand";
import type {
  InspirationSessionDTO,
  InspirationRecordDTO,
  InspirationAssetDTO,
  InspirationRecordStatus,
} from "@/lib/api/dto/inspiration.dto";

// ============================================================================
// Types
// ============================================================================

export interface InspirationStoreState {
  sessions: InspirationSessionDTO[];
  currentSessionId: string | null;
  records: Map<string, InspirationRecordDTO>;
  /** Pre-sorted list (ascending by createdAt) */
  recordsList: InspirationRecordDTO[];
  /** taskId → recordId mapping for WS event updates */
  taskRecordMap: Map<string, string>;
  isLoadingSessions: boolean;
  isLoadingRecords: boolean;
}

export interface InspirationStoreActions {
  // Sessions
  setSessions: (sessions: InspirationSessionDTO[]) => void;
  prependSession: (session: InspirationSessionDTO) => void;
  removeSession: (sessionId: string) => void;
  updateSession: (sessionId: string, updates: Partial<InspirationSessionDTO>) => void;
  setCurrentSessionId: (sessionId: string | null) => void;
  setLoadingSessions: (loading: boolean) => void;
  // Records
  setRecords: (records: InspirationRecordDTO[]) => void;
  appendRecord: (record: InspirationRecordDTO) => void;
  upsertRecord: (record: InspirationRecordDTO) => void;
  updateRecordStatus: (
    recordId: string,
    status: InspirationRecordStatus,
    updates?: Partial<InspirationRecordDTO>
  ) => void;
  updateRecordByTaskId: (
    taskId: string,
    updates: Partial<InspirationRecordDTO>
  ) => void;
  removeRecord: (recordId: string) => void;
  setLoadingRecords: (loading: boolean) => void;
  // Reset
  reset: () => void;
}

export type InspirationStore = InspirationStoreState & InspirationStoreActions;

// ============================================================================
// Helpers
// ============================================================================

const INITIAL_STATE: InspirationStoreState = {
  sessions: [],
  currentSessionId: null,
  records: new Map(),
  recordsList: [],
  taskRecordMap: new Map(),
  isLoadingSessions: false,
  isLoadingRecords: false,
};

function deriveRecordsList(records: Map<string, InspirationRecordDTO>): InspirationRecordDTO[] {
  return Array.from(records.values()).sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
  );
}

function deriveTaskMap(records: Map<string, InspirationRecordDTO>): Map<string, string> {
  const map = new Map<string, string>();
  records.forEach((record) => {
    if (record.taskId) {
      map.set(record.taskId, record.id);
    }
  });
  return map;
}

function derivedRecords(records: Map<string, InspirationRecordDTO>) {
  return {
    records,
    recordsList: deriveRecordsList(records),
    taskRecordMap: deriveTaskMap(records),
  };
}

// ============================================================================
// Selectors
// ============================================================================

export const inspirationSelectors = {
  currentSession: (state: InspirationStore): InspirationSessionDTO | null =>
    state.sessions.find((s) => s.id === state.currentSessionId) ?? null,
  hasRecords: (state: InspirationStore) => state.recordsList.length > 0,
  recordCount: (state: InspirationStore) => state.recordsList.length,
  sessionCount: (state: InspirationStore) => state.sessions.length,
  getRecordByTaskId:
    (taskId: string) =>
    (state: InspirationStore): InspirationRecordDTO | undefined => {
      const recordId = state.taskRecordMap.get(taskId);
      return recordId ? state.records.get(recordId) : undefined;
    },
};

// ============================================================================
// Store
// ============================================================================

export const useInspirationStore = create<InspirationStore>((set) => ({
  ...INITIAL_STATE,

  // -- Sessions --

  setSessions: (sessions) => set({ sessions }),

  prependSession: (session) =>
    set((state) => ({ sessions: [session, ...state.sessions] })),

  removeSession: (sessionId) =>
    set((state) => {
      const sessions = state.sessions.filter((s) => s.id !== sessionId);
      const currentSessionId =
        state.currentSessionId === sessionId ? null : state.currentSessionId;
      // Clear records if current session was deleted
      if (state.currentSessionId === sessionId) {
        return {
          sessions,
          currentSessionId,
          ...derivedRecords(new Map()),
        };
      }
      return { sessions, currentSessionId };
    }),

  updateSession: (sessionId, updates) =>
    set((state) => ({
      sessions: state.sessions.map((s) =>
        s.id === sessionId ? { ...s, ...updates } : s
      ),
    })),

  setCurrentSessionId: (sessionId) => set({ currentSessionId: sessionId }),

  setLoadingSessions: (loading) => set({ isLoadingSessions: loading }),

  // -- Records --

  setRecords: (recordsList) => {
    const records = new Map<string, InspirationRecordDTO>();
    for (const record of recordsList) {
      records.set(record.id, record);
    }
    set(derivedRecords(records));
  },

  appendRecord: (record) =>
    set((state) => {
      const records = new Map(state.records);
      records.set(record.id, record);
      return derivedRecords(records);
    }),

  upsertRecord: (record) =>
    set((state) => {
      const records = new Map(state.records);
      records.set(record.id, record);
      return derivedRecords(records);
    }),

  updateRecordStatus: (recordId, status, updates) =>
    set((state) => {
      const existing = state.records.get(recordId);
      if (!existing) return state;
      const records = new Map(state.records);
      records.set(recordId, { ...existing, ...updates, status });
      return derivedRecords(records);
    }),

  updateRecordByTaskId: (taskId, updates) =>
    set((state) => {
      const recordId = state.taskRecordMap.get(taskId);
      if (!recordId) return state;
      const existing = state.records.get(recordId);
      if (!existing) return state;
      const records = new Map(state.records);
      records.set(recordId, { ...existing, ...updates });
      return derivedRecords(records);
    }),

  removeRecord: (recordId) =>
    set((state) => {
      if (!state.records.has(recordId)) return state;
      const records = new Map(state.records);
      records.delete(recordId);
      return derivedRecords(records);
    }),

  setLoadingRecords: (loading) => set({ isLoadingRecords: loading }),

  // -- Reset --

  reset: () =>
    set({
      ...INITIAL_STATE,
      records: new Map(),
      recordsList: [],
      taskRecordMap: new Map(),
    }),
}));

export default useInspirationStore;
