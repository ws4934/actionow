"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useRouter, usePathname } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { toast } from "@heroui/react";
import { WorkspaceHeader, CreateWorkspaceModal } from "@/components/workspace";
import { WorkspaceProvider, useWorkspace } from "@/components/providers/workspace-provider";
import { AIGenerationProvider } from "@/components/providers/ai-generation-provider";
import { WebSocketProvider } from "@/lib/websocket";
import { TaskNotificationManager } from "@/components/workspace/task-center/task-notification-manager";
import { OnboardingModal } from "@/components/onboarding/onboarding-modal";
import { useOnboardingStore } from "@/lib/stores/onboarding-store";
import {
  workspaceService,
  authService,
  ApiError,
  ERROR_CODES,
  setAuthBundle,
  resetApiClientSessionState,
  beginWorkspaceSwitchTransition,
  endWorkspaceSwitchTransition,
  getErrorFromException,
} from "@/lib/api";
import { useAuthStore } from "@/lib/stores/auth-store";
import { getWorkspaceId as getCachedWorkspaceId, setWorkspaceId as setCachedWorkspaceId } from "@/lib/stores/workspace-context";
import { clearSessionCaches, consumeWorkspacesPrefetch } from "@/lib/stores/session-cache";
import type { WorkspaceDTO } from "@/lib/api/dto";

