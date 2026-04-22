"use client";

import { NumberField as HeroNumberField, Input, ErrorMessage } from "@heroui/react";
import type { InputParamDefinition } from "@/lib/api/dto/ai.dto";
import { FieldLabel } from "./field-label";

interface NumberFieldProps {
  param: InputParamDefinition;
  value: number | undefined;
  onChange: (value: number | undefined) => void;
  disabled?: boolean;
  error?: string;
}

export function NumberField({
  param,
  value,
  onChange,
  disabled,
  error,
}: NumberFieldProps) {
  const minValue = param.min ?? param.validation?.min;
  const maxValue = param.max ?? param.validation?.max;
  const placeholder =
    param.placeholder ??
    (minValue != null && maxValue != null ? `${minValue}-${maxValue}` : undefined);

  return (
    <HeroNumberField
      className="w-full"
      isRequired={param.required}
      isDisabled={disabled}
      isInvalid={!!error}
      value={value}
      onChange={(val) => onChange(val)}
      minValue={minValue}
      maxValue={maxValue}
    >
      <FieldLabel
        label={param.label}
        required={param.required}
        description={param.description}
      />
      <Input placeholder={placeholder} />
      {error && <ErrorMessage>{error}</ErrorMessage>}
    </HeroNumberField>
  );
}
