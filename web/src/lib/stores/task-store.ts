/**
 * Task Store
 * Normalized Zustand store shape:
 * - state: data only
 * - actions: mutations only
 * - selectors: derived read helpers
 */

import { create } from "zustand";
import type { TaskListItemDTO, TaskStatsSummaryDTO } from "@/lib/api/dto/task.dto";

// ============================================================================
// Types
// ============================================================================

export interface TaskStoreState {
  activeTasks: Map<string, TaskListItemDTO>;
  /** Pre-computed sorted list — avoids creating new arrays in selectors */
  activeTasksList: TaskListItemDTO[];
  runningCount: number;
  isInitialized: boolean;
  statsSummary: TaskStatsSummaryDTO | null;
}

export interface TaskStoreActions {
  setActiveTasks: (tasks: TaskListItemDTO[]) => void;
  upsertTask: (task: TaskListItemDTO) => void;
  updateTaskProgress: (taskId: string, progress: number) => void;
  completeTask: (taskId: string) => void;
  failTask: (taskId: string, errorMessage?: string) => void;
  removeTask: (taskId: string) => void;
  cancelTask: (taskId: string) => void;
  setStatsSummary: (stats: TaskStatsSummaryDTO | null) => void;
  setInitialized: (initialized: boolean) => void;
  reset: () => void;
}

export type TaskStore = TaskStoreState & TaskStoreActions;

// ============================================================================
// Constants
// ============================================================================

const INITIAL_STATE: TaskStoreState = {
  activeTasks: new Map(),
  activeTasksList: [],
  runningCount: 0,
  isInitialized: false,
  statsSummary: null,
};

// ============================================================================
// Helpers
// ============================================================================

function countRunning(tasks: Map<string, TaskListItemDTO>): number {
  let count = 0;
  tasks.forEach((task) => {
    if (task.status === "RUNNING") count++;
  });
  return count;
}

function isActive(status: string): boolean {
  return status === "PENDING" || status === "QUEUED" || status === "RUNNING";
}

function deriveList(tasks: Map<string, TaskListItemDTO>): TaskListItemDTO[] {
  return Array.from(tasks.values()).sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );
}

/** Build the derived fields from a tasks map */
function derived(tasks: Map<string, TaskListItemDTO>) {
  return {
    activeTasks: tasks,
    activeTasksList: deriveList(tasks),
    runningCount: countRunning(tasks),
  };
}

// ============================================================================
// Selectors (read-only helpers)
// ============================================================================

export const taskSelectors = {
  activeTasks: (state: TaskStore) => state.activeTasks,
  activeTasksList: (state: TaskStore) => state.activeTasksList,
  runningCount: (state: TaskStore) => state.runningCount,
  isInitialized: (state: TaskStore) => state.isInitialized,
  statsSummary: (state: TaskStore) => state.statsSummary,
  activeCount: (state: TaskStore) => state.activeTasksList.length,
  hasActiveTasks: (state: TaskStore) => state.activeTasksList.length > 0,
};

// ============================================================================
// Store
// ============================================================================

export const useTaskStore = create<TaskStore>((set) => ({
  ...INITIAL_STATE,

  setActiveTasks: (tasks) => {
    const map = new Map<string, TaskListItemDTO>();
    for (const task of tasks) {
      if (isActive(task.status)) {
        map.set(task.id, task);
      }
    }
    set(derived(map));
  },

  upsertTask: (task) =>
    set((state) => {
      const activeTasks = new Map(state.activeTasks);
      if (isActive(task.status)) {
        activeTasks.set(task.id, task);
      } else {
        activeTasks.delete(task.id);
      }
      return derived(activeTasks);
    }),

  updateTaskProgress: (taskId, progress) =>
    set((state) => {
      const existing = state.activeTasks.get(taskId);
      if (!existing) return state;
      const activeTasks = new Map(state.activeTasks);
      activeTasks.set(taskId, {
        ...existing,
        progress,
        status: "RUNNING",
      });
      return derived(activeTasks);
    }),

  completeTask: (taskId) =>
    set((state) => {
      const activeTasks = new Map(state.activeTasks);
      activeTasks.delete(taskId);
      return derived(activeTasks);
    }),

  failTask: (taskId, errorMessage) =>
    set((state) => {
      const existing = state.activeTasks.get(taskId);
      if (!existing) return state;
      const activeTasks = new Map(state.activeTasks);
      activeTasks.delete(taskId);
      // Keep failed task briefly visible, then remove
      if (existing) {
        activeTasks.set(taskId, {
          ...existing,
          status: "FAILED",
          errorMessage: errorMessage ?? existing.errorMessage,
        });
        // Auto-remove after 10s
        setTimeout(() => {
          useTaskStore.getState().removeTask(taskId);
        }, 10000);
      }
      return derived(activeTasks);
    }),

  removeTask: (taskId) =>
    set((state) => {
      if (!state.activeTasks.has(taskId)) return state;
      const activeTasks = new Map(state.activeTasks);
      activeTasks.delete(taskId);
      return derived(activeTasks);
    }),

  cancelTask: (taskId) =>
    set((state) => {
      const activeTasks = new Map(state.activeTasks);
      const existing = activeTasks.get(taskId);
      if (existing) {
        activeTasks.set(taskId, { ...existing, status: "CANCELLED" });
        // Auto-remove after 3s
        setTimeout(() => {
          useTaskStore.getState().removeTask(taskId);
        }, 3000);
      }
      return derived(activeTasks);
    }),

  setStatsSummary: (stats) => set({ statsSummary: stats }),

  setInitialized: (initialized) => set({ isInitialized: initialized }),

  reset: () => set({ ...INITIAL_STATE, activeTasks: new Map(), activeTasksList: [] }),
}));

// ============================================================================
// Convenience Hooks
// ============================================================================

export function useTaskState() {
  const activeTasksList = useTaskStore(taskSelectors.activeTasksList);
  const runningCount = useTaskStore(taskSelectors.runningCount);
  const hasActiveTasks = useTaskStore(taskSelectors.hasActiveTasks);
  const activeCount = useTaskStore(taskSelectors.activeCount);
  const statsSummary = useTaskStore(taskSelectors.statsSummary);
  const isInitialized = useTaskStore(taskSelectors.isInitialized);

  return {
    activeTasksList,
    runningCount,
    hasActiveTasks,
    activeCount,
    statsSummary,
    isInitialized,
  };
}

export function useTaskActions() {
  const setActiveTasks = useTaskStore((state) => state.setActiveTasks);
  const upsertTask = useTaskStore((state) => state.upsertTask);
  const updateTaskProgress = useTaskStore((state) => state.updateTaskProgress);
  const completeTask = useTaskStore((state) => state.completeTask);
  const failTask = useTaskStore((state) => state.failTask);
  const removeTask = useTaskStore((state) => state.removeTask);
  const cancelTask = useTaskStore((state) => state.cancelTask);
  const setStatsSummary = useTaskStore((state) => state.setStatsSummary);
  const setInitialized = useTaskStore((state) => state.setInitialized);
  const reset = useTaskStore((state) => state.reset);

  return {
    setActiveTasks,
    upsertTask,
    updateTaskProgress,
    completeTask,
    failTask,
    removeTask,
    cancelTask,
    setStatsSummary,
    setInitialized,
    reset,
  };
}

export default useTaskStore;
