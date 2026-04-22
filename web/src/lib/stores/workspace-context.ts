/**
 * Workspace Context Store
 * Manages workspace and tenant context using Zustand + persist
 */

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { createPersistStorage } from "./persist-storage";

interface WorkspaceContextState {
  workspaceId: string | null;
  tenantSchema: string | null;
  userRole: string | null;
  setWorkspaceId: (workspaceId: string | null) => void;
  setTenantSchema: (tenantSchema: string | null) => void;
  setUserRole: (role: string | null) => void;
  setWorkspaceContext: (workspaceId: string, tenantSchema?: string, userRole?: string) => void;
  clearWorkspaceContext: () => void;
}

export const useWorkspaceContextStore = create<WorkspaceContextState>()(
  persist(
    (set) => ({
      workspaceId: null,
      tenantSchema: null,
      userRole: null,
      setWorkspaceId: (workspaceId) => set({ workspaceId }),
      setTenantSchema: (tenantSchema) => set({ tenantSchema }),
      setUserRole: (userRole) => set({ userRole }),
      setWorkspaceContext: (workspaceId, tenantSchema, userRole) =>
        set({
          workspaceId,
          tenantSchema: tenantSchema ?? null,
          userRole: userRole ?? null,
        }),
      clearWorkspaceContext: () =>
        set({
          workspaceId: null,
          tenantSchema: null,
          userRole: null,
        }),
    }),
    {
      name: "actionow_workspace_context_store",
      storage: createPersistStorage<
        Pick<WorkspaceContextState, "workspaceId" | "tenantSchema" | "userRole">
      >(),
      partialize: (state) => ({
        workspaceId: state.workspaceId,
        tenantSchema: state.tenantSchema,
        userRole: state.userRole,
      }),
    }
  )
);

export function getWorkspaceId(): string | null {
  return useWorkspaceContextStore.getState().workspaceId;
}

export function getTenantSchema(): string | null {
  return useWorkspaceContextStore.getState().tenantSchema;
}

export function getUserRole(): string | null {
  return useWorkspaceContextStore.getState().userRole;
}

export function setWorkspaceId(workspaceId: string): void {
  useWorkspaceContextStore.getState().setWorkspaceId(workspaceId);
}

export function setTenantSchema(tenantSchema: string): void {
  useWorkspaceContextStore.getState().setTenantSchema(tenantSchema);
}

export function setUserRole(role: string): void {
  useWorkspaceContextStore.getState().setUserRole(role);
}

export function setWorkspaceContext(
  workspaceId: string,
  tenantSchema?: string,
  userRole?: string
): void {
  useWorkspaceContextStore.getState().setWorkspaceContext(workspaceId, tenantSchema, userRole);
}

export function clearWorkspaceContext(): void {
  useWorkspaceContextStore.getState().clearWorkspaceContext();
}
