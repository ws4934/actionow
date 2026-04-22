"use client";

import { useState, useEffect } from "react";
import { useTranslations, useLocale } from "next-intl";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Button, Spinner, toast} from "@heroui/react";
import {
  Building2,
  Users,
  Shield,
  User,
  CheckCircle,
  XCircle,
  LogIn,
  UserPlus,
  Loader2,
} from "lucide-react";
import {
  workspaceService,
  authService,
  isAuthenticated,
  setAuthBundle,
  resetApiClientSessionState,
  ApiError, getErrorFromException } from "@/lib/api";
import { setWorkspaceId } from "@/lib/stores/workspace-context";
import type { WorkspaceInvitationDTO } from "@/lib/api/dto";

interface InviteContentProps {
  workspaceCode: string;
  personalCode: string;
}

export function InviteContent({ workspaceCode, personalCode }: InviteContentProps) {
  const t = useTranslations("invite");
  const locale = useLocale();
  const router = useRouter();

  const [invitation, setInvitation] = useState<WorkspaceInvitationDTO | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isJoining, setIsJoining] = useState(false);
  const [joinSuccess, setJoinSuccess] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    setIsLoggedIn(isAuthenticated());
  }, []);

  useEffect(() => {
    const loadInvitation = async () => {
      if (!workspaceCode) {
        setError("invitationInvalid");
        setIsLoading(false);
        return;
      }

      try {
        setIsLoading(true);
        setError(null);
        const data = await workspaceService.getInvitationByCode(workspaceCode);
        setInvitation(data);
      } catch (err: unknown) {
        console.error("Failed to load invitation:", err);
        toast.danger(getErrorFromException(err, locale));
        if (err instanceof ApiError) {
          if (err.status === 404) {
            setError("invitationNotFound");
          } else if (err.status === 410) {
            setError("invitationExpired");
          } else {
            setError("invitationInvalid");
          }
        } else {
          setError("invitationInvalid");
        }
      } finally {
        setIsLoading(false);
      }
    };

    loadInvitation();
  }, [workspaceCode]);

  const currentInviteUrl = `/${locale}/invite?workspace_code=${workspaceCode}${personalCode ? `&invite_code=${personalCode}` : ""}`;

  const handleJoin = async () => {
    if (!invitation) return;

    try {
      setIsJoining(true);
      await workspaceService.acceptInvitation(workspaceCode);

      const switchedToken = await authService.switchWorkspace(invitation.workspaceId);
      setAuthBundle(switchedToken);
      setWorkspaceId(invitation.workspaceId);
      resetApiClientSessionState();

      setJoinSuccess(true);
      setTimeout(() => {
        router.push(`/${locale}/workspace/projects`);
      }, 1000);
    } catch (err) {
      console.error("Failed to join workspace:", err);
      toast.danger(getErrorFromException(err, locale));
      setError("joinFailed");
    } finally {
      setIsJoining(false);
    }
  };

  const handleLogin = () => {
    const returnUrl = encodeURIComponent(currentInviteUrl);
    router.push(`/${locale}/login?returnUrl=${returnUrl}`);
  };

  const handleRegister = () => {
    const returnUrl = encodeURIComponent(currentInviteUrl);
    const registerUrl = personalCode
      ? `/${locale}/register?code=${personalCode}&returnUrl=${returnUrl}`
      : `/${locale}/register?returnUrl=${returnUrl}`;
    router.push(registerUrl);
  };

  const getRoleName = (role: string) => {
    switch (role) {
      case "ADMIN":
        return t("roleAdmin");
      case "MEMBER":
        return t("roleMember");
      case "GUEST":
        return t("roleGuest");
      default:
        return role;
    }
  };

  const isExpired = invitation && (invitation.status === 2 || new Date(invitation.expiresAt) < new Date());
  const isDisabled = invitation?.status === 3;
  const isUsedUp = invitation && invitation.usedCount >= invitation.maxUses;
  const isInvalid = isExpired || isDisabled || isUsedUp;

  return (
    <div className="flex flex-col gap-4">
      {isLoading ? (
        <div className="flex flex-col items-center py-8">
          <Loader2 className="size-12 animate-spin text-accent" />
          <p className="mt-4 text-muted">{t("loading")}</p>
        </div>
      ) : error ? (
        <div className="flex flex-col items-center py-6">
          <XCircle className="size-16 text-danger" />
          <h2 className="mt-4 text-xl font-bold text-foreground">
            {t(error)}
          </h2>
          <p className="mt-2 text-center text-sm text-muted">
            {t(`${error}Desc`)}
          </p>
          <Link href={`/${locale}`}>
            <Button variant="secondary" className="mt-6">
              {t("backToHome")}
            </Button>
          </Link>
        </div>
      ) : joinSuccess ? (
        <div className="flex flex-col items-center py-8">
          <CheckCircle className="size-16 text-success" />
          <h2 className="mt-4 text-xl font-bold text-foreground">{t("joinSuccess")}</h2>
          <p className="mt-2 text-center text-muted">{t("redirecting")}</p>
        </div>
      ) : invitation ? (
        <>
          {/* Header */}
          <div className="flex flex-col items-center text-center">
            <div className="flex size-16 items-center justify-center rounded-2xl bg-accent/20">
              <Building2 className="size-8 text-accent" />
            </div>
            <h1 className="mt-4 text-xl font-bold text-foreground">{t("inviteToJoin")}</h1>
            <p className="mt-1 text-sm text-muted">
              {t("invitedAs", { role: getRoleName(invitation.role) })}
            </p>
          </div>

          {/* Invitation Details */}
          <div className="space-y-2.5 rounded-xl bg-background/50 p-4">
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted">{t("inviteCode")}</span>
              <code className="rounded bg-surface px-2 py-0.5 text-sm font-mono">
                {invitation.code}
              </code>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted">{t("role")}</span>
              <div className="flex items-center gap-1.5">
                {invitation.role === "ADMIN" ? (
                  <Shield className="size-4 text-accent" />
                ) : (
                  <User className="size-4 text-muted" />
                )}
                <span className="text-sm font-medium">{getRoleName(invitation.role)}</span>
              </div>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted">{t("remainingSlots")}</span>
              <span className="text-sm font-medium">
                {invitation.maxUses - invitation.usedCount} / {invitation.maxUses}
              </span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-muted">{t("validUntil")}</span>
              <span className="text-sm font-medium">
                {new Date(invitation.expiresAt).toLocaleDateString()}
              </span>
            </div>
          </div>

          {/* Status Warning */}
          {isInvalid && (
            <div className="rounded-lg bg-danger/10 p-3">
              <p className="text-center text-sm text-danger">
                {isExpired && t("invitationExpired")}
                {isDisabled && t("invitationDisabled")}
                {isUsedUp && !isExpired && !isDisabled && t("invitationUsedUp")}
              </p>
            </div>
          )}

          {/* Actions */}
          {!isInvalid && (
            <div className="flex flex-col gap-3">
              {isLoggedIn ? (
                <Button
                  className="button--accent h-11 w-full text-base font-semibold"
                  onPress={handleJoin}
                  isPending={isJoining}
                >
                  {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Users className="mr-2 size-5" />}{t("acceptAndJoin")}</>)}
                </Button>
              ) : (
                <>
                  <Button
                    className="button--accent h-11 w-full text-base font-semibold"
                    onPress={handleLogin}
                  >
                    <LogIn className="mr-2 size-5" />
                    {t("loginToJoin")}
                  </Button>
                  <Button
                    variant="secondary"
                    className="h-11 w-full text-base font-semibold"
                    onPress={handleRegister}
                  >
                    <UserPlus className="mr-2 size-5" />
                    {t("registerNew")}
                  </Button>
                  {personalCode && (
                    <p className="text-center text-xs text-muted">
                      {t("autoFillCode")}
                    </p>
                  )}
                </>
              )}
            </div>
          )}
        </>
      ) : null}
    </div>
  );
}
