"use client";

import { TextField, TextArea as HeroTextArea, ErrorMessage } from "@heroui/react";
import type { InputParamDefinition } from "@/lib/api/dto/ai.dto";
import { FieldLabel } from "./field-label";

interface TextareaFieldProps {
  param: InputParamDefinition;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  error?: string;
}

export function TextareaField({
  param,
  value,
  onChange,
  disabled,
  error,
}: TextareaFieldProps) {
  const rows = (param.componentProps?.rows as number) || 4;

  return (
    <TextField className="w-full" isRequired={param.required} isDisabled={disabled} isInvalid={!!error}>
      <FieldLabel
        label={param.label}
        required={param.required}
        description={param.description}
      />
      <HeroTextArea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        maxLength={param.maxLength}
        rows={rows}
        placeholder={param.placeholder}
      />
      {error && <ErrorMessage>{error}</ErrorMessage>}
    </TextField>
  );
}
