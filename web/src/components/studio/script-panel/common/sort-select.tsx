"use client";

/**
 * Sort Buttons
 * Independent icon buttons for sorting by different fields. Each button cycles
 * inactive → asc → desc → inactive. Multiple buttons can be active;
 * the order of activation defines the comparator chain.
 */

import { Button, Tooltip } from "@heroui/react";
import {
  ArrowDown01,
  ArrowDownAZ,
  ArrowUp01,
  ArrowUpAZ,
  CalendarArrowDown,
  CalendarArrowUp,
  ClockArrowDown,
  ClockArrowUp,
} from "lucide-react";

export type SortField = "name" | "createdAt" | "updatedAt" | "sequence";
export type SortOrder = "asc" | "desc";
export type SortKey = `${SortField}_${SortOrder}`;

export interface SortItem {
  title?: string;
  name?: string;
  createdAt?: string | number | Date | null;
  updatedAt?: string | number | Date | null;
  sequence?: number | null;
}

const FIELD_ICON: Record<SortField, Record<SortOrder, React.ComponentType<{ className?: string }>>> = {
  name: { asc: ArrowDownAZ, desc: ArrowUpAZ },
  createdAt: { asc: CalendarArrowUp, desc: CalendarArrowDown },
  updatedAt: { asc: ClockArrowUp, desc: ClockArrowDown },
  sequence: { asc: ArrowDown01, desc: ArrowUp01 },
};

const FIELD_LABEL: Record<SortField, string> = {
  name: "名称",
  createdAt: "创建时间",
  updatedAt: "更新时间",
  sequence: "序号",
};

function splitKey(key: SortKey): [SortField, SortOrder] {
  const [field, order] = key.split("_") as [SortField, SortOrder];
  return [field, order];
}

function nextState(current: SortOrder | undefined): SortOrder | undefined {
  if (!current) return "asc";
  if (current === "asc") return "desc";
  return undefined;
}

interface SortButtonsProps {
  value: SortKey[];
  onChange: (value: SortKey[]) => void;
  fields?: SortField[];
}

export function SortButtons({ value, onChange, fields = ["updatedAt", "createdAt", "name"] }: SortButtonsProps) {
  const getOrder = (field: SortField): SortOrder | undefined => {
    const match = value.find((k) => splitKey(k)[0] === field);
    return match ? splitKey(match)[1] : undefined;
  };

  const toggle = (field: SortField) => {
    const order = getOrder(field);
    const next = nextState(order);
    const withoutField = value.filter((k) => splitKey(k)[0] !== field);
    if (!next) {
      onChange(withoutField);
    } else {
      onChange([...withoutField, `${field}_${next}` as SortKey]);
    }
  };

  return (
    <div className="flex items-center gap-0.5">
      {fields.map((field) => {
        const order = getOrder(field);
        const Icon = FIELD_ICON[field][order ?? "asc"];
        const priority = order ? value.findIndex((k) => splitKey(k)[0] === field) + 1 : 0;
        const isActive = !!order;
        const orderLabel = order === "asc" ? "升序" : order === "desc" ? "降序" : "";
        return (
          <Tooltip key={field} delay={0}>
            <Button
              variant={isActive ? "primary" : "ghost"}
              size="sm"
              isIconOnly
              aria-label={`按${FIELD_LABEL[field]}排序`}
              className="relative"
              onPress={() => toggle(field)}
            >
              <Icon className="size-4" />
              {priority > 1 && (
                <span className="absolute -right-1 -top-1 flex size-3.5 items-center justify-center rounded-full bg-accent text-[9px] font-semibold text-white">
                  {priority}
                </span>
              )}
            </Button>
            <Tooltip.Content>
              {FIELD_LABEL[field]}
              {orderLabel && ` · ${orderLabel}`}
            </Tooltip.Content>
          </Tooltip>
        );
      })}
    </div>
  );
}

function toTime(value: string | number | Date | null | undefined): number {
  if (value == null) return 0;
  const t = new Date(value).getTime();
  return Number.isNaN(t) ? 0 : t;
}

function compareByField<T extends SortItem>(a: T, b: T, field: SortField): number {
  const getName = (item: T) => (item.title ?? item.name ?? "").toString();
  switch (field) {
    case "name":
      return getName(a).localeCompare(getName(b));
    case "createdAt":
      return toTime(a.createdAt) - toTime(b.createdAt);
    case "updatedAt":
      return toTime(a.updatedAt) - toTime(b.updatedAt);
    case "sequence":
      return (a.sequence ?? 0) - (b.sequence ?? 0);
    default:
      return 0;
  }
}

export function sortItems<T extends SortItem>(items: T[], keys: SortKey[]): T[] {
  if (keys.length === 0) return items;
  return [...items].sort((a, b) => {
    for (const key of keys) {
      const [field, order] = splitKey(key);
      const cmp = compareByField(a, b, field) * (order === "asc" ? 1 : -1);
      if (cmp !== 0) return cmp;
    }
    return 0;
  });
}
