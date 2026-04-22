"use client";

import { useState, useEffect, useRef } from "react";
import { useTranslations, useLocale } from "next-intl";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Button,
  Form,
  Input,
  Label,
  TextField,
  Tabs,
  Separator,
  InputOTP,
  Spinner,
  toast,
} from "@heroui/react";
import { Eye, EyeOff, Mail, Lock } from "lucide-react";
import {
  authService,
  setAuthBundle,
  getErrorFromException,
} from "@/lib/api";
import { handleLoginCacheTransition } from "@/lib/stores/session-cache";
import { useAuthActions } from "./use-auth-actions";
import { useLoginConfig, getOAuthIcon } from "./use-login-config";

import { ThemeSwitcher } from "@/components/ui/theme-switcher";
import { LanguageSwitcher } from "@/components/ui/language-switcher";
import { CaptchaInput, CaptchaInputRef } from "./captcha-input";

interface LoginFormProps {
  returnUrl?: string;
}

export function LoginForm({ returnUrl }: LoginFormProps) {
  const t = useTranslations("auth.login");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("auth.errors");
  const tSuccess = useTranslations("auth.success");
  const tCaptcha = useTranslations("auth.captcha");
  const locale = useLocale();
  const router = useRouter();
  const { autoSwitchWorkspace, handleOAuthLogin } = useAuthActions(returnUrl);

  // Login config
  const loginConfig = useLoginConfig();
  const hasPassword = loginConfig?.passwordLoginEnabled ?? true;
  const hasCode = loginConfig?.codeLoginEnabled ?? true;
  const oauthProviders = loginConfig?.oauthProviders ?? [];
  const hasOAuth = oauthProviders.length > 0;
  const hasBothTabs = hasPassword && hasCode;

  const [loginType, setLoginType] = useState<"password" | "code">("password");
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSendingCode, setIsSendingCode] = useState(false);

  // Form state
  const [account, setAccount] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");
  const [verifyCode, setVerifyCode] = useState("");

  // Captcha state
  const [captchaValue, setCaptchaValue] = useState("");
  const [captchaKey, setCaptchaKey] = useState("");
  const captchaRef = useRef<CaptchaInputRef>(null);

  // Countdown for verify code
  const [countdown, setCountdown] = useState(0);

  // Auto-select login type based on config
  useEffect(() => {
    if (loginConfig) {
      if (loginType === "password" && !hasPassword && hasCode) {
        setLoginType("code");
      } else if (loginType === "code" && !hasCode && hasPassword) {
        setLoginType("password");
      }
    }
  }, [loginConfig, hasPassword, hasCode, loginType]);

  const handleSendCode = async () => {
    if (countdown > 0 || !email || isSendingCode) return;

    // Validate captcha
    if (!captchaValue || captchaValue.length < 4) {
      toast.danger(tCaptcha("required"));
      return;
    }

    setIsSendingCode(true);
    try {
      await authService.sendVerifyCode({
        target: email,
        type: "login",
        captcha: captchaValue,
        captchaKey: captchaKey,
      });

      toast.success(tSuccess("codeSent"));
      setCountdown(60);
      setCaptchaValue("");
      captchaRef.current?.refresh();
      const interval = setInterval(() => {
        setCountdown((prev) => {
          if (prev <= 1) {
            clearInterval(interval);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } catch (error) {
      // Refresh captcha on error
      captchaRef.current?.refresh();
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsSendingCode(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Basic client-side validation for code login
    if (loginType === "code") {
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        toast.danger(tErrors("invalidEmail"));
        return;
      }
      if (!/^\d{6}$/.test(verifyCode)) {
        toast.danger(tErrors("invalidVerifyCode"));
        return;
      }
    }

    setIsLoading(true);

    try {
      let response;

      if (loginType === "password") {
        response = await authService.login({
          account,
          password,
        });
      } else {
        response = await authService.loginWithCode({
          target: email,
          verifyCode,
        });
      }

      const { token, user } = response;
      handleLoginCacheTransition(user.id);
      setAuthBundle(token, user.id);
      await autoSwitchWorkspace(user.id, token.workspaceId, "loginSuccess");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsLoading(false);
    }
  };

  // --- Form content blocks ---

  const passwordFormContent = (
    <Form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <TextField name="account" isRequired className="w-full">
        <Label>{t("account")}</Label>
        <div className="relative w-full">
          <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
          <Input
            placeholder={t("accountPlaceholder")}
            value={account}
            onChange={(e) => setAccount(e.target.value)}
            className="w-full pl-10"
          />
        </div>
      </TextField>

      <TextField name="password" type={showPassword ? "text" : "password"} isRequired className="w-full">
        <Label>{t("password")}</Label>
        <div className="relative w-full">
          <Lock className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
          <Input
            placeholder={t("passwordPlaceholder")}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full pl-10 pr-10"
          />
          <Button
            type="button"
            variant="ghost"
            isIconOnly
            size="sm"
            onPress={() => setShowPassword(!showPassword)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted hover:text-foreground"
          >
            {showPassword ? (
              <EyeOff className="size-4" />
            ) : (
              <Eye className="size-4" />
            )}
          </Button>
        </div>
      </TextField>

      <Button
        type="submit"
        isPending={isLoading}
        className="button--accent mt-2 h-11 w-full text-base font-semibold"
      >
        {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("submit")}</>)}
      </Button>
    </Form>
  );

  const codeFormContent = (
    <Form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <TextField name="email" isRequired className="w-full">
        <Label>{t("email")}</Label>
        <div className="relative w-full">
          <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
          <Input
            placeholder={t("emailPlaceholder")}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full pl-10"
          />
        </div>
      </TextField>

      {/* Captcha Input */}
      <div className="flex w-full flex-col gap-2">
        <Label isRequired>{tCaptcha("label")}</Label>
        <CaptchaInput
          ref={captchaRef}
          value={captchaValue}
          onChange={setCaptchaValue}
          onCaptchaKeyChange={setCaptchaKey}
        />
      </div>

      <div className="flex w-full flex-col gap-2">
        <Label isRequired>{t("verifyCode")}</Label>
        <div className="flex w-full items-center justify-between gap-3">
          <InputOTP
            maxLength={6}
            value={verifyCode}
            onChange={setVerifyCode}
          >
            <InputOTP.Group>
              <InputOTP.Slot index={0} />
              <InputOTP.Slot index={1} />
              <InputOTP.Slot index={2} />
            </InputOTP.Group>
            <InputOTP.Separator />
            <InputOTP.Group>
              <InputOTP.Slot index={3} />
              <InputOTP.Slot index={4} />
              <InputOTP.Slot index={5} />
            </InputOTP.Group>
          </InputOTP>
          <Button
            type="button"
            variant="secondary"
            onPress={handleSendCode}
            isDisabled={countdown > 0 || !email || !captchaValue}
            isPending={isSendingCode}
            className="h-10 shrink-0"
          >
            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{countdown > 0 ? `${countdown}s` : t("sendCode")}</>)}
          </Button>
        </div>
      </div>

      <Button
        type="submit"
        isPending={isLoading}
        className="button--accent mt-2 h-11 w-full text-base font-semibold"
      >
        {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("submit")}</>)}
      </Button>
    </Form>
  );

  return (
    <div className="flex flex-col gap-3">
      {/* Header */}
      <div className="w-full flex items-center justify-between">
        <div className="flex flex-col">
          <h1 className="text-2xl font-bold text-foreground">{t("title")}</h1>
          <p className="mt-1 text-sm text-muted">{t("subtitle")}</p>
        </div>
        {/* Bottom Controls */}
        <div className="flex items-center gap-4">
          <LanguageSwitcher />
          <ThemeSwitcher />
        </div>
      </div>

      {/* Login Forms */}
      {hasBothTabs ? (
        <Tabs
          selectedKey={loginType}
          onSelectionChange={(key) => setLoginType(key as "password" | "code")}
          className="w-full"
        >
          <Tabs.ListContainer>
            <Tabs.List aria-label="Login type" className="w-full">
              <Tabs.Tab id="password" className="flex-1">
                {t("tabs.password")}
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="code" className="flex-1">
                {t("tabs.code")}
                <Tabs.Indicator />
              </Tabs.Tab>
            </Tabs.List>
          </Tabs.ListContainer>

          <Tabs.Panel id="password" className="pt-4">
            {passwordFormContent}
          </Tabs.Panel>

          <Tabs.Panel id="code" className="pt-4">
            {codeFormContent}
          </Tabs.Panel>
        </Tabs>
      ) : hasPassword ? (
        <div className="pt-2">{passwordFormContent}</div>
      ) : hasCode ? (
        <div className="pt-2">{codeFormContent}</div>
      ) : null}

      {/* Register Link */}
      <div className="flex items-center justify-center gap-3 text-center text-sm text-muted">
        <Link href={`/${locale}/register`} className="text-accent hover:underline">
          {t("noAccount")}
        </Link>

        <Separator orientation="vertical" className="h-3"/>

        <Link
          href={`/${locale}/forgot-password`}
          className="text-accent hover:underline"
        >
          {t("forgotPassword")}
        </Link>
      </div>

      {hasOAuth && (
        <>
          {/* Divider */}
          <div className="flex items-center gap-3">
            <Separator className="flex-1" />
            <span className="text-xs text-muted">{tCommon("or")}</span>
            <Separator className="flex-1" />
          </div>

          {/* OAuth Buttons */}
          <div className="flex justify-center gap-3">
            {oauthProviders.map((op) => (
              <Button
                key={op.provider}
                variant="tertiary"
                isIconOnly
                className="size-11 rounded-full"
                aria-label={op.displayName}
                onPress={() => handleOAuthLogin(op.provider)}
              >
                {getOAuthIcon(op.icon, op.displayName)}
              </Button>
            ))}
          </div>
        </>
      )}

    </div>
  );
}
