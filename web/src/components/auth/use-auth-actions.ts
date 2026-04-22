"use client";

import { useLocale, useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { toast } from "@heroui/react";
import {
  authService,
  oauthService,
  workspaceService,
  resetApiClientSessionState,
  setAuthBundle,
  getErrorFromException,
} from "@/lib/api";
import { getWorkspaceId, setWorkspaceId } from "@/lib/stores/workspace-context";
import { setWorkspacesPrefetch } from "@/lib/stores/session-cache";

export function useAuthActions(returnUrl?: string) {
  const locale = useLocale();
  const router = useRouter();
  const tSuccess = useTranslations("auth.success");

  const autoSwitchWorkspace = async (
    userId: string,
    currentWorkspaceId: string | null,
    successKey: "loginSuccess" | "registerSuccess"
  ) => {
    const workspaces = await workspaceService.getWorkspaces();
    setWorkspacesPrefetch(workspaces);
    if (workspaces.length === 0) {
      toast.success(tSuccess(successKey));
      router.push(returnUrl || `/${locale}/workspace`);
      return;
    }

    const cachedWorkspaceId = getWorkspaceId();
    const targetWorkspaceId = workspaces.some((w) => w.id === cachedWorkspaceId)
      ? cachedWorkspaceId!
      : workspaces[0].id;

    if (currentWorkspaceId && currentWorkspaceId === targetWorkspaceId) {
      setWorkspaceId(targetWorkspaceId);
      toast.success(tSuccess(successKey));
      router.push(returnUrl || `/${locale}/workspace/projects`);
      return;
    }

    const switchedToken = await authService.switchWorkspace(targetWorkspaceId);
    setAuthBundle(switchedToken, userId);
    setWorkspaceId(targetWorkspaceId);
    resetApiClientSessionState();
    toast.success(tSuccess(successKey));
    router.push(returnUrl || `/${locale}/workspace/projects`);
  };

  const handleOAuthLogin = async (provider: string) => {
    try {
      const redirectUri = `${window.location.origin}/${locale}/oauth/callback?provider=${encodeURIComponent(provider)}`;
      const response = await oauthService.getAuthorizeUrl(provider, redirectUri);
      window.location.href = response.authorizeUrl;
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    }
  };

  return { autoSwitchWorkspace, handleOAuthLogin };
}
