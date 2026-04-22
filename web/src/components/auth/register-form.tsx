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
  Description,
  FieldError,
  Checkbox,
  Separator,
  InputOTP,
  Spinner,
  toast,
} from "@heroui/react";
import {
  Eye,
  EyeOff,
  Mail,
  Lock,
  User,
  UserCircle,
  Ticket,
  ArrowLeft,
  ArrowRight,
  CheckCircle,
  XCircle,
} from "lucide-react";
import {
  authService,
  inviteService,
  userService,
  setAuthBundle,
  getErrorFromException,
} from "@/lib/api";
import { handleLoginCacheTransition } from "@/lib/stores/session-cache";
import { useAuthActions } from "./use-auth-actions";
import { useLoginConfig, getOAuthIcon } from "./use-login-config";
import { CaptchaInput, CaptchaInputRef } from "./captcha-input";

interface RegisterFormProps {
  initialInviteCode?: string;
  returnUrl?: string;
}

export function RegisterForm({ initialInviteCode, returnUrl }: RegisterFormProps) {
  const t = useTranslations("auth.register");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("auth.errors");
  const tSuccess = useTranslations("auth.success");
  const tCaptcha = useTranslations("auth.captcha");
  const locale = useLocale();
  const router = useRouter();
  const { autoSwitchWorkspace, handleOAuthLogin } = useAuthActions(returnUrl);

  // Login config (shared with login page)
  const loginConfig = useLoginConfig();
  const registrationConfig = loginConfig ? {
    invitationCodeRequired: loginConfig.invitationCodeRequired,
    allowUserCode: loginConfig.allowUserCode,
  } : null;
  const oauthProviders = loginConfig?.oauthProviders ?? [];
  const hasOAuth = oauthProviders.length > 0;

  const [step, setStep] = useState<1 | 2>(1);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSendingCode, setIsSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);

  // Form state
  const [formData, setFormData] = useState({
    email: "",
    verifyCode: "",
    inviteCode: initialInviteCode || "",
    username: "",
    password: "",
    confirmPassword: "",
    nickname: "",
  });
  const [agreement, setAgreement] = useState(false);

  // Validation errors
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Invite code validation state
  const [inviteCodeValid, setInviteCodeValid] = useState<boolean | null>(null);
  const [isValidatingInviteCode, setIsValidatingInviteCode] = useState(false);

  // Availability check states
  const [emailAvailable, setEmailAvailable] = useState<boolean | null>(null);
  const [isCheckingEmail, setIsCheckingEmail] = useState(false);
  const [usernameAvailable, setUsernameAvailable] = useState<boolean | null>(null);
  const [isCheckingUsername, setIsCheckingUsername] = useState(false);

  // Captcha state
  const [captchaValue, setCaptchaValue] = useState("");
  const [captchaKey, setCaptchaKey] = useState("");
  const captchaRef = useRef<CaptchaInputRef>(null);

  const updateField = (field: string, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: "" }));
    }
    // Reset invite code validation when changed
    if (field === "inviteCode") {
      setInviteCodeValid(null);
    }
  };

  // Validate invite code when it changes
  useEffect(() => {
    const validateInviteCode = async () => {
      if (!formData.inviteCode || formData.inviteCode.length < 3) {
        setInviteCodeValid(null);
        return;
      }

      setIsValidatingInviteCode(true);
      try {
        const result = await inviteService.validate(formData.inviteCode);
        setInviteCodeValid(result.valid);
      } catch {
        setInviteCodeValid(false);
      } finally {
        setIsValidatingInviteCode(false);
      }
    };

    const debounce = setTimeout(validateInviteCode, 500);
    return () => clearTimeout(debounce);
  }, [formData.inviteCode]);

  // Email availability check (debounced 500ms)
  useEffect(() => {
    if (!formData.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      setEmailAvailable(null);
      return;
    }

    setIsCheckingEmail(true);
    const timer = setTimeout(async () => {
      try {
        const result = await userService.checkEmail(formData.email);
        setEmailAvailable(result.available);
      } catch {
        setEmailAvailable(null);
      } finally {
        setIsCheckingEmail(false);
      }
    }, 500);

    return () => { clearTimeout(timer); setIsCheckingEmail(false); };
  }, [formData.email]);

  // Username availability check (debounced 500ms)
  useEffect(() => {
    if (!formData.username || formData.username.length < 4 || !/^[a-zA-Z][a-zA-Z0-9_]*$/.test(formData.username)) {
      setUsernameAvailable(null);
      return;
    }

    setIsCheckingUsername(true);
    const timer = setTimeout(async () => {
      try {
        const result = await userService.checkUsername(formData.username);
        setUsernameAvailable(result.available);
      } catch {
        setUsernameAvailable(null);
      } finally {
        setIsCheckingUsername(false);
      }
    }, 500);

    return () => { clearTimeout(timer); setIsCheckingUsername(false); };
  }, [formData.username]);

  const validateStep1 = () => {
    const newErrors: Record<string, string> = {};

    // Email validation
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = tErrors("invalidEmail");
    }

    // Email availability
    if (isCheckingEmail) {
      newErrors.email = tErrors("checkingEmail");
    } else if (emailAvailable === false) {
      newErrors.email = tErrors("emailTaken");
    }

    // Verify code
    if (!/^\d{6}$/.test(formData.verifyCode)) {
      newErrors.verifyCode = tErrors("invalidVerifyCode");
    }

    // Invite code required check
    if (registrationConfig?.invitationCodeRequired && !formData.inviteCode) {
      newErrors.inviteCode = tErrors("inviteCodeRequired");
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const validateStep2 = () => {
    const newErrors: Record<string, string> = {};

    // Username validation
    if (formData.username.length < 4) {
      newErrors.username = tErrors("usernameTooShort");
    } else if (formData.username.length > 32) {
      newErrors.username = tErrors("usernameTooLong");
    } else if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(formData.username)) {
      newErrors.username = tErrors("invalidUsername");
    } else if (usernameAvailable === false) {
      newErrors.username = tErrors("usernameTaken");
    }

    // Password validation
    if (formData.password.length < 8) {
      newErrors.password = tErrors("passwordTooShort");
    } else if (formData.password.length > 64) {
      newErrors.password = tErrors("passwordTooLong");
    } else if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/.test(formData.password)) {
      newErrors.password = tErrors("invalidPassword");
    }

    // Confirm password
    if (formData.password !== formData.confirmPassword) {
      newErrors.confirmPassword = tErrors("passwordMismatch");
    }

    // Agreement
    if (!agreement) {
      newErrors.agreement = tErrors("agreementRequired");
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSendCode = async () => {
    if (countdown > 0 || !formData.email || isSendingCode) return;

    // Basic email validation before sending
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      setErrors((prev) => ({ ...prev, email: tErrors("invalidEmail") }));
      return;
    }

    // Validate captcha
    if (!captchaValue || captchaValue.length < 4) {
      toast.danger(tCaptcha("required"));
      return;
    }

    setIsSendingCode(true);
    try {
      await authService.sendVerifyCode({
        target: formData.email,
        type: "register",
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

  const handleNextStep = () => {
    if (validateStep1()) {
      setStep(2);
    }
  };

  const handlePrevStep = () => {
    setStep(1);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (step === 1) {
      handleNextStep();
      return;
    }

    if (!validateStep2()) return;

    setIsLoading(true);

    try {
      const response = await authService.register({
        username: formData.username,
        email: formData.email,
        password: formData.password,
        nickname: formData.nickname || undefined,
        verifyCode: formData.verifyCode,
        inviteCode: formData.inviteCode || undefined,
      });

      const { token, user } = response;
      handleLoginCacheTransition(user.id);
      setAuthBundle(token, user.id);
      await autoSwitchWorkspace(user.id, token.workspaceId, "registerSuccess");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col gap-3">
      {/* Header */}
      <div className="text-center">
        <h1 className="text-2xl font-bold text-foreground">{t("title")}</h1>
        <p className="mt-1 text-sm text-muted">{t("subtitle")}</p>
      </div>

      {/* Step Indicator */}
      <div className="flex items-center justify-center gap-2">
        <div className="flex flex-col items-center gap-1">
          <div
            className={`flex size-7 items-center justify-center rounded-full text-sm font-medium transition-colors ${
              step === 1
                ? "bg-accent text-accent-foreground"
                : "bg-accent/20 text-accent"
            }`}
          >
            1
          </div>
          <span className={`text-xs ${step === 1 ? "text-accent font-medium" : "text-muted"}`}>
            {t("step1Label")}
          </span>
        </div>
        <div className="mb-4 h-0.5 w-8 bg-border" />
        <div className="flex flex-col items-center gap-1">
          <div
            className={`flex size-7 items-center justify-center rounded-full text-sm font-medium transition-colors ${
              step === 2
                ? "bg-accent text-accent-foreground"
                : "bg-surface text-muted"
            }`}
          >
            2
          </div>
          <span className={`text-xs ${step === 2 ? "text-accent font-medium" : "text-muted"}`}>
            {t("step2Label")}
          </span>
        </div>
      </div>

      {/* Register Form */}
      <Form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {step === 1 ? (
          <>
            {/* Email */}
            <TextField name="email" type="email" isRequired isInvalid={!!errors.email} className="w-full">
              <Label>{t("email")}</Label>
              <div className="relative w-full">
                <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
                <Input
                  placeholder={t("emailPlaceholder")}
                  value={formData.email}
                  onChange={(e) => updateField("email", e.target.value)}
                  className="w-full pl-10 pr-10"
                />
                {formData.email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email) && (
                  <div className="absolute right-3 top-1/2 -translate-y-1/2">
                    {isCheckingEmail ? (
                      <div className="size-4 animate-spin rounded-full border-2 border-muted border-t-transparent" />
                    ) : emailAvailable === true ? (
                      <CheckCircle className="size-4 text-success" />
                    ) : emailAvailable === false ? (
                      <XCircle className="size-4 text-danger" />
                    ) : null}
                  </div>
                )}
              </div>
              {errors.email && <FieldError>{errors.email}</FieldError>}
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

            {/* Verify Code */}
            <div className="flex w-full flex-col gap-2">
              <Label isRequired>{t("verifyCode")}</Label>
              <div className="flex w-full items-center justify-between gap-3">
                <InputOTP
                  maxLength={6}
                  value={formData.verifyCode}
                  onChange={(value) => updateField("verifyCode", value)}
                  isInvalid={!!errors.verifyCode}
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
                  isDisabled={countdown > 0 || !formData.email || !captchaValue}
                  isPending={isSendingCode}
                  className="h-10 shrink-0"
                >
                  {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{countdown > 0 ? `${countdown}s` : t("sendCode")}</>)}
                </Button>
              </div>
              {errors.verifyCode && (
                <span className="text-xs text-danger">{errors.verifyCode}</span>
              )}
            </div>

            {/* Invite Code */}
            <TextField
              name="inviteCode"
              isRequired={registrationConfig?.invitationCodeRequired}
              isInvalid={!!errors.inviteCode}
              className="w-full"
            >
              <Label>
                {t("inviteCode")}{" "}
                {!registrationConfig?.invitationCodeRequired && (
                  <span className="text-xs text-muted">({t("inviteCodeOptional")})</span>
                )}
              </Label>
              <div className="relative w-full">
                <Ticket className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
                <Input
                  placeholder={t("inviteCodePlaceholder")}
                  value={formData.inviteCode}
                  onChange={(e) => updateField("inviteCode", e.target.value)}
                  className="w-full pl-10 pr-10"
                />
                {formData.inviteCode && (
                  <div className="absolute right-3 top-1/2 -translate-y-1/2">
                    {isValidatingInviteCode ? (
                      <div className="size-4 animate-spin rounded-full border-2 border-muted border-t-transparent" />
                    ) : inviteCodeValid === true ? (
                      <CheckCircle className="size-4 text-success" />
                    ) : inviteCodeValid === false ? (
                      <XCircle className="size-4 text-danger" />
                    ) : null}
                  </div>
                )}
              </div>
              {errors.inviteCode && <FieldError>{errors.inviteCode}</FieldError>}
            </TextField>

            {/* Next Step */}
            <Button
              type="submit"
              className="button--accent mt-auto h-11 w-full text-base font-semibold"
            >
              {t("nextStep")}
              <ArrowRight className="ml-1 size-4" />
            </Button>
          </>
        ) : (
          <>
            {/* Username */}
            <TextField
              name="username"
              isRequired
              isInvalid={!!errors.username}
              className="w-full"
            >
              <Label>{t("username")}</Label>
              <div className="relative w-full">
                <User className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
                <Input
                  placeholder={t("usernamePlaceholder")}
                  value={formData.username}
                  onChange={(e) => updateField("username", e.target.value)}
                  className="w-full pl-10 pr-10"
                />
                {formData.username && formData.username.length >= 4 && (
                  <div className="absolute right-3 top-1/2 -translate-y-1/2">
                    {isCheckingUsername ? (
                      <div className="size-4 animate-spin rounded-full border-2 border-muted border-t-transparent" />
                    ) : usernameAvailable === true ? (
                      <CheckCircle className="size-4 text-success" />
                    ) : usernameAvailable === false ? (
                      <XCircle className="size-4 text-danger" />
                    ) : null}
                  </div>
                )}
              </div>
              {errors.username ? (
                <FieldError>{errors.username}</FieldError>
              ) : (
                <Description>{t("usernameHint")}</Description>
              )}
            </TextField>

            {/* Password */}
            <TextField
              name="password"
              type={showPassword ? "text" : "password"}
              isRequired
              isInvalid={!!errors.password}
              className="w-full"
            >
              <Label>{t("password")}</Label>
              <div className="relative w-full">
                <Lock className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
                <Input
                  placeholder={t("passwordPlaceholder")}
                  value={formData.password}
                  onChange={(e) => updateField("password", e.target.value)}
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
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </Button>
              </div>
              {errors.password && <FieldError>{errors.password}</FieldError>}
            </TextField>

            {/* Confirm Password */}
            <TextField
              name="confirmPassword"
              type={showConfirmPassword ? "text" : "password"}
              isRequired
              isInvalid={!!errors.confirmPassword}
              className="w-full"
            >
              <Label>{t("confirmPassword")}</Label>
              <div className="relative w-full">
                <Lock className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
                <Input
                  placeholder={t("confirmPasswordPlaceholder")}
                  value={formData.confirmPassword}
                  onChange={(e) => updateField("confirmPassword", e.target.value)}
                  className="w-full pl-10 pr-10"
                />
                <Button
                  type="button"
                  variant="ghost"
                  isIconOnly
                  size="sm"
                  onPress={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted hover:text-foreground"
                >
                  {showConfirmPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </Button>
              </div>
              {errors.confirmPassword && <FieldError>{errors.confirmPassword}</FieldError>}
            </TextField>

            {/* Nickname */}
            <TextField name="nickname" className="w-full">
              <Label>
                {t("nickname")}{" "}
                <span className="text-xs text-muted">({t("nicknameOptional")})</span>
              </Label>
              <div className="relative w-full">
                <UserCircle className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
                <Input
                  placeholder={t("nicknamePlaceholder")}
                  value={formData.nickname}
                  onChange={(e) => updateField("nickname", e.target.value)}
                  className="w-full pl-10"
                />
              </div>
            </TextField>

            {/* Agreement */}
            <div className="flex flex-col gap-1">
              <div className="flex items-start gap-2">
                <Checkbox
                  id="agreement"
                  isSelected={agreement}
                  onChange={setAgreement}
                  className="mt-0.5"
                >
                  <Checkbox.Control className="size-4">
                    <Checkbox.Indicator />
                  </Checkbox.Control>
                </Checkbox>
                <label htmlFor="agreement" className="cursor-pointer text-sm text-muted">
                  {t("agreement")}{" "}
                  <Link href={`/${locale}/terms`} className="text-accent hover:underline">
                    {t("termsOfService")}
                  </Link>{" "}
                  {t("and")}{" "}
                  <Link href={`/${locale}/privacy`} className="text-accent hover:underline">
                    {t("privacyPolicy")}
                  </Link>
                </label>
              </div>
              {errors.agreement && (
                <span className="text-xs text-danger">{errors.agreement}</span>
              )}
            </div>

            {/* Buttons */}
            <div className="mt-auto flex gap-3">
              <Button
                type="button"
                variant="secondary"
                onPress={handlePrevStep}
                className="h-11 flex-1 text-base font-semibold"
              >
                <ArrowLeft className="mr-1 size-4" />
                {t("prevStep")}
              </Button>
              <Button
                type="submit"
                isPending={isLoading}
                className="button--accent h-11 flex-1 text-base font-semibold"
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{t("submit")}</>)}
              </Button>
            </div>
          </>
        )}
      </Form>

      {/* Login Link */}
      <p className="text-center text-sm text-muted">
        {t("hasAccount")}{" "}
        <Link href={`/${locale}/login`} className="text-accent hover:underline">
          {t("login")}
        </Link>
      </p>

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
