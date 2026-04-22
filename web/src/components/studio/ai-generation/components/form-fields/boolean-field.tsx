"use client";

import { Switch } from "@heroui/react";
import type { InputParamDefinition } from "@/lib/api/dto/ai.dto";
import { FieldLabel } from "./field-label";

interface BooleanFieldProps {
  param: InputParamDefinition;
  value: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
}

export function BooleanField({
  param,
  value,
  onChange,
  disabled,
}: BooleanFieldProps) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg bg-muted/5 px-3 py-2.5">
      <div className="min-w-0 flex-1">
        <FieldLabel
          label={param.label}
          required={param.required}
          description={param.description}
          containerClassName="min-w-0"
        />
      </div>
      <Switch
        isSelected={value}
        onChange={onChange}
        isDisabled={disabled}
      >
        <Switch.Control>
          <Switch.Thumb />
        </Switch.Control>
      </Switch>
    </div>
  );
}
