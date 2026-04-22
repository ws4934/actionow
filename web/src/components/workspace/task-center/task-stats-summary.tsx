"use client";

import { useTranslations } from "next-intl";
import { ListTodo, Loader2, CheckCircle, XCircle, TrendingUp } from "lucide-react";
import { Skeleton } from "@heroui/react";
import type { TaskStatsSummaryDTO } from "@/lib/api/dto/task.dto";

interface TaskStatsSummaryProps {
  stats: TaskStatsSummaryDTO | null;
  isLoading: boolean;
}

const STAT_ITEMS = [
  { key: "total", field: "totalTasks" as const, icon: ListTodo, color: "text-foreground" },
  { key: "running", field: "runningTasks" as const, icon: Loader2, color: "text-accent" },
  { key: "completed", field: "completedTasks" as const, icon: CheckCircle, color: "text-success" },
  { key: "failed", field: "failedTasks" as const, icon: XCircle, color: "text-danger" },
  { key: "successRate", field: "successRate" as const, icon: TrendingUp, color: "text-warning" },
];

export function TaskStatsSummary({ stats, isLoading }: TaskStatsSummaryProps) {
  const t = useTranslations("workspace.taskCenter.stats");

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {STAT_ITEMS.map(({ key, field, icon: Icon, color }) => (
        <div
          key={key}
          className="flex items-center gap-3 rounded-xl border border-border bg-background p-4"
        >
          <div className={`flex size-10 items-center justify-center rounded-lg bg-surface ${color}`}>
            <Icon className="size-5" />
          </div>
          <div>
            <p className="text-xs text-foreground-2">{t(key)}</p>
            {isLoading ? (
              <Skeleton className="mt-1 h-6 w-12 rounded" />
            ) : (
              <p className="text-lg font-semibold text-foreground">
                {stats
                  ? field === "successRate"
                    ? `${stats.successRate.toFixed(1)}%`
                    : stats[field]
                  : "—"}
              </p>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
