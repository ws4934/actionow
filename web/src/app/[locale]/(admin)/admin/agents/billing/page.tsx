"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useLocale } from "next-intl";
import { Button, Chip, ScrollShadow, SearchField, Skeleton, toast } from "@heroui/react";
import { RefreshCw } from "lucide-react";
import { adminService, getErrorFromException } from "@/lib/api";
import type { AgentBillingSessionDTO } from "@/lib/api/dto";

const PAGE_SIZE = 20;

function formatDateTime(value?: string): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function truncateId(id?: string, len = 8): string {
  if (!id) return "—";
  return id.length > len ? `${id.slice(0, len)}…` : id;
}

function formatCredits(value?: number | null): string {
  if (value == null || value === 0) return "0";
  return value.toLocaleString();
}

export default function AgentBillingPage() {
  const locale = useLocale();
  const [status, setStatus] = useState("");
  const [userId, setUserId] = useState("");
  const [workspaceId, setWorkspaceId] = useState("");

  const [records, setRecords] = useState<AgentBillingSessionDTO[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalRecords, setTotalRecords] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);

  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const requestIdRef = useRef(0);
  const loadedCount = records.length;

  const fetchPage = useCallback(
    async (pageNum: number, mode: "reset" | "append") => {
      const requestId = ++requestIdRef.current;
      if (mode === "append") setLoadingMore(true);
      else setLoading(true);

      try {
        const page = await adminService.getAgentBillingPage({
          current: pageNum,
          size: PAGE_SIZE,
          status: status.trim() || undefined,
          userId: userId.trim() || undefined,
          workspaceId: workspaceId.trim() || undefined,
        });

        if (requestId !== requestIdRef.current) return;

        setTotalRecords(page.total);
        setCurrentPage(page.current);
        setHasMore(page.current < page.pages);

        if (mode === "reset") {
          setRecords(page.records);
          return;
        }

        setRecords((prev) => {
          const existed = new Set(prev.map((item) => item.id));
          const merged = [...prev];
          for (const item of page.records) {
            if (!existed.has(item.id)) { merged.push(item); existed.add(item.id); }
          }
          return merged;
        });
      } catch (error) {
        if (requestId === requestIdRef.current) {
          toast.danger(getErrorFromException(error, locale));
          setHasMore(false);
        }
      } finally {
        if (requestId === requestIdRef.current) { setLoading(false); setLoadingMore(false); }
      }
    },
    [locale, status, userId, workspaceId]
  );

  const loadFirstPage = useCallback(() => {
    setRecords([]);
    setCurrentPage(0);
    setTotalRecords(0);
    setHasMore(true);
    void fetchPage(1, "reset");
  }, [fetchPage]);

  const loadNextPage = useCallback(() => {
    if (loading || loadingMore || !hasMore) return;
    void fetchPage(currentPage + 1, "append");
  }, [currentPage, fetchPage, hasMore, loading, loadingMore]);

  useEffect(() => { loadFirstPage(); }, [loadFirstPage]);

  useEffect(() => {
    const target = sentinelRef.current;
    if (!target || !hasMore) return;
    const observer = new IntersectionObserver(
      (entries) => { if (entries[0]?.isIntersecting) loadNextPage(); },
      { root: null, rootMargin: "280px 0px", threshold: 0.1 }
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [hasMore, loadNextPage]);

  return (
    <div className="flex h-full flex-col">
      {/* ── Toolbar ── */}
      <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
        <div className="flex items-center gap-2">
          <SearchField aria-label="状态" value={status} onChange={setStatus} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-28" placeholder="如 SETTLED" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <SearchField aria-label="用户ID" value={userId} onChange={setUserId} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-32" placeholder="用户ID" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <SearchField aria-label="工作空间ID" value={workspaceId} onChange={setWorkspaceId} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-36" placeholder="工作空间ID" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="sm" isIconOnly onPress={loadFirstPage} aria-label="刷新">
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
        </div>
      </div>

      {/* ── Scrollable content ── */}
      <ScrollShadow className="min-h-0 flex-1 overflow-y-auto">
        {loading ? (
          <div className="grid gap-3">
            <Skeleton className="h-12 rounded-xl" />
            <Skeleton className="h-12 rounded-xl" />
            <Skeleton className="h-12 rounded-xl" />
          </div>
        ) : records.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border bg-surface p-10 text-center text-sm text-muted">
            暂无计费数据
          </div>
        ) : (
          <div className="overflow-hidden rounded-xl border border-border bg-surface">
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-surface-secondary">
                  <tr>
                    <th className="px-3 py-2.5 text-xs font-semibold text-muted">会话ID / 工作空间</th>
                    <th className="px-3 py-2.5 text-xs font-semibold text-muted">用户ID</th>
                    <th className="px-3 py-2.5 text-xs font-semibold text-muted">模型</th>
                    <th className="px-3 py-2.5 text-xs font-semibold text-muted">Token（输入 / 输出）</th>
                    <th className="px-3 py-2.5 text-xs font-semibold text-muted">成本（积分）</th>
                    <th className="px-3 py-2.5 text-xs font-semibold text-muted">状态</th>
                    <th className="px-3 py-2.5 text-xs font-semibold text-muted">创建 / 结算时间</th>
                  </tr>
                </thead>
                <tbody>
                  {records.map((item) => {
                    const inputTokens = item.totalInputTokens ?? item.promptTokens ?? 0;
                    const outputTokens = item.totalOutputTokens ?? item.completionTokens ?? 0;
                    const thoughtTokens = item.totalThoughtTokens ?? 0;
                    const cachedTokens = item.totalCachedTokens ?? 0;
                    const llmCost = item.llmCost ?? 0;
                    const toolCost = item.aiToolCost ?? 0;
                    const totalCost = item.totalCost ?? item.creditsConsumed ?? item.settledAmount ?? 0;
                    const statusColor =
                      item.status === "SETTLED" ? "success" :
                      item.status === "FAILED" ? "danger" :
                      item.status === "ACTIVE" ? "accent" : "default";
                    return (
                      <tr key={item.id} className="border-t border-border hover:bg-surface-secondary/40">
                        <td className="px-3 py-2.5">
                          <div className="font-mono text-xs leading-5">
                            <div title={item.conversationId || item.id}>{truncateId(item.conversationId || item.id, 12)}</div>
                            {item.workspaceId ? (
                              <div className="text-muted" title={item.workspaceId}>{truncateId(item.workspaceId, 10)}</div>
                            ) : null}
                          </div>
                        </td>
                        <td className="px-3 py-2.5 font-mono text-xs">
                          <span title={item.userId}>{truncateId(item.userId, 10)}</span>
                        </td>
                        <td className="px-3 py-2.5">
                          <div className="leading-5">
                            <div className="text-sm">{item.modelName || item.llmModelId || item.modelId || "—"}</div>
                            <div className="text-xs text-muted">{item.modelProvider || item.llmProvider || "—"}</div>
                          </div>
                        </td>
                        <td className="px-3 py-2.5 font-mono text-xs">
                          <div>{inputTokens.toLocaleString()} / {outputTokens.toLocaleString()}</div>
                          {(thoughtTokens > 0 || cachedTokens > 0) ? (
                            <div className="text-muted">
                              {thoughtTokens > 0 ? `思考: ${thoughtTokens.toLocaleString()}` : ""}
                              {thoughtTokens > 0 && cachedTokens > 0 ? " " : ""}
                              {cachedTokens > 0 ? `缓存: ${cachedTokens.toLocaleString()}` : ""}
                            </div>
                          ) : null}
                        </td>
                        <td className="px-3 py-2.5 text-xs">
                          <div className="font-semibold">{formatCredits(totalCost)}</div>
                          {(llmCost > 0 || toolCost > 0) ? (
                            <div className="text-muted">
                              LLM {formatCredits(llmCost)} / 工具 {formatCredits(toolCost)}
                            </div>
                          ) : null}
                        </td>
                        <td className="px-3 py-2.5">
                          <Chip size="sm" variant="soft" color={statusColor}>
                            {item.status || "—"}
                          </Chip>
                          {item.settleError ? (
                            <div className="mt-0.5 text-xs text-danger" title={item.settleError}>
                              {item.settleError.length > 24 ? `${item.settleError.slice(0, 24)}…` : item.settleError}
                            </div>
                          ) : null}
                        </td>
                        <td className="px-3 py-2.5 text-xs text-muted">
                          <div>{formatDateTime(item.createdAt)}</div>
                          {item.settledAt ? (
                            <div className="text-accent/80">{formatDateTime(item.settledAt)}</div>
                          ) : null}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <div ref={sentinelRef} className="h-10" />

        {loadingMore ? (
          <div className="mt-2 grid gap-3">
            <Skeleton className="h-12 rounded-xl" />
            <Skeleton className="h-12 rounded-xl" />
          </div>
        ) : null}

        {!loading && !hasMore && records.length > 0 ? (
          <p className="py-4 text-center text-xs text-muted">已加载全部 {totalRecords} 条数据</p>
        ) : null}
      </ScrollShadow>
    </div>
  );
}
