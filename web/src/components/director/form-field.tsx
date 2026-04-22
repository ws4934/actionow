"use client";

import type { InputHTMLAttributes, TextareaHTMLAttributes } from "react";

type InputType = "text" | "email" | "password" | "number" | "tel" | "url";

interface FormFieldProps {
  label: string;
  name?: string;
  type?: InputType;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  readOnly?: boolean;
  error?: string;
  hint?: string;
  className?: string;
}

const INPUT_CLASSES = `
  w-full rounded-lg border border-border bg-surface px-3 py-2.5
  text-sm text-foreground placeholder:text-muted
  focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent
  disabled:cursor-not-allowed disabled:opacity-50
  read-only:bg-background read-only:text-muted
`.trim().replace(/\s+/g, " ");

/**
 * 表单输入字段组件
 */
export function FormField({
  label,
  name,
  type = "text",
  value,
  onChange,
  placeholder,
  required = false,
  disabled = false,
  readOnly = false,
  error,
  hint,
  className = "",
}: FormFieldProps) {
  return (
    <div className={className}>
      <label className="mb-1.5 block text-sm font-medium text-foreground">
        {label}
        {required && <span className="ml-0.5 text-danger">*</span>}
      </label>
      <input
        type={type}
        name={name}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        required={required}
        disabled={disabled}
        readOnly={readOnly}
        className={`${INPUT_CLASSES} ${error ? "border-danger focus:border-danger focus:ring-danger" : ""}`}
      />
      {error && <p className="mt-1 text-xs text-danger">{error}</p>}
      {hint && !error && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}

/**
 * 多行文本输入组件
 */
interface TextAreaFieldProps {
  label: string;
  name?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  rows?: number;
  required?: boolean;
  disabled?: boolean;
  readOnly?: boolean;
  error?: string;
  hint?: string;
  className?: string;
}

export function TextAreaField({
  label,
  name,
  value,
  onChange,
  placeholder,
  rows = 3,
  required = false,
  disabled = false,
  readOnly = false,
  error,
  hint,
  className = "",
}: TextAreaFieldProps) {
  return (
    <div className={className}>
      <label className="mb-1.5 block text-sm font-medium text-foreground">
        {label}
        {required && <span className="ml-0.5 text-danger">*</span>}
      </label>
      <textarea
        name={name}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        rows={rows}
        required={required}
        disabled={disabled}
        readOnly={readOnly}
        className={`${INPUT_CLASSES} resize-none ${error ? "border-danger focus:border-danger focus:ring-danger" : ""}`}
      />
      {error && <p className="mt-1 text-xs text-danger">{error}</p>}
      {hint && !error && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}

/**
 * 只读字段组件 (用于展示信息)
 */
interface ReadOnlyFieldProps {
  label: string;
  value: string;
  className?: string;
}

export function ReadOnlyField({
  label,
  value,
  className = "",
}: ReadOnlyFieldProps) {
  return (
    <div className={className}>
      <label className="mb-1.5 block text-sm font-medium text-foreground">
        {label}
      </label>
      <div className="rounded-lg bg-surface px-3 py-2.5 text-sm text-muted">
        {value || "-"}
      </div>
    </div>
  );
}
