"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import {
  Button,
  Chip,
  ListBox,
  ScrollShadow,
  SearchField,
  Select,
  Skeleton,
  toast,
} from "@heroui/react";
import { Plus, RefreshCw } from "lucide-react";
import type { Key } from "react";
import { aiAdminService, getErrorFromException } from "@/lib/api";
import type { GroovyTemplateDTO, GroovyTemplateType } from "@/lib/api/dto";
import { ViewToggle, type ViewMode } from "@/components/admin/view-toggle";

const PAGE_SIZE = 12;

const TYPE_OPTIONS: Array<{ value: "" | GroovyTemplateType; label: string }> = [
  { value: "", label: "全部" },
  { value: "REQUEST_BUILDER", label: "REQUEST_BUILDER" },
  { value: "RESPONSE_MAPPER", label: "RESPONSE_MAPPER" },
  { value: "CUSTOM_LOGIC", label: "CUSTOM_LOGIC" },
];

export default function GroovyTemplatesPage() {
  const locale = useLocale();
  const router = useRouter();
  const [viewMode, setViewMode] = useState<ViewMode>("card");
  const [name, setName] = useState("");
  const [templateType, setTemplateType] = useState<"" | GroovyTemplateType>("");

  const [records, setRecords] = useState<GroovyTemplateDTO[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalRecords, setTotalRecords] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const requestIdRef = useRef(0);
  const loadedCount = records.length;

  const fetchPage = useCallback(
    async (pageNum: number, mode: "reset" | "append") => {
      const requestId = ++requestIdRef.current;
      if (mode === "append") setLoadingMore(true);
      else setLoading(true);

      try {
        const page = await aiAdminService.getGroovyTemplatePage({
          pageNum,
          pageSize: PAGE_SIZE,
          name: name.trim() || undefined,
          templateType: templateType || undefined,
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
        if (requestId === requestIdRef.current) toast.danger(getErrorFromException(error, locale));
      } finally {
        if (requestId === requestIdRef.current) { setLoading(false); setLoadingMore(false); }
      }
    },
    [locale, name, templateType]
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

  const handleToggle = async (e: React.MouseEvent, item: GroovyTemplateDTO) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      setTogglingId(item.id);
      await aiAdminService.toggleGroovyTemplate(item.id, !item.enabled);
      setRecords((prev) => prev.map((r) => r.id === item.id ? { ...r, enabled: !item.enabled } : r));
      toast.success("状态已更新");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setTogglingId(null);
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

  const handleTypeChange = (key: Key | Key[] | null) => {
    if (key === null || Array.isArray(key)) return;
    const val = String(key);
    setTemplateType(val === "__all__" ? "" : (val as GroovyTemplateType));
  };

  const selectedTypeLabel = TYPE_OPTIONS.find((o) => o.value === templateType)?.label ?? "全部";

  return (
    <div className="flex h-full flex-col">
      {/* ── Toolbar ── */}
      <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
        <div className="flex items-center gap-2">
          <SearchField
            aria-label="模板名称"
            value={name}
            onChange={setName}
            variant="secondary"
          >
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input
                className="w-44"
                placeholder="按模板名称搜索"
                onKeyDown={(e) => e.key === "Enter" && loadFirstPage()}
              />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <Select value={templateType || "__all__"} onChange={handleTypeChange} className="w-44" aria-label="模板类型">
            <Select.Trigger>
              <Select.Value>{() => <span>{selectedTypeLabel}</span>}</Select.Value>
              <Select.Indicator />
            </Select.Trigger>
            <Select.Popover>
              <ListBox>
                {TYPE_OPTIONS.map((opt) => (
                  <ListBox.Item key={opt.value || "__all__"} id={opt.value || "__all__"} textValue={opt.label}>
                    {opt.label}
                    <ListBox.ItemIndicator />
                  </ListBox.Item>
                ))}
              </ListBox>
            </Select.Popover>
          </Select>
        </div>
        <div className="flex items-center gap-2">
          <ViewToggle value={viewMode} onChange={setViewMode} />
          <Button variant="ghost" size="sm" isIconOnly onPress={loadFirstPage} aria-label="刷新">
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
          <Button size="sm" onPress={() => router.push(`/${locale}/admin/models/groovy/new`)}>
            <Plus className="size-4" />
            新建模板
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
            暂无模板数据
          </div>
        ) : viewMode === "card" ? (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {records.map((item) => (
              <Link
                key={item.id}
                href={`/${locale}/admin/models/groovy/${item.id}`}
                className="group rounded-xl border border-border bg-surface p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-accent/70 hover:shadow-md"
              >
                <div className="mb-2 flex items-start justify-between gap-2">
                  <h3 className="line-clamp-1 text-base font-semibold text-foreground">{item.name}</h3>
                  <div className="flex shrink-0 items-center gap-1">
                    <Chip
                      size="sm"
                      variant="soft"
                      color={item.enabled !== false ? "success" : "default"}
                      className="cursor-pointer"
                      onClick={(e) => handleToggle(e as unknown as React.MouseEvent, item)}
                      aria-disabled={togglingId === item.id}
                    >
                      {togglingId === item.id ? "…" : item.enabled !== false ? "启用" : "禁用"}
                    </Chip>
                    <Chip size="sm" variant="soft" color={item.isSystem ? "warning" : "accent"}>
                      {item.isSystem ? "系统" : "自定义"}
                    </Chip>
                  </div>
                </div>
                <p className="text-xs font-mono text-muted">{item.templateType}</p>
                {item.generationType ? (
                  <p className="mt-0.5 text-xs text-muted">{item.generationType}</p>
                ) : null}
                <p className="mt-2 line-clamp-3 text-xs text-muted">{item.description || "无描述"}</p>
              </Link>
            ))}
          </div>
        ) : (
          <div className="space-y-2">
            {records.map((item) => (
              <Link
                key={item.id}
                href={`/${locale}/admin/models/groovy/${item.id}`}
                className="block rounded-xl border border-border bg-surface px-4 py-3 transition hover:border-accent/70 hover:bg-surface-secondary/30"
              >
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="line-clamp-1 text-sm font-semibold text-foreground">{item.name}</p>
                      <Chip
                        size="sm"
                        variant="soft"
                        color={item.enabled !== false ? "success" : "default"}
                        className="cursor-pointer shrink-0"
                        onClick={(e) => handleToggle(e as unknown as React.MouseEvent, item)}
                        aria-disabled={togglingId === item.id}
                      >
                        {togglingId === item.id ? "…" : item.enabled !== false ? "启用" : "禁用"}
                      </Chip>
                      <Chip size="sm" variant="soft" color={item.isSystem ? "warning" : "accent"}>
                        {item.isSystem ? "系统" : "自定义"}
                      </Chip>
                    </div>
                    <p className="mt-0.5 font-mono text-xs text-muted">{item.templateType}</p>
                    {item.description ? (
                      <p className="mt-0.5 line-clamp-1 text-xs text-muted">{item.description}</p>
                    ) : null}
                  </div>
                  <div className="text-xs text-muted lg:min-w-[200px]">
                    {item.generationType ? <span>generationType: {item.generationType}</span> : null}
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
