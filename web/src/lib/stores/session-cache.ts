/**
 * Session cache management
 * Clears user/session-scoped localStorage entries when account changes or logout happens.
 */

import { getUserId } from "./auth-store";
import { clearWorkspaceContext } from "./workspace-context";
import { clearPreferences as clearUiPreferences, clearScriptTabs } from "./preferences-store";
import { clearAllActiveScripts } from "./script-store";
import { aiGenerationCache } from "./ai-generation-cache";
import { useTaskStore } from "./task-store";

const WORKSPACES_PREFETCH_KEY = "actionow_workspaces_prefetch";
const WORKSPACES_PREFETCH_TTL_MS = 5000;

export function setWorkspacesPrefetch(workspaces: unknown[]): void {
  if (typeof window === "undefined") return;
  sessionStorage.setItem(
    WORKSPACES_PREFETCH_KEY,
    JSON.stringify({ data: workspaces, ts: Date.now() })
  );
}

export function consumeWorkspacesPrefetch<T = unknown>(): T[] | null {
  if (typeof window === "undefined") return null;
  const raw = sessionStorage.getItem(WORKSPACES_PREFETCH_KEY);
  sessionStorage.removeItem(WORKSPACES_PREFETCH_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as { data: T[]; ts: number };
    if (Date.now() - parsed.ts > WORKSPACES_PREFETCH_TTL_MS) return null;
    return parsed.data;
  } catch {
    return null;
  }
}

export interface ClearSessionCachesOptions {
  clearPreferences?: boolean;
}

/**
 * Clear session-scoped caches, but keep global preferences by default.
 */
export function clearSessionCaches(options: ClearSessionCachesOptions = {}): void {
  if (typeof window === "undefined") return;

  const { clearPreferences = false } = options;

  clearWorkspaceContext();
  clearAllActiveScripts();
  clearScriptTabs();
  aiGenerationCache.clear();
  useTaskStore.getState().reset();
  sessionStorage.removeItem(WORKSPACES_PREFETCH_KEY);

  if (clearPreferences) {
    clearUiPreferences();
  }
}

/**
 * On login/register success, clear session caches only when switching to a different account.
 */
export function handleLoginCacheTransition(
  newUserId: string,
  options: ClearSessionCachesOptions = {}
): void {
  if (typeof window === "undefined") return;

  const previousUserId = getUserId();
  if (previousUserId && previousUserId !== newUserId) {
    clearSessionCaches(options);
  }
}
