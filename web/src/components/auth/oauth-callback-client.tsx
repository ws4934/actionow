"use client";

import { useEffect, useRef, useState } from "react";
import { useTranslations, useLocale } from "next-intl";
import { useRouter } from "next/navigation";
import { Button, Spinner, toast } from "@heroui/react";
import { AlertCircle } from "lucide-react";
import {
  oauthService,
  setAuthBundle,
  getErrorFromException,
} from "@/lib/api";
import { handleLoginCacheTransition } from "@/lib/stores/session-cache";
import { useAuthActions } from "./use-auth-actions";

interface OAuthCallbackClientProps {
  code?: string;
  state?: string;
  provider?: string;
  error?: string;
}

export function OAuthCallbackClient({
  code,
  state,
  provider,
  error: oauthError,
}: OAuthCallbackClientProps) {
  const t = useTranslations("auth.oauth");
  const locale = useLocale();
  const router = useRouter();
  const { autoSwitchWorkspace } = useAuthActions();
  const [error, setError] = useState<string | null>(oauthError || null);
  const calledRef = useRef(false);

  useEffect(() => {
    if (calledRef.current) return;
    calledRef.current = true;

    if (oauthError) {
      setError(oauthError);
      return;
    }

    if (!code || !provider) {
      setError(t("missingParams"));
      return;
    }

    const redirectUri = `${window.location.origin}/${locale}/oauth/callback?provider=${encodeURIComponent(provider)}`;

    oauthService
      .handleCallback(provider, {
        code,
        state,
        redirectUri,
      })
      .then(async (response) => {
        const { token, user } = response;
        handleLoginCacheTransition(user.id);
        setAuthBundle(token, user.id);
        const successKey = response.isNewUser ? "registerSuccess" : "loginSuccess";
        await autoSwitchWorkspace(user.id, token.workspaceId, successKey);
      })
      .catch((err) => {
        setError(getErrorFromException(err, locale));
      });
  }, [code, state, provider, oauthError, locale, t, autoSwitchWorkspace]);

  if (error) {
    return (
      <div className="flex flex-col items-center gap-4 py-8 text-center">
        <AlertCircle className="size-12 text-danger" />
        <div className="flex flex-col gap-1">
          <h2 className="text-lg font-semibold text-foreground">{t("callbackError")}</h2>
          <p className="text-sm text-muted">{error}</p>
        </div>
        <Button
          variant="secondary"
          onPress={() => router.push(`/${locale}/login`)}
        >
          {t("backToLogin")}
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-4 py-8">
      <Spinner size="lg" />
      <p className="text-sm text-muted">{t("processing")}</p>
    </div>
  );
}
