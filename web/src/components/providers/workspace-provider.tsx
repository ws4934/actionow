"use client";

import {
  createContext,
  useContext,
  useState,
  useMemo,
  useCallback,
  type ReactNode,
} from "react";
import type { WorkspaceDTO } from "@/lib/api/dto";
import { useAuthStore } from "@/lib/stores/auth-store";

interface WorkspaceContextValue {
  workspaces: WorkspaceDTO[];
  currentWorkspace: WorkspaceDTO | null;
  currentWorkspaceId: string | null;
  setWorkspaces: (workspaces: WorkspaceDTO[]) => void;
  setCurrentWorkspaceId: (_id: string) => void;
  isLoading: boolean;
}

const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const tokenWorkspaceId = useAuthStore((state) => state.tokenBundle?.workspaceId ?? null);
  const [workspaces, setWorkspacesState] = useState<WorkspaceDTO[]>([]);
  const isLoading = false;

  const setWorkspaces = useCallback((nextWorkspaces: WorkspaceDTO[]) => {
    setWorkspacesState(nextWorkspaces);
  }, []);

  // Workspace is determined by token claim only.
  const setCurrentWorkspaceId = useCallback(() => {
    // No-op by design in high-security mode.
  }, []);

  const currentWorkspace = useMemo(
    () => workspaces.find((w) => w.id === tokenWorkspaceId) || null,
    [workspaces, tokenWorkspaceId]
  );

  const contextValue = useMemo(
    () => ({
      workspaces,
      currentWorkspace,
      currentWorkspaceId: tokenWorkspaceId,
      setWorkspaces,
      setCurrentWorkspaceId,
      isLoading,
    }),
    [workspaces, currentWorkspace, tokenWorkspaceId, setWorkspaces, setCurrentWorkspaceId, isLoading]
  );

  return (
    <WorkspaceContext.Provider value={contextValue}>
      {children}
    </WorkspaceContext.Provider>
  );
}

export function useWorkspace() {
  const context = useContext(WorkspaceContext);
  if (!context) {
    throw new Error("useWorkspace must be used within a WorkspaceProvider");
  }
  return context;
}
