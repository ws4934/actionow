"use client";

import { useState, useCallback, useRef } from "react";
import { ListBox, Select, Switch } from "@heroui/react";
import {
  AlertCircle,
  Braces,
  ChevronDown,
  ChevronRight,
  FileCheck,
  GripVertical,
  Hash,
  Layers,
  Link2,
  Plus,
  Settings2,
  ShieldCheck,
  Trash2,
  X,
} from "lucide-react";
import { CodeEditor } from "./code-editor";

// ─── Param type definitions ────────────────────────────────────────────────

export const BASIC_PARAM_TYPES = [
  { value: "TEXT", label: "单行文本 (TEXT)" },
  { value: "TEXTAREA", label: "多行文本 (TEXTAREA)" },
  { value: "NUMBER", label: "数字 (NUMBER)" },
  { value: "BOOLEAN", label: "布尔值 (BOOLEAN)" },
  { value: "SELECT", label: "下拉选择 (SELECT)" },
  { value: "STRING", label: "字符串 (STRING)" },
  { value: "INTEGER", label: "整数 (INTEGER)" },
];

export const FILE_PARAM_TYPES = [
  { value: "IMAGE", label: "单个图片 (IMAGE)" },
  { value: "VIDEO", label: "单个视频 (VIDEO)" },
  { value: "AUDIO", label: "单个音频 (AUDIO)" },
  { value: "DOCUMENT", label: "单个文档 (DOCUMENT)" },
];

export const LIST_PARAM_TYPES = [
  { value: "TEXT_LIST", label: "文本列表 (TEXT_LIST)" },
  { value: "NUMBER_LIST", label: "数字列表 (NUMBER_LIST)" },
  { value: "IMAGE_LIST", label: "图片列表 (IMAGE_LIST)" },
  { value: "VIDEO_LIST", label: "视频列表 (VIDEO_LIST)" },
  { value: "AUDIO_LIST", label: "音频列表 (AUDIO_LIST)" },
  { value: "DOCUMENT_LIST", label: "文档列表 (DOCUMENT_LIST)" },
  { value: "ARRAY", label: "数组 (ARRAY)" },
];

export const ENTITY_PARAM_TYPES = [
  { value: "CHARACTER", label: "角色引用 (CHARACTER)" },
  { value: "SCENE", label: "场景引用 (SCENE)" },
  { value: "PROP", label: "道具引用 (PROP)" },
  { value: "STYLE", label: "风格引用 (STYLE)" },
  { value: "STORYBOARD", label: "分镜引用 (STORYBOARD)" },
];

export const ENTITY_LIST_PARAM_TYPES = [
  { value: "CHARACTER_LIST", label: "角色列表 (CHARACTER_LIST)" },
  { value: "SCENE_LIST", label: "场景列表 (SCENE_LIST)" },
  { value: "PROP_LIST", label: "道具列表 (PROP_LIST)" },
  { value: "STYLE_LIST", label: "风格列表 (STYLE_LIST)" },
  { value: "STORYBOARD_LIST", label: "分镜列表 (STORYBOARD_LIST)" },
];

export const ALL_PARAM_TYPES = [
  ...BASIC_PARAM_TYPES,
  ...FILE_PARAM_TYPES,
  ...LIST_PARAM_TYPES,
  ...ENTITY_PARAM_TYPES,
  ...ENTITY_LIST_PARAM_TYPES,
];

// ─── Type helpers ──────────────────────────────────────────────────────────

function typeLabel(type: string): string {
  return ALL_PARAM_TYPES.find((t) => t.value === type)?.label ?? type;
}

function typeBadgeColor(type: string): string {
  if (FILE_PARAM_TYPES.some((t) => t.value === type))
    return "bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300";
  if (LIST_PARAM_TYPES.some((t) => t.value === type))
    return "bg-purple-50 text-purple-700 dark:bg-purple-950/40 dark:text-purple-300";
  if (ENTITY_PARAM_TYPES.some((t) => t.value === type))
    return "bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300";
  if (ENTITY_LIST_PARAM_TYPES.some((t) => t.value === type))
    return "bg-orange-50 text-orange-700 dark:bg-orange-950/40 dark:text-orange-300";
  return "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300";
}

// ─── InputParam types ─────────────────────────────────────────────────────

export interface InputParam {
  _key?: string;
  name: string;
  type: string;
  label?: string;
  labelEn?: string;
  description?: string;
  required?: boolean;
  placeholder?: string;
  default?: string | number | boolean | null;
  defaultValue?: string | number | boolean | null;
  // Flat validation (backward compat)
  min?: number;
  max?: number;
  step?: number;
  minLength?: number;
  maxLength?: number;
  // Structured validation
  validation?: {
    min?: number;
    max?: number;
    step?: number;
    precision?: number;
    minLength?: number;
    maxLength?: number;
    pattern?: string;
    patternMessage?: string;
    minItems?: number;
    maxItems?: number;
    uniqueItems?: boolean;
    message?: string;
  };
  options?: string[] | { value: string; label: string }[];
  enum?: string[];
  group?: string;
  order?: number;
  component?: string;
  componentProps?: Record<string, unknown>;
  // File config
  fileConfig?: {
    accept?: string;
    maxSize?: number;
    maxSizeLabel?: string;
    inputFormat?: string;
    maxCount?: number;
    minCount?: number;
    maxWidth?: number;
    maxHeight?: number;
    maxDuration?: number;
  };
  // Visibility & conditional
  visible?: boolean;
  disabled?: boolean;
  dependsOn?: Record<string, unknown>;
  effectType?: string;
  helpTip?: string;
  helpUrl?: string;
}

