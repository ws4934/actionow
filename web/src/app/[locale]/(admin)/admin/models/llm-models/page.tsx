"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import {
  Button,
  Chip,
  ScrollShadow,
  SearchField,
  Skeleton,
  Spinner,
  toast,
} from "@heroui/react";
import { Plus, RefreshCw, RotateCcw } from "lucide-react";
import { aiAdminService, getErrorFromException } from "@/lib/api";
import type { LlmProviderDTO } from "@/lib/api/dto";
import { ViewToggle, type ViewMode } from "@/components/admin/view-toggle";

const PAGE_SIZE = 12;

export default function LlmModelsPage() {
  const locale = useLocale();
  const router = useRouter();
  const [viewMode, setViewMode] = useState<ViewMode>("card");
  const [modelName, setModelName] = useState("");
  const [provider, setProvider] = useState("");

  const [records, setRecords] = useState<LlmProviderDTO[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalRecords, setTotalRecords] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [refreshingCache, setRefreshingCache] = useState(false);

  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const requestIdRef = useRef(0);
  const loadedCount = records.length;

  const fetchPage = useCallback(
    async (pageNum: number, mode: "reset" | "append") => {
      const requestId = ++requestIdRef.current;
      if (mode === "append") setLoadingMore(true);
      else setLoading(true);

      try {
        const page = await aiAdminService.getLlmProviderPage({
          current: pageNum,
          size: PAGE_SIZE,
          modelName: modelName.trim() || undefined,
          provider: provider.trim() || undefined,
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
    [locale, modelName, provider]
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

  const handleToggle = async (e: React.MouseEvent, item: LlmProviderDTO) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      setTogglingId(item.id);
      await aiAdminService.toggleLlmProvider(item.id, !item.enabled);
      setRecords((prev) => prev.map((r) => r.id === item.id ? { ...r, enabled: !item.enabled } : r));
      toast.success("状态已更新");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setTogglingId(null);
    }
  };

  const handleRefreshCache = async () => {
    try {
      setRefreshingCache(true);
      await aiAdminService.refreshLlmProviderCache();
      toast.success("缓存已刷新");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setRefreshingCache(false);
    }
  };

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
          <SearchField
            aria-label="模型名称"
            value={modelName}
            onChange={setModelName}
            variant="secondary"
          >
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input
                className="w-40"
                placeholder="按模型名称搜索"
                onKeyDown={(e) => e.key === "Enter" && loadFirstPage()}
              />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <SearchField
            aria-label="厂商"
            value={provider}
            onChange={setProvider}
            variant="secondary"
          >
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input
                className="w-28"
                placeholder="如 OPENAI"
                onKeyDown={(e) => e.key === "Enter" && loadFirstPage()}
              />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
        </div>
        <div className="flex items-center gap-2">
          <ViewToggle value={viewMode} onChange={setViewMode} />
          <Button variant="tertiary" size="sm" isPending={refreshingCache} onPress={handleRefreshCache} className="gap-1">
            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <RotateCcw className="size-4" />}刷新缓存</>)}
          </Button>
          <Button variant="ghost" size="sm" isIconOnly onPress={loadFirstPage} aria-label="刷新">
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
          <Button size="sm" onPress={() => router.push(`/${locale}/admin/models/llm-models/new`)}>
            <Plus className="size-4" />
            新建模型
          </Button>
        </div>
      </div>

      {/* ── Scrollable content ── */}
      <ScrollShadow className="min-h-0 flex-1 overflow-y-auto pt-0.5">
        {loading ? (
          <div className="grid gap-3 md:grid-cols-3">
            <Skeleton className="h-36 rounded-xl" />
            <Skeleton className="h-36 rounded-xl" />
            <Skeleton className="h-36 rounded-xl" />
          </div>
        ) : records.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border bg-surface p-10 text-center text-sm text-muted">
            暂无模型数据
          </div>
        ) : viewMode === "card" ? (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {records.map((item) => (
              <Link
                key={item.id}
                href={`/${locale}/admin/models/llm-models/${item.id}`}
                className="group rounded-xl border border-border bg-surface p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-accent/70 hover:shadow-md"
              >
                <div className="mb-2 flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <h3 className="line-clamp-1 text-base font-semibold text-foreground">{item.modelName}</h3>
                    <p className="mt-0.5 font-mono text-xs text-muted">{item.modelId}</p>
                  </div>
                  <Chip
                    size="sm"
                    variant="soft"
                    color={item.enabled ? "success" : "default"}
                    className="cursor-pointer shrink-0"
                    onClick={(e) => handleToggle(e as unknown as React.MouseEvent, item)}
                    aria-disabled={togglingId === item.id}
                  >
                    {togglingId === item.id ? "…" : item.enabled ? "启用" : "禁用"}
                  </Chip>
                </div>
                <p className="text-sm text-muted">{item.provider}</p>
                {item.description ? (
                  <p className="mt-1 line-clamp-2 text-xs text-muted">{item.description}</p>
                ) : null}
                <div className="mt-3 flex items-center justify-between text-xs text-muted">
                  <span>temperature: {item.temperature ?? "-"}</span>
                  <span>maxOut: {item.maxOutputTokens ?? "-"}</span>
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <div className="space-y-2">
            {records.map((item) => (
              <Link
                key={item.id}
                href={`/${locale}/admin/models/llm-models/${item.id}`}
                className="block rounded-xl border border-border bg-surface px-4 py-3 transition hover:border-accent/70 hover:bg-surface-secondary/30"
              >
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="line-clamp-1 text-sm font-semibold text-foreground">{item.modelName}</p>
                      <Chip
                        size="sm"
                        variant="soft"
                        color={item.enabled ? "success" : "default"}
                        className="cursor-pointer shrink-0"
                        onClick={(e) => handleToggle(e as unknown as React.MouseEvent, item)}
                        aria-disabled={togglingId === item.id}
                      >
                        {togglingId === item.id ? "…" : item.enabled ? "启用" : "禁用"}
                      </Chip>
                    </div>
                    <p className="mt-0.5 font-mono text-xs text-muted">{item.modelId}</p>
                    {item.description ? (
                      <p className="mt-0.5 line-clamp-1 text-xs text-muted">{item.description}</p>
                    ) : null}
                  </div>
                  <div className="grid grid-cols-2 gap-x-6 gap-y-1 text-xs text-muted lg:min-w-[300px]">
                    <span>厂商: {item.provider}</span>
                    <span>优先级: {item.priority ?? "-"}</span>
                    <span>temperature: {item.temperature ?? "-"}</span>
                    <span>maxOutputTokens: {item.maxOutputTokens ?? "-"}</span>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}

        <div ref={sentinelRef} className="h-10" />

        {loadingMore ? (
          <div className="mt-2 grid gap-3 md:grid-cols-3">
            <Skeleton className="h-32 rounded-xl" />
            <Skeleton className="h-32 rounded-xl" />
            <Skeleton className="h-32 rounded-xl" />
          </div>
        ) : null}

        {!loading && !hasMore && records.length > 0 ? (
          <p className="py-4 text-center text-xs text-muted">已加载全部 {totalRecords} 条数据</p>
        ) : null}
      </ScrollShadow>
    </div>
  );
}
