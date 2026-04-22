"use client";

import { RadioGroup, Radio, Label } from "@heroui/react";
import type { ScriptStatus } from "@/lib/api/dto";

interface StatusFilterProps {
  value: ScriptStatus | "ALL";
  onChange: (value: ScriptStatus | "ALL") => void;
  counts?: {
    all: number;
    draft: number;
    inProgress: number;
    completed: number;
  };
}

const STATUS_OPTIONS = [
  { value: "ALL" as const, label: "全部" },
  { value: "DRAFT" as const, label: "草稿" },
  { value: "IN_PROGRESS" as const, label: "进行中" },
  { value: "COMPLETED" as const, label: "已完成" },
];

export function StatusFilter({ value, onChange, counts }: StatusFilterProps) {
  const getCount = (status: ScriptStatus | "ALL") => {
    if (!counts) return undefined;
    switch (status) {
      case "ALL":
        return counts.all;
      case "DRAFT":
        return counts.draft;
      case "IN_PROGRESS":
        return counts.inProgress;
      case "COMPLETED":
        return counts.completed;
      default:
        return undefined;
    }
  };

  return (
    <div className="px-3">
      <h3 className="mb-2 px-2 text-xs font-medium uppercase tracking-wide text-muted">
        状态筛选
      </h3>
      <RadioGroup
        value={value}
        onChange={(newValue) => onChange(newValue as ScriptStatus | "ALL")}
        className="gap-0.5"
      >
        {STATUS_OPTIONS.map((option) => {
          const count = getCount(option.value);
          return (
            <Radio
              key={option.value}
              value={option.value}
              className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm data-[selected=true]:bg-accent/10"
            >
              <Radio.Control>
                <Radio.Indicator />
              </Radio.Control>
              <Radio.Content className="flex flex-1 flex-row items-center justify-between">
                <Label className="cursor-pointer">{option.label}</Label>
                {count !== undefined && (
                  <span className="text-xs text-muted">({count})</span>
                )}
              </Radio.Content>
            </Radio>
          );
        })}
      </RadioGroup>
    </div>
  );
}
