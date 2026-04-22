"use client";

import { Label, Tooltip } from "@heroui/react";
import { CircleQuestionMark } from "lucide-react";

interface FieldLabelProps {
  label: string;
  required?: boolean;
  description?: string | null;
  containerClassName?: string;
  labelClassName?: string;
}

function joinClasses(...classes: Array<string | undefined>) {
  return classes.filter(Boolean).join(" ");
}

export function FieldLabel({
  label,
  required,
  description,
  containerClassName,
  labelClassName,
}: FieldLabelProps) {
  return (
    <div className={joinClasses("flex min-w-0 items-center gap-1.5", containerClassName)}>
      <Label className={joinClasses("min-w-0 text-sm font-medium", labelClassName)}>
        <span className="truncate">{label}</span>
        {required && <span className="ml-1 text-danger">*</span>}
      </Label>
      {description && (
        <Tooltip delay={300}>
          <Tooltip.Trigger>
            <button
              type="button"
              aria-label={`Show description for ${label}`}
              className="inline-flex size-5 shrink-0 items-center justify-center rounded-full text-muted transition-colors hover:bg-muted/10 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
            >
              <CircleQuestionMark className="size-3.5" />
            </button>
          </Tooltip.Trigger>
          <Tooltip.Content className="max-w-[280px] p-2">
            <p className="whitespace-pre-wrap break-words text-xs leading-relaxed text-foreground/85">
              {description}
            </p>
          </Tooltip.Content>
        </Tooltip>
      )}
    </div>
  );
}
