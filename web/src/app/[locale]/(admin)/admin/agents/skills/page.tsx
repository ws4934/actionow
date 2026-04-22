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
  Switch,
  toast,
} from "@heroui/react";
import { Plus, RefreshCw, RotateCcw, Upload } from "lucide-react";
import { adminService, getErrorFromException } from "@/lib/api";
import type { AgentSkillDTO } from "@/lib/api/dto";
import { ViewToggle, type ViewMode } from "@/components/admin/view-toggle";

const PAGE_SIZE = 20;

export default function AgentSkillsPage() {
  const locale = useLocale();
  const router = useRouter();
  const [viewMode, setViewMode] = useState<ViewMode>("card");
  const [keyword, setKeyword] = useState("");

  const [records, setRecords] = useState<AgentSkillDTO[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalRecords, setTotalRecords] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [togglingName, setTogglingName] = useState<string | null>(null);
  const [reloading, setReloading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const requestIdRef = useRef(0);
  const loadedCount = records.length;

  const fetchPage = useCallback(
    async (pageNum: number, mode: "reset" | "append") => {
      const requestId = ++requestIdRef.current;
      if (mode === "append") setLoadingMore(true);
      else setLoading(true);

      try {
        const result = await adminService.getSkillList({
          page: pageNum,
          size: PAGE_SIZE,
          keyword: keyword.trim() || undefined,
        });

        if (requestId !== requestIdRef.current) return;

        setTotalRecords(result.total);
        setCurrentPage(pageNum);
        setHasMore(pageNum * PAGE_SIZE < result.total);

        if (mode === "reset") {
          setRecords(result.items);
          return;
        }

        setRecords((prev) => {
          const existed = new Set(prev.map((item) => item.name));
          const merged = [...prev];
          for (const item of result.items) {
            if (!existed.has(item.name)) { merged.push(item); existed.add(item.name); }
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
    [locale, keyword]
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

  const handleToggle = async (item: AgentSkillDTO) => {
    try {
      setTogglingName(item.name);
      const res = await adminService.toggleSkill(item.name);
      setRecords((prev) => prev.map((s) => (s.name === item.name ? { ...s, enabled: res.enabled } : s)));
      toast.success("状态已更新");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setTogglingName(null);
    }
  };

  const handleReload = async () => {
    try {
      setReloading(true);
      await adminService.reloadSkills();
      toast.success("Skill 已重载");
      loadFirstPage();
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setReloading(false);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = "";
    setUploading(true);
    try {
      const result = await adminService.importSkills(file);
      if (result.failed === 0) {
        toast.success(`导入成功：${result.success}/${result.total} 个 Skill`);
      } else {
        toast.warning(`部分导入成功：${result.success}/${result.total} 个 Skill${result.errors[0] ? `\n${result.errors[0]}` : ""}`);
      }
      loadFirstPage();
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="flex h-full flex-col">
      {/* ── Toolbar ── */}
      <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
        <div className="flex items-center gap-2">
          <SearchField aria-label="关键字" value={keyword} onChange={setKeyword} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-44" placeholder="名称/描述" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
        </div>
        <div className="flex items-center gap-2">
          <ViewToggle value={viewMode} onChange={setViewMode} />
          <Button variant="ghost" size="sm" isIconOnly onPress={loadFirstPage} aria-label="刷新">
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
          <Button variant="tertiary" size="sm" isPending={reloading} onPress={handleReload} className="gap-1">
            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <RotateCcw className="size-4" />}重载</>)}
          </Button>
          <input ref={fileInputRef} type="file" accept=".zip" className="hidden" onChange={handleFileUpload} />
          <Button variant="tertiary" size="sm" isPending={uploading} onPress={() => fileInputRef.current?.click()} className="gap-1">
            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Upload className="size-4" />}导入ZIP</>)}
          </Button>
          <Button size="sm" onPress={() => router.push(`/${locale}/admin/agents/skills/new`)}>
            <Plus className="size-4" />
            新建Skill
          </Button>
        </div>
      </div>

      {/* ── Scrollable content ── */}
      <ScrollShadow className="min-h-0 flex-1 overflow-y-auto pt-0.5">
        {loading ? (
          <div className="grid gap-3 md:grid-cols-3">
            <Skeleton className="h-28 rounded-xl" />
            <Skeleton className="h-28 rounded-xl" />
            <Skeleton className="h-28 rounded-xl" />
          </div>
        ) : records.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border bg-surface p-10 text-center text-sm text-muted">
            暂无 Skill
          </div>
        ) : viewMode === "card" ? (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {records.map((item) => (
              <Link
                key={item.name}
                href={`/${locale}/admin/agents/skills/${item.name}`}
                className="group rounded-xl border border-border bg-surface p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-accent/70 hover:shadow-md"
              >
                <div className="mb-2 flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <h3 className="line-clamp-1 text-base font-semibold text-foreground">{item.displayName || item.name}</h3>
                    <p className="mt-0.5 font-mono text-xs text-muted">{item.name}</p>
                  </div>
                  <div onClick={(e) => e.preventDefault()}>
                    <Switch
                      isSelected={item.enabled}
                      isDisabled={togglingName === item.name}
                      onChange={() => void handleToggle(item)}
                      size="sm"
                    />
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-1.5">
                  <Chip size="sm" variant="soft" color={item.enabled ? "success" : "default"}>
                    {item.enabled ? "启用" : "禁用"}
                  </Chip>
                  <Chip size="sm" variant="soft" color="default">
                    {item.scope || "SYSTEM"}
                  </Chip>
                  <span className="text-xs text-muted">v{item.version}</span>
                </div>
                {item.description ? (
                  <p className="mt-2 line-clamp-2 text-xs text-muted">{item.description}</p>
                ) : null}
              </Link>
            ))}
          </div>
        ) : (
          <div className="space-y-2">
            {records.map((item) => (
              <Link
                key={item.name}
                href={`/${locale}/admin/agents/skills/${item.name}`}
                className="block rounded-xl border border-border bg-surface px-4 py-3 transition hover:border-accent/70 hover:bg-surface-secondary/30"
              >
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="line-clamp-1 text-sm font-semibold text-foreground">{item.displayName || item.name}</p>
                      <Chip size="sm" variant="soft" color={item.enabled ? "success" : "default"}>
                        {item.enabled ? "启用" : "禁用"}
                      </Chip>
                      <Chip size="sm" variant="soft" color="default">
                        {item.scope || "SYSTEM"}
                      </Chip>
                    </div>
                    <p className="mt-0.5 font-mono text-xs text-muted">{item.name}</p>
                    {item.description ? (
                      <p className="mt-0.5 line-clamp-1 text-xs text-muted">{item.description}</p>
                    ) : null}
                  </div>
                  <div className="flex items-center gap-3 lg:min-w-[160px]">
                    <span className="text-xs text-muted">v{item.version}</span>
                    <div onClick={(e) => e.preventDefault()}>
                      <Switch
                        isSelected={item.enabled}
                        isDisabled={togglingName === item.name}
                        onChange={() => void handleToggle(item)}
                        size="sm"
                      />
                    </div>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}

        <div ref={sentinelRef} className="h-10" />

        {loadingMore ? (
          <div className="mt-2 grid gap-3 md:grid-cols-3">
            <Skeleton className="h-24 rounded-xl" />
            <Skeleton className="h-24 rounded-xl" />
            <Skeleton className="h-24 rounded-xl" />
          </div>
        ) : null}

        {!loading && !hasMore && records.length > 0 ? (
          <p className="py-4 text-center text-xs text-muted">已加载全部 {totalRecords} 条数据</p>
        ) : null}
      </ScrollShadow>
    </div>
  );
}
