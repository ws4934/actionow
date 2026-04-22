"use client";

/**
 * CaptchaInput Component
 * Displays a captcha image with input field and refresh button
 */

import { useState, useEffect, useCallback, forwardRef, useImperativeHandle } from "react";
import { useTranslations, useLocale} from "next-intl";
import { Input, Button, Spinner, toast} from "@heroui/react";
import { RefreshCw } from "lucide-react";
import { authService, getErrorFromException} from "@/lib/api";

export interface CaptchaData {
  captcha: string;
  captchaKey: string;
}

export interface CaptchaInputRef {
  refresh: () => Promise<void>;
  getCaptchaData: () => CaptchaData;
  isValid: () => boolean;
}

interface CaptchaInputProps {
  value: string;
  onChange: (value: string) => void;
  onCaptchaKeyChange?: (key: string) => void;
  placeholder?: string;
  isDisabled?: boolean;
  isInvalid?: boolean;
  autoRefreshOnMount?: boolean;
}

export const CaptchaInput = forwardRef<CaptchaInputRef, CaptchaInputProps>(
  function CaptchaInput(
    {
      value,
      onChange,
      onCaptchaKeyChange,
      placeholder,
      isDisabled = false,
      isInvalid = false,
      autoRefreshOnMount = true,
    },
    ref
  ) {
    const t = useTranslations("auth.captcha");
    const locale = useLocale();
    const [captchaImage, setCaptchaImage] = useState<string>("");
    const [captchaKey, setCaptchaKey] = useState<string>("");
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string>("");

    const fetchCaptcha = useCallback(async () => {
      setIsLoading(true);
      setError("");
      try {
        const response = await authService.getCaptcha();
        setCaptchaImage(response.captchaImage);
        setCaptchaKey(response.captchaKey);
        onCaptchaKeyChange?.(response.captchaKey);
        // Clear input when refreshing captcha
        onChange("");
      } catch (err) {
        console.error("Failed to fetch captcha:", err);
        toast.danger(getErrorFromException(err, locale));
        setError(t("fetchError"));
      } finally {
        setIsLoading(false);
      }
    }, [onChange, onCaptchaKeyChange, locale, t]);

    // Fetch captcha on mount
    useEffect(() => {
      if (autoRefreshOnMount) {
        fetchCaptcha();
      }
    }, [autoRefreshOnMount, fetchCaptcha]);

    // Expose methods via ref
    useImperativeHandle(ref, () => ({
      refresh: fetchCaptcha,
      getCaptchaData: () => ({
        captcha: value,
        captchaKey: captchaKey,
      }),
      isValid: () => value.length >= 4 && captchaKey.length > 0,
    }));

    return (
      <div className="flex items-center gap-2">
        {/* Captcha Image */}
        <div
          className="relative flex h-10 w-24 shrink-0 cursor-pointer items-center justify-center overflow-hidden rounded-md border border-border bg-surface"
          onClick={() => !isLoading && !isDisabled && fetchCaptcha()}
          title={t("clickToRefresh")}
        >
          {isLoading ? (
            <Spinner size="sm" />
          ) : error ? (
            <span className="text-xs text-danger">{error}</span>
          ) : captchaImage ? (
            <img
              src={captchaImage}
              alt={t("imageAlt")}
              className="h-full w-full object-contain"
              draggable={false}
            />
          ) : (
            <span className="text-xs text-muted">{t("clickToLoad")}</span>
          )}
        </div>

        {/* Refresh Button */}
        <Button
          type="button"
          variant="ghost"
          size="sm"
          isIconOnly
          className="size-10 shrink-0"
          onPress={fetchCaptcha}
          isDisabled={isLoading || isDisabled}
          aria-label={t("refresh")}
        >
          <RefreshCw className={`size-4 ${isLoading ? "animate-spin" : ""}`} />
        </Button>

        {/* Input Field */}
        <Input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder ?? t("placeholder")}
          maxLength={6}
          className={`flex-1 ${isInvalid ? "border-danger" : ""}`}
          disabled={isDisabled || !captchaKey}
          autoComplete="off"
        />
      </div>
    );
  }
);

export default CaptchaInput;
