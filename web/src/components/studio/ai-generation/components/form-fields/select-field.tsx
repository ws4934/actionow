"use client";

import { useMemo } from "react";
import { Select, ListBox, ErrorMessage } from "@heroui/react";
import type { Key } from "react";
import type { InputParamDefinition } from "@/lib/api/dto/ai.dto";
import { FieldLabel } from "./field-label";

interface SelectFieldProps {
  param: InputParamDefinition;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  error?: string;
}

export function SelectField({
  param,
  value,
  onChange,
  disabled,
  error,
}: SelectFieldProps) {
  // Build options from either `options` array or `enum` array
  const options = useMemo(() => {
    if (param.options && param.options.length > 0) {
      return param.options;
    }
    // Fallback to enum values if no options provided
    if (param.enum && param.enum.length > 0) {
      return param.enum.map((v) => ({ value: v, label: v }));
    }
    return [];
  }, [param.options, param.enum]);

  const handleChange = (key: Key | Key[] | null) => {
    if (key !== null && !Array.isArray(key)) {
      onChange(String(key));
    }
  };

  const selectedOption = options.find(o => o.value === value);

  return (
    <Select
      className="w-full"
      value={value || null}
      onChange={handleChange}
      isDisabled={disabled}
      isInvalid={!!error}
    >
      <FieldLabel
        label={param.label}
        required={param.required}
        description={param.description}
      />
      <Select.Trigger className="overflow-hidden">
        <Select.Value>
          {({ isPlaceholder }) => (
            <span className="block min-w-0 truncate">
              {isPlaceholder ? param.placeholder || param.label : selectedOption?.label ?? value}
            </span>
          )}
        </Select.Value>
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover>
        <ListBox>
          {options.map((option) => (
            <ListBox.Item key={option.value} id={option.value} textValue={option.label}>
              <span className="block truncate">{option.label}</span>
              <ListBox.ItemIndicator />
            </ListBox.Item>
          ))}
        </ListBox>
      </Select.Popover>
      {error && <ErrorMessage>{error}</ErrorMessage>}
    </Select>
  );
}
