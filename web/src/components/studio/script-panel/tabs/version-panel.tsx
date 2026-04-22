"use client";

/**
 * Version Panel Component
 * Displays version history with view, compare and restore functionality
 */

import { useState, useEffect } from "react";
import { Button, Tooltip, toast } from "@heroui/react";
import { Eye, X, GitCompare, RotateCcw, Loader2, ArrowLeft } from "lucide-react";
import { useLocale } from "next-intl";
import { projectService, getErrorFromException } from "@/lib/api";
import type { VersionInfoDTO, ScriptVersionDetailDTO, VersionDiffDTO } from "@/lib/api/dto";
import { useWorkspace } from "@/components/providers/workspace-provider";
import { StatusBadge, VersionListSkeleton } from "../common";

interface VersionPanelProps {
  scriptId: string;
  onRestore: () => void;
  onClose: () => void;
}

export function VersionPanel({ scriptId, onRestore, onClose }: VersionPanelProps) {
  const { currentWorkspaceId } = useWorkspace();
  const locale = useLocale();
  const [versions, setVersions] = useState<VersionInfoDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedVersion, setSelectedVersion] = useState<ScriptVersionDetailDTO | null>(null);
  const [compareVersions, setCompareVersions] = useState<{ v1: number; v2: number } | null>(null);
  const [diffResult, setDiffResult] = useState<VersionDiffDTO | null>(null);
  const [isRestoring, setIsRestoring] = useState(false);
  const [viewMode, setViewMode] = useState<"list" | "detail" | "diff">("list");

  useEffect(() => {
    const fetchVersions = async () => {
      if (!currentWorkspaceId || !scriptId) return;
      try {
        setIsLoading(true);
        const data = await projectService.getScriptVersions( scriptId);
        setVersions(data);
      } catch (err) {
        console.error("Failed to fetch versions:", err);
        toast.danger(getErrorFromException(err, locale));
      } finally {
        setIsLoading(false);
      }
    };
    fetchVersions();
  }, [currentWorkspaceId, scriptId]);

  const handleViewVersion = async (versionNumber: number) => {
    if (!currentWorkspaceId) return;
    try {
      const detail = await projectService.getScriptVersion( scriptId, versionNumber);
      setSelectedVersion(detail);
      setViewMode("detail");
    } catch (err) {
      console.error("Failed to fetch version detail:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  };

  const handleCompare = async (v1: number, v2: number) => {
    if (!currentWorkspaceId) return;
    try {
      setCompareVersions({ v1, v2 });
      const diff = await projectService.compareScriptVersions( scriptId, v1, v2);
      setDiffResult(diff);
      setViewMode("diff");
    } catch (err) {
      console.error("Failed to compare versions:", err);
      toast.danger(getErrorFromException(err, locale));
    }
  };

  const handleRestore = async (versionNumber: number) => {
    if (!currentWorkspaceId) return;
    try {
      setIsRestoring(true);
      await projectService.restoreScriptVersion( scriptId, {
        versionNumber,
        reason: `恢复到版本 ${versionNumber}`,
      });
      onRestore();
      onClose();
    } catch (err) {
      console.error("Failed to restore version:", err);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setIsRestoring(false);
    }
  };

  const formatDateTime = (dateStr: string) => {
    return new Date(dateStr).toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  if (isLoading) {
    return <VersionListSkeleton />;
  }

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex shrink-0 items-center justify-between border-b border-muted/10 px-4 py-3">
        <div className="flex items-center gap-2">
          {viewMode !== "list" && (
            <Button
              variant="ghost"
              size="sm"
              isIconOnly
              className="size-7"
              onPress={() => {
                setViewMode("list");
                setSelectedVersion(null);
                setDiffResult(null);
              }}
            >
              <ArrowLeft className="size-4" />
            </Button>
          )}
          <h3 className="text-sm font-semibold">
            {viewMode === "list" && "版本历史"}
            {viewMode === "detail" && `版本 ${selectedVersion?.versionNumber} 详情`}
            {viewMode === "diff" && `版本对比 (v${compareVersions?.v1} → v${compareVersions?.v2})`}
          </h3>
        </div>
        <Button variant="ghost" size="sm" isIconOnly className="size-7" onPress={onClose}>
          <X className="size-4" />
        </Button>
      </div>

      {/* Content */}
      <div className="min-h-0 flex-1 overflow-auto">
        {viewMode === "list" && (
          <div className="space-y-2 p-4">
            {versions.map((version, index) => (
              <div
                key={version.id}
                className={`rounded-xl p-3 transition-all ${
                  version.isCurrent ? "bg-accent/10 ring-1 ring-accent/30" : "bg-muted/5 hover:bg-muted/10"
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium">v{version.versionNumber}</span>
                      {version.isCurrent && (
                        <span className="rounded-full bg-accent-soft-hover px-1.5 py-0.5 text-[10px] font-medium text-accent">
                          当前版本
                        </span>
                      )}
                    </div>
                    <p className="mt-1 text-xs text-muted">
                      {version.changeSummary || "无变更说明"}
                    </p>
                    <p className="mt-1 text-[11px] text-muted/70">
                      {formatDateTime(version.createdAt)}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <Tooltip delay={0}>
                      <Button
                        variant="ghost"
                        size="sm"
                        isIconOnly
                        className="size-7"
                        onPress={() => handleViewVersion(version.versionNumber)}
                      >
                        <Eye className="size-3.5" />
                      </Button>
                      <Tooltip.Content>查看详情</Tooltip.Content>
                    </Tooltip>
                    {index < versions.length - 1 && (
                      <Tooltip delay={0}>
                        <Button
                          variant="ghost"
                          size="sm"
                          isIconOnly
                          className="size-7"
                          onPress={() => handleCompare(versions[index + 1].versionNumber, version.versionNumber)}
                        >
                          <GitCompare className="size-3.5" />
                        </Button>
                        <Tooltip.Content>与上一版本对比</Tooltip.Content>
                      </Tooltip>
                    )}
                    {!version.isCurrent && (
                      <Tooltip delay={0}>
                        <Button
                          variant="ghost"
                          size="sm"
                          isIconOnly
                          className="size-7"
                          onPress={() => handleRestore(version.versionNumber)}
                          isDisabled={isRestoring}
                        >
                          {isRestoring ? (
                            <Loader2 className="size-3.5 animate-spin" />
                          ) : (
                            <RotateCcw className="size-3.5" />
                          )}
                        </Button>
                        <Tooltip.Content>恢复到此版本</Tooltip.Content>
                      </Tooltip>
                    )}
                  </div>
                </div>
              </div>
            ))}
            {versions.length === 0 && (
              <div className="py-8 text-center text-sm text-muted">暂无版本历史</div>
            )}
          </div>
        )}

        {viewMode === "detail" && selectedVersion && (
          <div className="space-y-4 p-4">
            <div className="rounded-xl bg-muted/5 p-4">
              <div className="space-y-3">
                <div>
                  <label className="text-[11px] font-medium uppercase text-muted/70">标题</label>
                  <p className="mt-1 text-sm">{selectedVersion.title}</p>
                </div>
                <div>
                  <label className="text-[11px] font-medium uppercase text-muted/70">状态</label>
                  <div className="mt-1">
                    <StatusBadge status={selectedVersion.status} />
                  </div>
                </div>
                <div>
                  <label className="text-[11px] font-medium uppercase text-muted/70">简介</label>
                  <p className="mt-1 text-sm text-muted">{selectedVersion.synopsis || "暂无简介"}</p>
                </div>
                <div>
                  <label className="text-[11px] font-medium uppercase text-muted/70">变更说明</label>
                  <p className="mt-1 text-sm text-muted">{selectedVersion.changeSummary || "无"}</p>
                </div>
                <div>
                  <label className="text-[11px] font-medium uppercase text-muted/70">创建时间</label>
                  <p className="mt-1 text-sm text-muted">{formatDateTime(selectedVersion.createdAt)}</p>
                </div>
              </div>
            </div>
            {selectedVersion.content && (
              <div className="rounded-xl bg-muted/5 p-4">
                <label className="text-[11px] font-medium uppercase text-muted/70">正文内容</label>
                <div className="prose prose-sm dark:prose-invert mt-2 max-w-none">
                  <pre className="whitespace-pre-wrap text-xs">{selectedVersion.content}</pre>
                </div>
              </div>
            )}
            {!selectedVersion.isCurrent && (
              <Button
                variant="tertiary"
                className="w-full gap-2"
                onPress={() => handleRestore(selectedVersion.versionNumber)}
                isDisabled={isRestoring}
              >
                {isRestoring ? <Loader2 className="size-4 animate-spin" /> : <RotateCcw className="size-4" />}
                恢复到此版本
              </Button>
            )}
          </div>
        )}

        {viewMode === "diff" && diffResult && (
          <div className="space-y-3 p-4">
            {diffResult.fieldDiffs.length === 0 ? (
              <div className="py-8 text-center text-sm text-muted">两个版本没有差异</div>
            ) : (
              diffResult.fieldDiffs.map((diff, index) => (
                <div key={index} className="rounded-xl bg-muted/5 p-3">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-medium">{diff.fieldLabel}</span>
                    <span
                      className={`rounded-full px-1.5 py-0.5 text-[10px] font-medium ${
                        diff.changeType === "ADDED"
                          ? "bg-success-soft-hover text-success"
                          : diff.changeType === "REMOVED"
                          ? "bg-danger-soft-hover text-danger"
                          : "bg-warning-soft-hover text-warning"
                      }`}
                    >
                      {diff.changeType === "ADDED" && "新增"}
                      {diff.changeType === "REMOVED" && "删除"}
                      {diff.changeType === "MODIFIED" && "修改"}
                    </span>
                  </div>
                  <div className="mt-2 space-y-1.5">
                    {diff.oldValue !== null && (
                      <div className="rounded-lg bg-danger/5 p-2">
                        <span className="text-[10px] font-medium text-danger/70">旧值</span>
                        <p className="mt-0.5 text-xs text-muted line-through">{diff.oldValue}</p>
                      </div>
                    )}
                    {diff.newValue !== null && (
                      <div className="rounded-lg bg-success/5 p-2">
                        <span className="text-[10px] font-medium text-success/70">新值</span>
                        <p className="mt-0.5 text-xs">{diff.newValue}</p>
                      </div>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}