function normalizeType(raw: unknown): string {
  if (!raw) return "TEXT";
  if (typeof raw === "object" && raw !== null) {
    const obj = raw as Record<string, unknown>;
    return String(obj.value ?? obj.label ?? "TEXT");
  }
  return String(raw);
}

function parseParams(json: string): InputParam[] {
  try {
    const arr = JSON.parse(json);
    if (Array.isArray(arr))
      return arr.map((p, i) => ({
        ...p,
        type: normalizeType(p.type),
        _key: String(i) + Date.now(),
      }));
  } catch {}
  return [];
}

function isEmptyObj(v: unknown): boolean {
  return (
    v != null &&
    typeof v === "object" &&
    !Array.isArray(v) &&
    Object.values(v as Record<string, unknown>).every((x) => x == null)
  );
}

function serializeParams(params: InputParam[]): string {
  return JSON.stringify(
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    params.map(({ _key, validation, fileConfig, dependsOn, componentProps, ...rest }) => {
      const out: Record<string, unknown> = { ...rest };
      if (validation && !isEmptyObj(validation)) out.validation = validation;
      if (fileConfig && !isEmptyObj(fileConfig)) out.fileConfig = fileConfig;
      if (dependsOn && !isEmptyObj(dependsOn)) out.dependsOn = dependsOn;
      if (componentProps && !isEmptyObj(componentProps)) out.componentProps = componentProps;
      return out;
    }),
    null,
    2
  );
}

// ─── InputParamGroup types ─────────────────────────────────────────────────
// Actual API structure: { name, label, fields: string[] }

export interface InputParamGroup {
  _key?: string;
  name: string;
  label?: string;
  fields?: string[];
}

function parseParamGroups(json: string): InputParamGroup[] {
  try {
    const arr = JSON.parse(json);
    if (Array.isArray(arr))
      return arr.map((g, i) => ({
        ...g,
        fields: Array.isArray(g.fields) ? g.fields : [],
        _key: String(i) + Date.now(),
      }));
  } catch {}
  return [];
}

function serializeParamGroups(groups: InputParamGroup[]): string {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  return JSON.stringify(groups.map(({ _key, ...rest }) => rest), null, 2);
}

// ─── TypeSelect ────────────────────────────────────────────────────────────

function TypeSelect({
  value,
  onChange,
}: {
  value: string;
  onChange: (v: string) => void;
}) {
  type FlatEntry =
    | { kind: "header"; id: string; title: string }
    | { kind: "sep"; id: string }
    | { kind: "item"; value: string; label: string };

  const flatEntries: FlatEntry[] = [
    { kind: "header", id: "hdr-basic", title: "基础类型" },
    ...BASIC_PARAM_TYPES.map((t) => ({ kind: "item" as const, value: t.value, label: t.label })),
    { kind: "sep", id: "sep-1" },
    { kind: "header", id: "hdr-file", title: "文件类型" },
    ...FILE_PARAM_TYPES.map((t) => ({ kind: "item" as const, value: t.value, label: t.label })),
    { kind: "sep", id: "sep-2" },
    { kind: "header", id: "hdr-list", title: "列表类型" },
    ...LIST_PARAM_TYPES.map((t) => ({ kind: "item" as const, value: t.value, label: t.label })),
    { kind: "sep", id: "sep-3" },
    { kind: "header", id: "hdr-entity", title: "实体引用 (单个)" },
    ...ENTITY_PARAM_TYPES.map((t) => ({ kind: "item" as const, value: t.value, label: t.label })),
    { kind: "sep", id: "sep-4" },
    { kind: "header", id: "hdr-entity-list", title: "实体引用 (列表)" },
    ...ENTITY_LIST_PARAM_TYPES.map((t) => ({ kind: "item" as const, value: t.value, label: t.label })),
  ];

  return (
    <Select
      value={value}
      onChange={(key) => {
        if (key && !Array.isArray(key)) onChange(String(key));
      }}
    >
      <Select.Trigger className="h-7 min-w-[160px] text-xs">
        <Select.Value>
          {() => <span className="text-xs">{typeLabel(value)}</span>}
        </Select.Value>
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover>
        <ListBox>
          {flatEntries.map((entry) => {
            if (entry.kind === "header") {
              return (
                <ListBox.Item
                  key={entry.id}
                  id={entry.id}
                  isDisabled
                  textValue={entry.title}
                  className="pointer-events-none px-2 pt-2 pb-0.5 text-[10px] font-semibold uppercase tracking-wider text-muted"
                >
                  {entry.title}
                </ListBox.Item>
              );
            }
            if (entry.kind === "sep") {
              return (
                <ListBox.Item
                  key={entry.id}
                  id={entry.id}
                  isDisabled
                  textValue=""
                  className="pointer-events-none my-1 h-px bg-border p-0"
                />
              );
            }
            return (
              <ListBox.Item key={entry.value} id={entry.value} textValue={entry.label}>
                <span className="text-xs">{entry.label}</span>
                <ListBox.ItemIndicator />
              </ListBox.Item>
            );
          })}
        </ListBox>
      </Select.Popover>
    </Select>
  );
}

