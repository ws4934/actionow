"use client";

import { useState, useRef } from "react";
import { useTranslations, useLocale } from "next-intl";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Button,
  Form,
  Input,
  Label,
  TextField,
  FieldError,
  InputOTP,
  Spinner,
  toast,
} from "@heroui/react";
import { Eye, EyeOff, Mail, Lock, ArrowLeft } from "lucide-react";
import {
  authService,
  userService,
  getErrorFromException,
} from "@/lib/api";
import { CaptchaInput, CaptchaInputRef } from "./captcha-input";

export function ForgotPasswordForm() {
  const t = useTranslations("auth.forgotPassword");
  const tErrors = useTranslations("auth.errors");
  const tSuccess = useTranslations("auth.success");
  const tCaptcha = useTranslations("auth.captcha");
  const locale = useLocale();
  const router = useRouter();

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isSendingCode, setIsSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [step, setStep] = useState<"email" | "reset">("email");

  // Form state
  const [formData, setFormData] = useState({
    email: "",
    verifyCode: "",
    newPassword: "",
    confirmPassword: "",
  });

  // Validation errors
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Captcha state
  const [captchaValue, setCaptchaValue] = useState("");
  const [captchaKey, setCaptchaKey] = useState("");
  const captchaRef = useRef<CaptchaInputRef>(null);

  const updateField = (field: string, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: "" }));
    }
  };

  const handleSendCode = async () => {
    if (countdown > 0 || !formData.email || isSendingCode) return;

    // Basic email validation
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
        type: "reset_password",
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
      setStep("reset");
    } catch (error) {
      // Refresh captcha on error
      captchaRef.current?.refresh();
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsSendingCode(false);
    }
  };

  const validateForm = () => {
    const newErrors: Record<string, string> = {};

    // Verify code
    if (!/^\d{6}$/.test(formData.verifyCode)) {
      newErrors.verifyCode = tErrors("invalidVerifyCode");
    }

    // Password validation
    if (formData.newPassword.length < 8) {
      newErrors.newPassword = tErrors("passwordTooShort");
    } else if (formData.newPassword.length > 64) {
      newErrors.newPassword = tErrors("passwordTooLong");
    } else if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/.test(formData.newPassword)) {
      newErrors.newPassword = tErrors("invalidPassword");
    }

    // Confirm password
    if (formData.newPassword !== formData.confirmPassword) {
      newErrors.confirmPassword = tErrors("passwordMismatch");
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (step === "email") {
      handleSendCode();
      return;
    }

    if (!validateForm()) return;

    setIsLoading(true);

    try {
      await userService.resetPassword({
        target: formData.email,
        verifyCode: formData.verifyCode,
        newPassword: formData.newPassword,
      });

      toast.success(tSuccess("passwordResetSuccess"));
      router.push(`/${locale}/login`);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="text-center">
        <h1 className="text-2xl font-bold text-foreground">{t("title")}</h1>
        <p className="mt-1 text-sm text-muted">{t("subtitle")}</p>
      </div>

      {/* Form */}
      <Form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {/* Email */}
        <TextField name="email" type="email" isRequired isDisabled={step === "reset"} className="w-full">
          <Label>{t("email")}</Label>
          <div className="relative w-full">
            <Mail className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
            <Input
              placeholder={t("emailPlaceholder")}
              value={formData.email}
              onChange={(e) => updateField("email", e.target.value)}
              className="w-full pl-10"
            />
          </div>
        </TextField>

        {/* Code sent confirmation */}
        {step === "reset" && countdown > 0 && (
          <p className="text-xs text-muted">
            {t("codeSentTo", { email: formData.email })}
          </p>
        )}

        {/* Captcha Input - shown in email step and when countdown expires in reset step */}
        {(step === "email" || countdown === 0) && (
          <div className="flex w-full flex-col gap-2">
            <Label isRequired>{tCaptcha("label")}</Label>
            <CaptchaInput
              ref={captchaRef}
              value={captchaValue}
              onChange={setCaptchaValue}
              onCaptchaKeyChange={setCaptchaKey}
            />
          </div>
        )}

        {step === "reset" && (
          <>
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
                  isDisabled={countdown > 0 || !captchaValue}
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

            {/* New Password */}
            <TextField
              name="newPassword"
              type={showPassword ? "text" : "password"}
              isRequired
              isInvalid={!!errors.newPassword}
              className="w-full"
            >
              <Label>{t("newPassword")}</Label>
              <div className="relative w-full">
                <Lock className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
                <Input
                  placeholder={t("newPasswordPlaceholder")}
                  value={formData.newPassword}
                  onChange={(e) => updateField("newPassword", e.target.value)}
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
              {errors.newPassword && <FieldError>{errors.newPassword}</FieldError>}
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
          </>
        )}

        {/* Submit */}
        <Button
          type="submit"
          isPending={isLoading || isSendingCode}
          isDisabled={step === "email" && !captchaValue}
          className="button--accent mt-2 h-11 w-full text-base font-semibold"
        >
          {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}{step === "email" ? t("sendCode") : t("submit")}</>)}
        </Button>
      </Form>

      {/* Back to Login */}
      <Link
        href={`/${locale}/login`}
        className="flex items-center justify-center gap-2 text-sm text-muted hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        {t("backToLogin")}
      </Link>
    </div>
  );
}
