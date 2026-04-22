"use client";

import { useState, useCallback } from "react";
import { Button, toast} from "@heroui/react";
import { Copy, Check } from "lucide-react";
import { useLocale } from "next-intl";
import { getErrorFromException } from "@/lib/api";

interface CopyButtonProps {
  text: string;
  size?: "sm" | "md";
  variant?: "tertiary" | "secondary";
  successDuration?: number;
  className?: string;
}

/**
 * 复制按钮组件
 * 点击后复制文本到剪贴板，显示成功反馈
 */
export function CopyButton({
  text,
  size = "sm",
  variant = "tertiary",
  successDuration = 2000,
  className = "",
}: CopyButtonProps) {
  const locale = useLocale();
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), successDuration);
    } catch (err) {
      console.error("Failed to copy:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  }, [text, successDuration]);

  const iconSize = size === "sm" ? "size-4" : "size-5";

  return (
    <Button
      variant={variant}
      size={size}
      isIconOnly
      onPress={handleCopy}
      className={className}
      aria-label={copied ? "已复制" : "复制"}
    >
      {copied ? (
        <Check className={`${iconSize} text-success`} />
      ) : (
        <Copy className={iconSize} />
      )}
    </Button>
  );
}

/**
 * 带文本的复制字段组件
 */
interface CopyFieldProps {
  label?: string;
  value: string;
  className?: string;
}

export function CopyField({ label, value, className = "" }: CopyFieldProps) {
  return (
    <div className={className}>
      {label && (
        <label className="mb-1.5 block text-sm font-medium text-foreground">
          {label}
        </label>
      )}
      <div className="flex items-center gap-2">
        <code className="flex-1 truncate rounded-lg bg-surface px-3 py-2 font-mono text-sm text-foreground">
          {value}
        </code>
        <CopyButton text={value} />
      </div>
    </div>
  );
}