// ─── EnumInput ─────────────────────────────────────────────────────────────
// Reusable chip input for enum/options arrays

function ChipInput({
  label,
  values,
  onChange,
  placeholder,
}: {
  label: string;
  values: string[];
  onChange: (v: string[]) => void;
  placeholder?: string;
}) {
  const [input, setInput] = useState("");

  const add = (val: string) => {
    const v = val.trim();
    if (v && !values.includes(v)) onChange([...values, v]);
    setInput("");
  };

  return (
    <div>
      <label className="mb-1 block text-[10px] font-medium text-muted">{label}</label>
      <div className="mb-1.5 flex flex-wrap gap-1.5">
        {values.map((opt, i) => (
          <span
            key={i}
            className="flex items-center gap-1 rounded-full border border-[#3a3a3a] bg-[#1e1e1e] px-2 py-0.5 text-xs text-[#9e9e9e]"
          >
            {opt}
            <button
              type="button"
              onClick={() => onChange(values.filter((_, j) => j !== i))}
              className="text-[#555] hover:text-red-400"
            >
              <X className="size-3" />
            </button>
          </span>
        ))}
      </div>
      <div className="flex gap-1.5">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") { e.preventDefault(); add(input); }
          }}
          placeholder={placeholder ?? "输入值，回车确认"}
          className="flex-1 rounded-md border border-[#3a3a3a] bg-[#1e1e1e] px-2 py-1.5 text-xs text-[#d4d4d4] placeholder:text-[#555] focus:outline-none focus:ring-1 focus:ring-accent/40"
        />
        <button
          type="button"
          onClick={() => add(input)}
          className="rounded-md border border-[#3a3a3a] bg-[#252526] px-2 py-1 text-xs text-[#858585] hover:text-[#ccc]"
        >
          <Plus className="size-3" />
        </button>
      </div>
    </div>
  );
}

// ─── ExpandSection ────────────────────────────────────────────────────────

function ExpandSection({
  title,
  icon: Icon,
  children,
  defaultOpen = true,
}: {
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  children: React.ReactNode;
  defaultOpen?: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-t border-border/30">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-1.5 px-3 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted hover:text-foreground"
      >
        <Icon className="size-3" />
        {title}
        <ChevronRight
          className={`ml-auto size-3 transition-transform ${open ? "rotate-90" : ""}`}
        />
      </button>
      {open && <div className="space-y-2 px-3 pb-3">{children}</div>}
    </div>
  );
}

// ─── Field helper ─────────────────────────────────────────────────────────

const inputCls =
  "w-full rounded-md border border-border bg-background px-2 py-1.5 text-xs focus:outline-none focus:ring-1 focus:ring-accent/50";

function FieldInput({
  label,
  value,
  onChange,
  placeholder,
  type = "text",
  mono = false,
}: {
  label: string;
  value: string | number | undefined | null;
  onChange: (v: string) => void;
  placeholder?: string;
  type?: string;
  mono?: boolean;
}) {
  return (
    <div>
      <label className="mb-1 block text-[10px] font-medium text-muted">
        {label}
      </label>
      <input
        type={type}
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={`${inputCls}${mono ? " font-mono" : ""}`}
      />
    </div>
  );
}

// ─── Type category helpers ────────────────────────────────────────────────

const isFileType = (t: string) =>
  FILE_PARAM_TYPES.some((f) => f.value === t) ||
  ["IMAGE_LIST", "VIDEO_LIST", "AUDIO_LIST", "DOCUMENT_LIST"].includes(t);
const isListType = (t: string) => LIST_PARAM_TYPES.some((f) => f.value === t) ||
  ENTITY_LIST_PARAM_TYPES.some((f) => f.value === t);
const isImageType = (t: string) => t === "IMAGE" || t === "IMAGE_LIST";
const isMediaType = (t: string) =>
  ["VIDEO", "AUDIO", "VIDEO_LIST", "AUDIO_LIST"].includes(t);

// ─── ParamCard ────────────────────────────────────────────────────────────