function WorkspaceLayoutContent({ children }: { children: React.ReactNode }) {
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const tWorkspace = useTranslations("workspace.errors");
  const { workspaces, currentWorkspaceId, setWorkspaces, isLoading } = useWorkspace();
  const isLoggedIn = useAuthStore((state) => !!state.tokenBundle?.accessToken);
  const tokenWorkspaceId = useAuthStore((state) => state.tokenBundle?.workspaceId ?? null);

  const [isAuthHydrated, setIsAuthHydrated] = useState(() =>
    useAuthStore.persist.hasHydrated()
  );
  const [isLoadingWorkspaces, setIsLoadingWorkspaces] = useState(true);
  const [isSwitchingWorkspace, setIsSwitchingWorkspace] = useState(false);
  const [workspaceSelectionRequired, setWorkspaceSelectionRequired] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [hasNoWorkspaces, setHasNoWorkspaces] = useState(false);
  const workspacesLoadPromiseRef = useRef<Promise<void> | null>(null);
  const lastWorkspacesLoadAtRef = useRef<number>(0);
  const activeSwitchWorkspaceIdRef = useRef<string | null>(null);
  const autoSwitchAttemptKeyRef = useRef<string | null>(null);
  const ignoreWorkspaceMismatchUntilRef = useRef<number>(0);
  const isSwitchingWorkspaceRef = useRef(false);

  useEffect(() => {
    isSwitchingWorkspaceRef.current = isSwitchingWorkspace;
  }, [isSwitchingWorkspace]);

  useEffect(() => {
    const unsubscribeHydrateStart = useAuthStore.persist.onHydrate(() => {
      setIsAuthHydrated(false);
    });
    const unsubscribeHydrateEnd = useAuthStore.persist.onFinishHydration(() => {
      setIsAuthHydrated(true);
    });

    setIsAuthHydrated(useAuthStore.persist.hasHydrated());

    return () => {
      unsubscribeHydrateStart();
      unsubscribeHydrateEnd();
    };
  }, []);

  useEffect(() => {
    if (!isAuthHydrated) return;
    if (isLoggedIn) return;

    const hasStoredAuth = localStorage.getItem("actionow_auth_store") !== null;
    if (!hasStoredAuth) {
      router.replace(`/${locale}/login`);
      return;
    }

    const redirectTimer = setTimeout(() => {
      const latestAccessToken = useAuthStore.getState().tokenBundle?.accessToken;
      if (!latestAccessToken) {
        router.replace(`/${locale}/login`);
      }
    }, 700);

    return () => {
      clearTimeout(redirectTimer);
    };
  }, [isAuthHydrated, isLoggedIn, locale, router]);

  useEffect(() => {
    const handleWorkspaceAuthMismatch = () => {
      if (isSwitchingWorkspaceRef.current) return;
      if (Date.now() < ignoreWorkspaceMismatchUntilRef.current) return;
      setWorkspaceSelectionRequired(true);
    };

    window.addEventListener("workspaceAuthMismatch", handleWorkspaceAuthMismatch);
    return () => {
      window.removeEventListener("workspaceAuthMismatch", handleWorkspaceAuthMismatch);
    };
  }, []);

  const loadWorkspaces = useCallback(async () => {
    const now = Date.now();
    if (workspacesLoadPromiseRef.current) {
      await workspacesLoadPromiseRef.current;
      return;
    }
    if (now - lastWorkspacesLoadAtRef.current < 1200) {
      setIsLoadingWorkspaces(false);
      return;
    }

    const loadPromise = (async () => {
      try {
        const prefetched = consumeWorkspacesPrefetch<WorkspaceDTO>();
        const data = prefetched ?? await workspaceService.getWorkspaces();
        setWorkspaces(data);

        if (data.length === 0) {
          setHasNoWorkspaces(true);
          const onboardingCompleted = useOnboardingStore.getState().hasCompletedOnboarding;
          if (onboardingCompleted) {
            setShowCreateModal(true);
          } else {
            useOnboardingStore.getState().setNeedsWorkspace(true);
          }
        } else {
          setHasNoWorkspaces(false);
        }
      } catch (error) {
        if (
          error instanceof ApiError &&
          (String(error.code) === String(ERROR_CODES.RESOURCE_NOT_FOUND) ||
            String(error.code) === String(ERROR_CODES.NOT_FOUND))
        ) {
          setWorkspaces([]);
          setHasNoWorkspaces(true);
          const onboardingCompleted = useOnboardingStore.getState().hasCompletedOnboarding;
          if (onboardingCompleted) {
            setShowCreateModal(true);
          } else {
            useOnboardingStore.getState().setNeedsWorkspace(true);
          }
        } else {
          console.error("Failed to load workspaces:", error);
          toast.danger(getErrorFromException(error, locale));
        }
      } finally {
        lastWorkspacesLoadAtRef.current = Date.now();
        setIsLoadingWorkspaces(false);
      }
    })();

    workspacesLoadPromiseRef.current = loadPromise;
    try {
      await loadPromise;
    } finally {
      workspacesLoadPromiseRef.current = null;
    }
  }, [setWorkspaces]);

  useEffect(() => {
    if (!isAuthHydrated || !isLoggedIn) return;
    loadWorkspaces();
  }, [isAuthHydrated, isLoggedIn, loadWorkspaces]);

  const handleWorkspaceChange = useCallback(async (
    workspaceId: string,
    options?: { force?: boolean }
  ): Promise<boolean> => {
    if (!workspaceId || (!options?.force && workspaceId === tokenWorkspaceId)) {
      return true;
    }
    if (activeSwitchWorkspaceIdRef.current) {
      return false;
    }

    try {
      activeSwitchWorkspaceIdRef.current = workspaceId;
      ignoreWorkspaceMismatchUntilRef.current = Date.now() + 2000;
      setIsSwitchingWorkspace(true);
      beginWorkspaceSwitchTransition();
      const tokenBundle = await authService.switchWorkspace(workspaceId);

      // Atomic token replacement after switch.
      setAuthBundle(tokenBundle);

      // Reset in-memory queues and clear business/session caches.
      resetApiClientSessionState();
      clearSessionCaches();
      setCachedWorkspaceId(workspaceId);
      setWorkspaceSelectionRequired(false);
      autoSwitchAttemptKeyRef.current = null;
      ignoreWorkspaceMismatchUntilRef.current = Date.now() + 2000;

      const isOnStudioPage = /\/workspace\/projects\/[^/]+$/.test(pathname);
      if (isOnStudioPage) {
        router.push(`/${locale}/workspace/projects`);
      } else {
        router.refresh();
      }
      return true;
    } catch (error) {
      const code = error instanceof ApiError ? String(error.code) : "";
      const authError = error instanceof ApiError ? (error.authError || "") : "";

      if (
        code === ERROR_CODES.WORKSPACE_NOT_MEMBER ||
        code === ERROR_CODES.WORKSPACE_NO_PERMISSION ||
        authError.toLowerCase().includes("workspace")
      ) {
        setWorkspaceSelectionRequired(true);
        toast.danger(tWorkspace("switchPermissionDenied"));
      } else {
        toast.danger(error instanceof Error ? error.message : tWorkspace("switchFailed"));
      }
      return false;
    } finally {
      activeSwitchWorkspaceIdRef.current = null;
      endWorkspaceSwitchTransition();
      setIsSwitchingWorkspace(false);
    }
  }, [locale, pathname, router, tokenWorkspaceId, tWorkspace]);

  useEffect(() => {
    if (!isAuthHydrated || isLoading || isLoadingWorkspaces || isSwitchingWorkspace) return;
    if (workspaces.length === 0) return;
    if (tokenWorkspaceId && !workspaceSelectionRequired) return;

    const cachedWorkspaceId = getCachedWorkspaceId();
    const preferredWorkspaceId = workspaces.find((workspace) => workspace.id === cachedWorkspaceId)?.id
      ?? (workspaceSelectionRequired && tokenWorkspaceId
        ? (workspaces.find((workspace) => workspace.id !== tokenWorkspaceId)?.id ?? workspaces[0].id)
        : workspaces[0].id);

    const autoSwitchKey = `${workspaceSelectionRequired ? "mismatch" : "unbound"}:${tokenWorkspaceId ?? "null"}:${preferredWorkspaceId}:${workspaces.map((workspace) => workspace.id).join(",")}`;
    if (autoSwitchAttemptKeyRef.current === autoSwitchKey) {
      return;
    }
    autoSwitchAttemptKeyRef.current = autoSwitchKey;

    void handleWorkspaceChange(preferredWorkspaceId, {
      force: workspaceSelectionRequired && preferredWorkspaceId === tokenWorkspaceId,
    });
  }, [
    handleWorkspaceChange,
    isAuthHydrated,
    isLoading,
    isLoadingWorkspaces,
    isSwitchingWorkspace,
    tokenWorkspaceId,
    workspaceSelectionRequired,
    workspaces,
  ]);

  const handleCreateWorkspace = useCallback(() => {
    setShowCreateModal(true);
  }, []);

  const handleWorkspaceCreated = useCallback((workspace: WorkspaceDTO) => {
    setWorkspaces([...workspaces, workspace]);
    setHasNoWorkspaces(false);
    setShowCreateModal(false);
    void handleWorkspaceChange(workspace.id);
  }, [handleWorkspaceChange, setWorkspaces, workspaces]);

  if (!isAuthHydrated || isLoading || isLoadingWorkspaces) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="size-8 animate-spin rounded-full border-4 border-accent border-t-transparent" />
      </div>
    );
  }

  const shouldBlockWorkspaceRoutes =
    isSwitchingWorkspace || !tokenWorkspaceId || workspaceSelectionRequired;

  return (
    <AIGenerationProvider>
      <WebSocketProvider workspaceId={currentWorkspaceId}>
        <div className="flex h-screen flex-col bg-background">
          <WorkspaceHeader
            workspaces={workspaces}
            currentWorkspaceId={currentWorkspaceId}
            onWorkspaceChange={(workspaceId) => {
              void handleWorkspaceChange(workspaceId);
            }}
            onCreateWorkspace={handleCreateWorkspace}
          />

          {shouldBlockWorkspaceRoutes ? (
            <main className="flex min-h-0 flex-1 items-center justify-center">
              <div className="size-8 animate-spin rounded-full border-4 border-accent border-t-transparent" />
            </main>
          ) : (
            <main className="min-h-0 flex-1">{children}</main>
          )}

          <CreateWorkspaceModal
            isOpen={showCreateModal}
            onOpenChange={setShowCreateModal}
            onSuccess={handleWorkspaceCreated}
            isDismissable={!hasNoWorkspaces}
          />
          <TaskNotificationManager />
          <OnboardingModal onWorkspaceCreated={handleWorkspaceCreated} />
        </div>
      </WebSocketProvider>
    </AIGenerationProvider>
  );
}

export default function WorkspaceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <WorkspaceProvider>
      <WorkspaceLayoutContent>{children}</WorkspaceLayoutContent>
    </WorkspaceProvider>
  );
}
