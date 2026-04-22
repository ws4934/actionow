"use client";

import { TextField as HeroTextField, Input, ErrorMessage } from "@heroui/react";
import type { InputParamDefinition } from "@/lib/api/dto/ai.dto";
import { FieldLabel } from "./field-label";

interface TextFieldProps {
  param: InputParamDefinition;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  error?: string;
}

export function TextField({
  param,
  value,
  onChange,
  disabled,
  error,
}: TextFieldProps) {
  return (
    <HeroTextField className="w-full" isRequired={param.required} isDisabled={disabled} isInvalid={!!error}>
      <FieldLabel
        label={param.label}
        required={param.required}
        description={param.description}
      />
      <Input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        maxLength={param.maxLength}
        placeholder={param.placeholder}
      />
      {error && <ErrorMessage>{error}</ErrorMessage>}
    </HeroTextField>
  );
}