function ParamCard({
  param,
  index,
  onChange,
  onDelete,
  onDragStart,
  onDragOver,
  onDrop,
  onDragEnd,
  isDragOver,
}: {
  param: InputParam;
  index: number;
  onChange: (p: InputParam) => void;
  onDelete: () => void;
  onDragStart: () => void;
  onDragOver: (e: React.DragEvent) => void;
  onDrop: () => void;
  onDragEnd: () => void;
  isDragOver: boolean;
}) {
  const [expanded, setExpanded] = useState(false);

  const isNumber = ["NUMBER", "INTEGER"].includes(param.type);
  const isText = ["TEXT", "TEXTAREA", "STRING"].includes(param.type);
  const isSelect = param.type === "SELECT";
  const hasEnum = ["STRING", "SELECT"].includes(param.type);
  const isFile = isFileType(param.type);
  const isList = isListType(param.type);
  const showValidation = isNumber || isText || hasEnum || isList;

  const upd = (patch: Partial<InputParam>) => onChange({ ...param, ...patch });
  const updValidation = (patch: Record<string, unknown>) =>
    upd({ validation: { ...param.validation, ...patch } });
  const updFileConfig = (patch: Record<string, unknown>) =>
    upd({ fileConfig: { ...param.fileConfig, ...patch } });

  return (
    <div
      onDragOver={onDragOver}
      onDrop={onDrop}
      className={`rounded-lg border bg-background transition-all ${
        isDragOver
          ? "border-accent opacity-60 shadow-md"
          : expanded
          ? "border-accent/30 shadow-sm"
          : "border-border"
      }`}
    >
      {/* Header row */}
      <div className="flex items-center gap-2 px-3 py-2">
        {/* Grip */}
        <div
          draggable
          onDragStart={(e) => {
            e.dataTransfer.effectAllowed = "move";
            e.dataTransfer.setDragImage(e.currentTarget, 8, 8);
            onDragStart();
          }}
          onDragEnd={onDragEnd}
          className="flex shrink-0 cursor-grab items-center active:cursor-grabbing"
        >
          <GripVertical className="size-3.5 text-muted/40" />
        </div>
        <span className="w-5 shrink-0 text-[10px] text-muted/60 tabular-nums">
          {index + 1}
        </span>

        {/* Name + subtitle */}
        <div className="min-w-0 flex-1">
          <input
            draggable={false}
            value={param.name}
            onChange={(e) => upd({ name: e.target.value })}
            placeholder="参数名 (name)"
            className="w-full bg-transparent font-mono text-xs font-medium text-foreground placeholder:text-muted/50 focus:outline-none"
          />
          {!expanded && (param.label || param.group) && (
            <div className="mt-0.5 flex items-center gap-1.5 text-[10px] text-muted">
              {param.label && (
                <span className="truncate">{param.label}</span>
              )}
              {param.group && (
                <span className="shrink-0 rounded bg-[#2a2a2e] px-1 py-0.5 font-mono text-[9px] text-[#888]">
                  {param.group}
                </span>
              )}
            </div>
          )}
        </div>

        {/* Type select + badge */}
        <TypeSelect value={param.type} onChange={(v) => upd({ type: v })} />
        <span
          className={`hidden shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium lg:inline ${typeBadgeColor(param.type)}`}
        >
          {String(param.type)}
        </span>

        {/* Required */}
        <div className="flex shrink-0 items-center gap-1">
          <span className="text-[10px] text-muted">必填</span>
          <Switch
            isSelected={param.required ?? false}
            onChange={(v) => upd({ required: v })}
            className="scale-75"
          />
        </div>

        {/* Expand/Delete */}
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="shrink-0 rounded p-0.5 text-muted hover:text-foreground"
        >
          {expanded ? (
            <ChevronDown className="size-3.5" />
          ) : (
            <ChevronRight className="size-3.5" />
          )}
        </button>
        <button
          type="button"
          onClick={onDelete}
          className="shrink-0 rounded p-0.5 text-muted hover:text-red-500"
        >
          <Trash2 className="size-3.5" />
        </button>
      </div>

      {/* Expanded sections */}
      {expanded && (
        <>
          {/* ── Section A: 基本属性 ── */}
          <ExpandSection title="基本属性" icon={Settings2}>
            <div className="grid grid-cols-2 gap-2">
              <FieldInput
                label="显示名称 (label)"
                value={param.label ?? ""}
                onChange={(v) => upd({ label: v })}
                placeholder="用户可见标签"
              />
              <FieldInput
                label="英文名称 (labelEn)"
                value={param.labelEn ?? ""}
                onChange={(v) => upd({ labelEn: v || undefined })}
                placeholder="English label"
              />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <FieldInput
                label="占位符 (placeholder)"
                value={param.placeholder ?? ""}
                onChange={(v) => upd({ placeholder: v })}
                placeholder="输入提示文字"
              />
              <FieldInput
                label="帮助提示 (helpTip)"
                value={param.helpTip ?? ""}
                onChange={(v) => upd({ helpTip: v || undefined })}
                placeholder="鼠标悬停提示"
              />
            </div>
            <FieldInput
              label="描述 (description)"
              value={param.description ?? ""}
              onChange={(v) => upd({ description: v })}
              placeholder="参数用途说明"
            />
            <div className="grid grid-cols-3 gap-2">
              <FieldInput
                label="默认值 (default)"
                value={
                  param.default != null
                    ? String(param.default)
                    : param.defaultValue != null
                    ? String(param.defaultValue)
                    : ""
                }
                onChange={(v) =>
                  upd({
                    default: v || undefined,
                    defaultValue: v || undefined,
                  })
                }
                placeholder="默认值"
              />
              <FieldInput
                label="分组 (group)"
                value={param.group ?? ""}
                onChange={(v) => upd({ group: v || undefined })}
                placeholder="basic / advanced"
                mono
              />
              <FieldInput
                label="排序 (order)"
                value={param.order ?? ""}
                onChange={(v) =>
                  upd({ order: v ? Number(v) : undefined })
                }
                placeholder="0"
                type="number"
              />
            </div>
          </ExpandSection>

          {/* ── Section B: 校验规则 ── */}
          {showValidation && (
            <ExpandSection title="校验规则" icon={ShieldCheck}>
              {/* NUMBER/INTEGER */}
              {isNumber && (
                <div className="grid grid-cols-4 gap-2">
                  <FieldInput
                    label="最小值"
                    value={param.validation?.min ?? param.min ?? ""}
                    onChange={(v) => {
                      const n = v ? Number(v) : undefined;
                      upd({ min: n });
                      updValidation({ min: n });
                    }}
                    type="number"
                  />
                  <FieldInput
                    label="最大值"
                    value={param.validation?.max ?? param.max ?? ""}
                    onChange={(v) => {
                      const n = v ? Number(v) : undefined;
                      upd({ max: n });
                      updValidation({ max: n });
                    }}
                    type="number"
                  />
                  <FieldInput
                    label="步进"
                    value={param.validation?.step ?? param.step ?? ""}
                    onChange={(v) => {
                      const n = v ? Number(v) : undefined;
                      upd({ step: n });
                      updValidation({ step: n });
                    }}
                    type="number"
                  />
                  <FieldInput
                    label="精度"
                    value={param.validation?.precision ?? ""}
                    onChange={(v) =>
                      updValidation({ precision: v ? Number(v) : undefined })
                    }
                    type="number"
                    placeholder="小数位数"
                  />
                </div>
              )}
              {/* TEXT/TEXTAREA/STRING */}
              {isText && (
                <>
                  <div className="grid grid-cols-2 gap-2">
                    <FieldInput
                      label="最小长度"
                      value={param.validation?.minLength ?? param.minLength ?? ""}
                      onChange={(v) => {
                        const n = v ? Number(v) : undefined;
                        upd({ minLength: n });
                        updValidation({ minLength: n });
                      }}
                      type="number"
                    />
                    <FieldInput
                      label="最大长度"
                      value={param.validation?.maxLength ?? param.maxLength ?? ""}
                      onChange={(v) => {
                        const n = v ? Number(v) : undefined;
                        upd({ maxLength: n });
                        updValidation({ maxLength: n });
                      }}
                      type="number"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <FieldInput
                      label="正则校验 (pattern)"
                      value={param.validation?.pattern ?? ""}
                      onChange={(v) =>
                        updValidation({ pattern: v || undefined })
                      }
                      placeholder="^[a-zA-Z]+$"
                      mono
                    />
                    <FieldInput
                      label="校验失败提示"
                      value={param.validation?.patternMessage ?? ""}
                      onChange={(v) =>
                        updValidation({ patternMessage: v || undefined })
                      }
                      placeholder="格式不正确"
                    />
                  </div>
                </>
              )}
              {/* LIST types */}
              {isList && (
                <div className="grid grid-cols-3 gap-2">
                  <FieldInput
                    label="最少项数"
                    value={param.validation?.minItems ?? ""}
                    onChange={(v) =>
                      updValidation({ minItems: v ? Number(v) : undefined })
                    }
                    type="number"
                  />
                  <FieldInput
                    label="最多项数"
                    value={param.validation?.maxItems ?? ""}
                    onChange={(v) =>
                      updValidation({ maxItems: v ? Number(v) : undefined })
                    }
                    type="number"
                  />
                  <div className="flex items-end gap-1.5 pb-0.5">
                    <span className="text-[10px] text-muted">元素唯一</span>
                    <Switch
                      isSelected={param.validation?.uniqueItems ?? false}
                      onChange={(v) => updValidation({ uniqueItems: v || undefined })}
                      className="scale-75"
                    />
                  </div>
                </div>
              )}
              {/* SELECT/STRING enum */}
              {hasEnum && (
                <ChipInput
                  label={
                    isSelect
                      ? "选项 (options — value:label 格式)"
                      : "枚举值 (enum)"
                  }
                  values={
                    isSelect
                      ? (param.options ?? []).map((o) =>
                          typeof o === "string"
                            ? o
                            : `${o.value}:${o.label}`
                        )
                      : (param.enum ?? [])
                  }
                  onChange={(v) => {
                    if (isSelect) {
                      // If any item has ":" format, store all as objects
                      const hasColonFormat = v.some((s) => s.indexOf(":") > 0);
                      if (hasColonFormat) {
                        const parsed: { value: string; label: string }[] =
                          v.map((s) => {
                            const idx = s.indexOf(":");
                            if (idx > 0)
                              return {
                                value: s.slice(0, idx),
                                label: s.slice(idx + 1),
                              };
                            return { value: s, label: s };
                          });
                        upd({ options: parsed });
                      } else {
                        upd({ options: v });
                      }
                    } else {
                      upd({ enum: v });
                    }
                  }}
                  placeholder={
                    isSelect
                      ? "value:label 格式，回车确认"
                      : "输入选项值，回车确认"
                  }
                />
              )}
            </ExpandSection>
          )}

          {/* ── Section C: 文件配置 ── */}
          {isFile && (
            <ExpandSection title="文件配置" icon={FileCheck}>
              <FieldInput
                label="允许类型 (accept)"
                value={param.fileConfig?.accept ?? ""}
                onChange={(v) => updFileConfig({ accept: v || undefined })}
                placeholder="image/png,image/jpeg,image/webp"
                mono
              />
              <div className="grid grid-cols-3 gap-2">
                <FieldInput
                  label="最大大小 (bytes)"
                  value={param.fileConfig?.maxSize ?? ""}
                  onChange={(v) =>
                    updFileConfig({
                      maxSize: v ? Number(v) : undefined,
                    })
                  }
                  type="number"
                  placeholder="10485760"
                />
                <FieldInput
                  label="大小标签"
                  value={param.fileConfig?.maxSizeLabel ?? ""}
                  onChange={(v) =>
                    updFileConfig({ maxSizeLabel: v || undefined })
                  }
                  placeholder="10MB"
                />
                <div>
                  <label className="mb-1 block text-[10px] font-medium text-muted">
                    输入格式
                  </label>
                  <select
                    value={param.fileConfig?.inputFormat ?? "URL"}
                    onChange={(e) =>
                      updFileConfig({ inputFormat: e.target.value })
                    }
                    className={inputCls}
                  >
                    <option value="URL">URL</option>
                    <option value="BASE64">BASE64</option>
                    <option value="BOTH">BOTH</option>
                  </select>
                </div>
              </div>
              {isImageType(param.type) && (
                <div className="grid grid-cols-2 gap-2">
                  <FieldInput
                    label="最大宽度 (px)"
                    value={param.fileConfig?.maxWidth ?? ""}
                    onChange={(v) =>
                      updFileConfig({
                        maxWidth: v ? Number(v) : undefined,
                      })
                    }
                    type="number"
                  />
                  <FieldInput
                    label="最大高度 (px)"
                    value={param.fileConfig?.maxHeight ?? ""}
                    onChange={(v) =>
                      updFileConfig({
                        maxHeight: v ? Number(v) : undefined,
                      })
                    }
                    type="number"
                  />
                </div>
              )}
              {isMediaType(param.type) && (
                <FieldInput
                  label="最大时长 (秒)"
                  value={param.fileConfig?.maxDuration ?? ""}
                  onChange={(v) =>
                    updFileConfig({
                      maxDuration: v ? Number(v) : undefined,
                    })
                  }
                  type="number"
                />
              )}
              {param.type.endsWith("_LIST") && (
                <div className="grid grid-cols-2 gap-2">
                  <FieldInput
                    label="最少文件数"
                    value={param.fileConfig?.minCount ?? ""}
                    onChange={(v) =>
                      updFileConfig({
                        minCount: v ? Number(v) : undefined,
                      })
                    }
                    type="number"
                  />
                  <FieldInput
                    label="最多文件数"
                    value={param.fileConfig?.maxCount ?? ""}
                    onChange={(v) =>
                      updFileConfig({
                        maxCount: v ? Number(v) : undefined,
                      })
                    }
                    type="number"
                  />
                </div>
              )}
            </ExpandSection>
          )}

          {/* ── Section D: 条件依赖 ── */}
          <ExpandSection title="条件依赖" icon={Link2} defaultOpen={false}>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="mb-1 block text-[10px] font-medium text-muted">
                  生效方式 (effectType)
                </label>
                <select
                  value={param.effectType ?? ""}
                  onChange={(e) =>
                    upd({ effectType: e.target.value || undefined })
                  }
                  className={inputCls}
                >
                  <option value="">— 不设置 —</option>
                  <option value="visibility">visibility (满足时显示)</option>
                  <option value="hidden">hidden (满足时隐藏)</option>
                  <option value="disabled">disabled (满足时禁用)</option>
                  <option value="required">required (满足时必填)</option>
                </select>
              </div>
              <FieldInput
                label="自定义组件 (component)"
                value={param.component ?? ""}
                onChange={(v) => upd({ component: v || undefined })}
                placeholder="EntitySelect"
                mono
              />
            </div>
            <div>
              <label className="mb-1 block text-[10px] font-medium text-muted">
                dependsOn (JSON)
              </label>
              <textarea
                rows={3}
                value={
                  param.dependsOn
                    ? JSON.stringify(param.dependsOn, null, 2)
                    : ""
                }
                onChange={(e) => {
                  const v = e.target.value.trim();
                  if (!v) {
                    upd({ dependsOn: undefined });
                    return;
                  }
                  try {
                    upd({ dependsOn: JSON.parse(v) });
                  } catch {
                    /* let user keep typing */
                  }
                }}
                placeholder='{"field": "hd", "operator": "eq", "value": true}'
                className={`${inputCls} resize-y font-mono`}
              />
            </div>
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-1.5">
                <span className="text-[10px] text-muted">visible</span>
                <Switch
                  isSelected={param.visible ?? true}
                  onChange={(v) => upd({ visible: v })}
                  className="scale-75"
                />
              </div>
              <div className="flex items-center gap-1.5">
                <span className="text-[10px] text-muted">disabled</span>
                <Switch
                  isSelected={param.disabled ?? false}
                  onChange={(v) => upd({ disabled: v || undefined })}
                  className="scale-75"
                />
              </div>
            </div>
          </ExpandSection>
        </>
      )}
    </div>
  );
}

