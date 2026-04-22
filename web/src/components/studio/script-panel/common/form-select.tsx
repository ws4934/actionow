"use client";

/**
 * Form Select Component
 * A simplified wrapper around HeroUI Select for form usage
 */

import { Select, ListBox, Label } from "@heroui/react";
import type { Key } from "react";

interface FormSelectOption {
  value: string;
  label: string;
}

interface FormSelectProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: FormSelectOption[];
  placeholder?: string;
  className?: string;
  variant?: "primary" | "secondary";
  isDisabled?: boolean;
}

export function FormSelect({
  label,
  value,
  onChange,
  options,
  placeholder = "请选择",
  className,
  variant = "secondary",
  isDisabled = false,
}: FormSelectProps) {
  const handleChange = (key: Key | Key[] | null) => {
    if (key !== null && !Array.isArray(key)) {
      onChange(String(key));
    }
  };

  const selectedOption = options.find(opt => opt.value === value);

  return (
    <Select
      className={className}
      placeholder={placeholder}
      value={value || null}
      onChange={handleChange}
      variant={variant}
      isDisabled={isDisabled}
    >
      {label && <Label className="text-xs font-medium text-muted">{label}</Label>}
      <Select.Trigger className="overflow-hidden">
        <Select.Value>
          {({ isPlaceholder }) => (
            <span className="block min-w-0 truncate">
              {isPlaceholder ? placeholder : selectedOption?.label}
            </span>
          )}
        </Select.Value>
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover className="max-w-[300px]">
        <ListBox>
          {options.map((option) => (
            <ListBox.Item key={option.value} id={option.value} textValue={option.label}>
              <span className="block truncate">{option.label}</span>
              <ListBox.ItemIndicator />
            </ListBox.Item>
          ))}
        </ListBox>
      </Select.Popover>
    </Select>
  );
}