// ─── ParamGroupCard ────────────────────────────────────────────────────────

function ParamGroupCard({
  group,
  onChange,
  onDelete,
  availableParamNames,
}: {
  group: InputParamGroup;
  onChange: (g: InputParamGroup) => void;
  onDelete: () => void;
  availableParamNames: string[];
}) {
  const upd = (patch: Partial<InputParamGroup>) => onChange({ ...group, ...patch });
  const fields = group.fields ?? [];

  const addField = (name: string) => {
    if (!name || fields.includes(name)) return;
    upd({ fields: [...fields, name] });
  };

  const removeField = (name: string) => {
    upd({ fields: fields.filter((f) => f !== name) });
  };

  // Param names not yet in this group
  const suggestions = availableParamNames.filter((n) => n && !fields.includes(n));

  return (
    <div className="rounded-lg border border-[#3a3a3a] bg-[#252526] p-3 space-y-2">
      {/* Name + label row */}
      <div className="grid grid-cols-[1fr_1fr_1.75rem] items-center gap-2">
        <input
          value={group.name}
          onChange={(e) => upd({ name: e.target.value })}
          placeholder="name (英文key)"
          className="min-w-0 rounded border border-[#3a3a3a] bg-[#1e1e1e] px-2 py-1 font-mono text-xs font-medium text-[#d4d4d4] placeholder:text-[#555] focus:outline-none focus:ring-1 focus:ring-accent/40"
        />
        <input
          value={group.label ?? ""}
          onChange={(e) => upd({ label: e.target.value })}
          placeholder="label (显示名)"
          className="min-w-0 rounded border border-[#3a3a3a] bg-[#1e1e1e] px-2 py-1 text-xs text-[#9e9e9e] placeholder:text-[#555] focus:outline-none focus:ring-1 focus:ring-accent/40"
        />
        <button
          type="button"
          onClick={onDelete}
          className="flex items-center justify-center rounded p-0.5 text-[#555] hover:text-red-400"
        >
          <Trash2 className="size-3.5" />
        </button>
      </div>

      {/* Fields section */}
      <div>
        <p className="mb-1 text-[10px] text-[#666]">
          fields — 此分组包含的参数 ({fields.length})
        </p>

        {/* Current fields as chips */}
        <div className="flex flex-wrap gap-1.5">
          {fields.map((f) => (
            <span
              key={f}
              className="flex items-center gap-1 rounded-full border border-[#3a3a3a] bg-[#1e1e1e] px-2 py-0.5 font-mono text-xs text-[#9cdcfe]"
            >
              {f}
              <button
                type="button"
                onClick={() => removeField(f)}
                className="text-[#555] hover:text-red-400"
              >
                <X className="size-3" />
              </button>
            </span>
          ))}
          {fields.length === 0 && (
            <span className="text-xs text-[#555]">暂无字段</span>
          )}
        </div>

        {/* Suggestions from existing params */}
        {suggestions.length > 0 && (
          <div className="mt-2">
            <p className="mb-1 text-[10px] text-[#555]">从已有参数中添加：</p>
            <div className="flex flex-wrap gap-1">
              {suggestions.map((n) => (
                <button
                  key={n}
                  type="button"
                  onClick={() => addField(n)}
                  className="flex items-center gap-0.5 rounded-full border border-dashed border-[#3a3a3a] px-2 py-0.5 font-mono text-xs text-[#555] transition-colors hover:border-accent/50 hover:text-accent"
                >
                  <Plus className="size-2.5" />
                  {n}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ─── SchemaVisualEditor ───────────────────────────────────────────────────

interface SchemaVisualEditorProps {
  value: string;
  onChange: (value: string) => void;
  /** If provided, renders a "参数分组" section below params in visual mode */
  groupsValue?: string;
  onGroupsChange?: (v: string) => void;
}

export function SchemaVisualEditor({
  value,
  onChange,
  groupsValue,
  onGroupsChange,
}: SchemaVisualEditorProps) {
  const showGroups = groupsValue !== undefined && onGroupsChange !== undefined;

  const [mode, setMode] = useState<"visual" | "json">("visual");
  const [params, setParams] = useState<InputParam[]>(() => parseParams(value));
  const [jsonParseError, setJsonParseError] = useState<string | null>(null);

  const [groups, setGroups] = useState<InputParamGroup[]>(() =>
    showGroups ? parseParamGroups(groupsValue!) : []
  );

  // Drag-to-reorder
  const dragIdxRef = useRef<number | null>(null);
  const [dragOverIdx, setDragOverIdx] = useState<number | null>(null);

  const updateParams = useCallback(
    (next: InputParam[]) => {
      setParams(next);
      onChange(serializeParams(next));
    },
    [onChange]
  );

  const updateGroups = useCallback(
    (next: InputParamGroup[]) => {
      setGroups(next);
      onGroupsChange?.(serializeParamGroups(next));
    },
    [onGroupsChange]
  );

  const switchToJson = () => {
    onChange(serializeParams(params));
    setMode("json");
  };

  const switchToVisual = () => {
    try {
      setParams(parseParams(value));
      setJsonParseError(null);
      setMode("visual");
    } catch {
      setJsonParseError("JSON 格式错误，请修正后切换");
    }
  };

  const addParam = () =>
    updateParams([
      ...params,
      { _key: String(Date.now()), name: "", type: "TEXT", required: false },
    ]);

  const addGroup = () =>
    updateGroups([
      ...groups,
      {
        _key: String(Date.now()),
        name: `group_${groups.length + 1}`,
        label: "",
        fields: [],
      },
    ]);

  const handleDragStart = (i: number) => {
    dragIdxRef.current = i;
  };
  const handleDragOver = (e: React.DragEvent, i: number) => {
    e.preventDefault();
    setDragOverIdx(i);
  };
  const handleDrop = (i: number) => {
    const from = dragIdxRef.current;
    if (from === null || from === i) return;
    const next = [...params];
    const [item] = next.splice(from, 1);
    next.splice(i, 0, item);
    updateParams(next);
    dragIdxRef.current = null;
    setDragOverIdx(null);
  };
  const handleDragEnd = () => {
    dragIdxRef.current = null;
    setDragOverIdx(null);
  };

  // Available param names for group field picker
  const availableParamNames = params.map((p) => p.name).filter((n) => n.trim() !== "");

  return (
    <div className="flex h-full flex-col">
      {/* Mode toggle bar */}
      <div className="flex shrink-0 items-center justify-between border-b border-[#2d2d2d] bg-[#1e1e1e] px-3 py-1.5">
        <div className="flex items-center gap-1 rounded-md border border-[#3a3a3a] bg-[#252526] p-0.5">
          <button
            type="button"
            onClick={switchToVisual}
            className={`flex items-center gap-1.5 rounded px-2.5 py-1 text-[11px] font-medium transition-colors ${
              mode === "visual"
                ? "bg-[#1e1e1e] text-white shadow-sm"
                : "text-[#858585] hover:text-[#ccc]"
            }`}
          >
            <Layers className="size-3" />
            可视化
          </button>
          <button
            type="button"
            onClick={switchToJson}
            className={`flex items-center gap-1.5 rounded px-2.5 py-1 text-[11px] font-medium transition-colors ${
              mode === "json"
                ? "bg-[#1e1e1e] text-emerald-400 shadow-sm"
                : "text-[#858585] hover:text-[#ccc]"
            }`}
          >
            <Braces className="size-3" />
            JSON
          </button>
        </div>

        <div className="flex items-center gap-2">
          <span className="text-[10px] text-[#6e6e6e]">{params.length} 个参数</span>
          {mode === "visual" && (
            <button
              type="button"
              onClick={addParam}
              className="flex items-center gap-1 rounded-md border border-[#3a3a3a] bg-[#252526] px-2 py-1 text-[11px] text-[#858585] transition-colors hover:border-accent/50 hover:text-accent"
            >
              <Plus className="size-3" />
              添加参数
            </button>
          )}
        </div>
      </div>

      {/* Content area */}
      {mode === "json" ? (
        <div className="flex-1 min-h-0 overflow-hidden">
          <CodeEditor
            value={value}
            onChange={onChange}
            lang="json"
            placeholder='[\n  { "name": "prompt", "type": "TEXT", "required": true }\n]'
          />
        </div>
      ) : (
        <div
          className="flex-1 min-h-0 overflow-y-auto p-3 space-y-2"
          style={{ scrollbarWidth: "thin", background: "#1c1c1e" }}
        >
          {jsonParseError && (
            <div className="flex items-center gap-2 rounded-lg border border-red-800/40 bg-red-950/20 px-3 py-2">
              <AlertCircle className="size-3.5 shrink-0 text-red-400" />
              <p className="text-xs text-red-400">{jsonParseError}</p>
            </div>
          )}

          {/* ── Params ── */}
          {params.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <Hash className="mb-2 size-8 text-[#444]" />
              <p className="text-sm text-[#666]">暂无参数</p>
              <p className="mt-1 text-xs text-[#444]">点击右上角「添加参数」开始配置</p>
            </div>
          ) : (
            params.map((param, i) => (
              <ParamCard
                key={param._key ?? i}
                param={param}
                index={i}
                onChange={(updated) => {
                  const next = [...params];
                  next[i] = updated;
                  updateParams(next);
                }}
                onDelete={() => updateParams(params.filter((_, j) => j !== i))}
                onDragStart={() => handleDragStart(i)}
                onDragOver={(e) => handleDragOver(e, i)}
                onDrop={() => handleDrop(i)}
                onDragEnd={handleDragEnd}
                isDragOver={dragOverIdx === i}
              />
            ))
          )}

          {params.length > 0 && (
            <button
              type="button"
              onClick={addParam}
              className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-dashed border-[#3a3a3a] py-2 text-xs text-[#666] transition-colors hover:border-accent/40 hover:text-accent/80"
            >
              <Plus className="size-3.5" />
              添加参数
            </button>
          )}

          {/* ── Groups section ── */}
          {showGroups && (
            <div className="mt-1 border-t border-[#333] pt-3">
              <div className="mb-2 flex items-center justify-between">
                <span className="text-[11px] font-semibold uppercase tracking-widest text-[#6e6e6e]">
                  参数分组 (inputGroups)
                </span>
                <button
                  type="button"
                  onClick={addGroup}
                  className="flex items-center gap-1 rounded-md border border-[#3a3a3a] bg-[#252526] px-2 py-1 text-[11px] text-[#858585] transition-colors hover:border-accent/50 hover:text-accent"
                >
                  <Plus className="size-3" />
                  添加分组
                </button>
              </div>

              <div className="space-y-2">
                {groups.length === 0 && (
                  <p className="py-2 text-xs text-[#555]">暂无分组，点击「添加分组」创建</p>
                )}
                {groups.map((g, i) => (
                  <ParamGroupCard
                    key={g._key ?? i}
                    group={g}
                    onChange={(updated) => {
                      const next = [...groups];
                      next[i] = updated;
                      updateGroups(next);
                    }}
                    onDelete={() => updateGroups(groups.filter((_, j) => j !== i))}
                    availableParamNames={availableParamNames}
                  />
                ))}
              </div>
            </div>
          )}

          <div className="h-2" />
        </div>
      )}
    </div>
  );
}
