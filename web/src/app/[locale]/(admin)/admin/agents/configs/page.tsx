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
  Switch,
  toast,
} from "@heroui/react";
import { Plus, RefreshCw } from "lucide-react";
import type { Key } from "react";
import { adminService, getErrorFromException } from "@/lib/api";
import type { AgentConfigDTO } from "@/lib/api/dto";
import { ViewToggle, type ViewMode } from "@/components/admin/view-toggle";

const PAGE_SIZE = 12;

const SCOPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "", label: "全部作用域" },
  { value: "SYSTEM", label: "SYSTEM" },
  { value: "WORKSPACE", label: "WORKSPACE" },
  { value: "USER", label: "USER" },
];

const ENABLED_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "", label: "全部状态" },
  { value: "true", label: "启用" },
  { value: "false", label: "禁用" },
];

export default function AgentConfigsPage() {
  const locale = useLocale();
  const router = useRouter();
  const [viewMode, setViewMode] = useState<ViewMode>("card");
  const [keyword, setKeyword] = useState("");
  const [agentType, setAgentType] = useState("");
  const [scope, setScope] = useState("");
  const [enabled, setEnabled] = useState<"" | "true" | "false">("");

  const [records, setRecords] = useState<AgentConfigDTO[]>([]);
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
        const page = await adminService.getAgentConfigPage({
          current: pageNum,
          size: PAGE_SIZE,
          keyword: keyword.trim() || undefined,
          agentType: agentType.trim() || undefined,
          scope: scope || undefined,
          enabled: enabled ? enabled === "true" : undefined,
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
    [locale, keyword, agentType, scope, enabled]
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

  const handleScopeChange = (key: Key | Key[] | null) => {
    if (key === null || Array.isArray(key)) return;
    const val = String(key);
    setScope(val === "__all__" ? "" : val);
  };

  const handleEnabledChange = (key: Key | Key[] | null) => {
    if (key === null || Array.isArray(key)) return;
    const val = String(key);
    setEnabled(val === "__all__" ? "" : (val as "true" | "false"));
  };

  const handleToggle = async (item: AgentConfigDTO, nextEnabled: boolean) => {
    if (!item.id) return;
    try {
      setTogglingId(item.id);
      await adminService.toggleAgentConfig(item.id, nextEnabled);
      setRecords((prev) => prev.map((it) => (it.id === item.id ? { ...it, enabled: nextEnabled } : it)));
      toast.success("状态已更新");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setTogglingId(null);
    }
  };

  const selectedScopeLabel = SCOPE_OPTIONS.find((o) => o.value === scope)?.label ?? "全部作用域";
  const selectedEnabledLabel = ENABLED_OPTIONS.find((o) => o.value === enabled)?.label ?? "全部状态";

  return (
    <div className="flex h-full flex-col">
      {/* ── Toolbar ── */}
      <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
        <div className="flex flex-wrap items-center gap-2">
          <SearchField aria-label="关键字" value={keyword} onChange={setKeyword} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-36" placeholder="名称/描述" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <SearchField aria-label="Agent类型" value={agentType} onChange={setAgentType} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-40" placeholder="如 STORYBOARD_EXPERT" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <Select value={scope || "__all__"} onChange={handleScopeChange} className="w-36" aria-label="作用域">
            <Select.Trigger>
              <Select.Value>{() => <span>{selectedScopeLabel}</span>}</Select.Value>
              <Select.Indicator />
            </Select.Trigger>
            <Select.Popover>
              <ListBox>
                {SCOPE_OPTIONS.map((opt) => (
                  <ListBox.Item key={opt.value || "__all__"} id={opt.value || "__all__"} textValue={opt.label}>
                    {opt.label}
                    <ListBox.ItemIndicator />
                  </ListBox.Item>
                ))}
              </ListBox>
            </Select.Popover>
          </Select>
          <Select value={enabled || "__all__"} onChange={handleEnabledChange} className="w-28" aria-label="启用状态">
            <Select.Trigger>
              <Select.Value>{() => <span>{selectedEnabledLabel}</span>}</Select.Value>
              <Select.Indicator />
            </Select.Trigger>
            <Select.Popover>
              <ListBox>
                {ENABLED_OPTIONS.map((opt) => (
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
          <Button size="sm" onPress={() => router.push(`/${locale}/admin/agents/configs/new`)}>
            <Plus className="size-4" />
            新建配置
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
            暂无 Agent 配置
          </div>
        ) : viewMode === "card" ? (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {records.map((item) => (
              <Link
                key={item.id}
                href={`/${locale}/admin/agents/configs/${item.id}`}
                className="group rounded-xl border border-border bg-surface p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-accent/70 hover:shadow-md"
              >
                <div className="mb-2 flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <h3 className="line-clamp-1 text-base font-semibold text-foreground">{item.agentName || item.agentType}</h3>
                    <p className="mt-0.5 font-mono text-xs text-muted">{item.agentType}</p>
                  </div>
                  <div onClick={(e) => e.preventDefault()}>
                    <Switch
                      isSelected={item.enabled}
                      isDisabled={togglingId === item.id}
                      onChange={() => void handleToggle(item, !item.enabled)}
                      size="sm"
                    />
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-1.5">
                  <Chip size="sm" variant="soft" color={item.enabled ? "success" : "default"}>
                    {item.enabled ? "启用" : "禁用"}
                  </Chip>
                  {item.scope ? (
                    <Chip size="sm" variant="soft" color="default">
                      {item.scope}
                    </Chip>
                  ) : null}
                  {item.executionMode ? (
                    <Chip size="sm" variant="soft" color="default">
                      {item.executionMode}
                    </Chip>
                  ) : null}
                </div>
                {item.description ? (
                  <p className="mt-2 line-clamp-2 text-xs text-muted">{item.description}</p>
                ) : null}
                {item.skillLoadMode ? (
                  <p className="mt-1 text-xs text-muted">Skill: {item.skillLoadMode}</p>
                ) : null}
                {item.llmProviderId ? (
                  <p className="mt-1.5 font-mono text-xs text-muted">LLM: {item.llmProviderId}</p>
                ) : null}
                {item.tags && item.tags.length > 0 ? (
                  <div className="mt-2 flex flex-wrap gap-1">
                    {item.tags.map((tag) => (
                      <span key={tag} className="rounded bg-surface-secondary px-1.5 py-0.5 font-mono text-[10px] text-muted">
                        {tag}
                      </span>
                    ))}
                  </div>
                ) : null}
              </Link>
            ))}
          </div>
        ) : (
          <div className="space-y-2">
            {records.map((item) => (
              <Link
                key={item.id}
                href={`/${locale}/admin/agents/configs/${item.id}`}
                className="block rounded-xl border border-border bg-surface px-4 py-3 transition hover:border-accent/70 hover:bg-surface-secondary/30"
              >
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="line-clamp-1 text-sm font-semibold text-foreground">{item.agentName || item.agentType}</p>
                      <Chip size="sm" variant="soft" color={item.enabled ? "success" : "default"}>
                        {item.enabled ? "启用" : "禁用"}
                      </Chip>
                      {item.scope ? (
                        <Chip size="sm" variant="soft" color="default">
                      {item.scope}
                    </Chip>
                  ) : null}
                      {item.executionMode ? (
                        <Chip size="sm" variant="soft" color="default">
                          {item.executionMode}
                        </Chip>
                      ) : null}
                    </div>
                    <p className="mt-0.5 font-mono text-xs text-muted">{item.agentType}</p>
                    {item.description ? (
                      <p className="mt-0.5 line-clamp-1 text-xs text-muted">{item.description}</p>
                    ) : null}
                    {item.skillLoadMode ? (
                      <p className="mt-0.5 text-xs text-muted">Skill: {item.skillLoadMode}</p>
                    ) : null}
                    {item.tags && item.tags.length > 0 ? (
                      <div className="mt-1 flex flex-wrap gap-1">
                        {item.tags.map((tag) => (
                          <span key={tag} className="rounded bg-surface-secondary px-1.5 py-0.5 font-mono text-[10px] text-muted">
                            {tag}
                          </span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                  <div className="flex items-center gap-3 lg:min-w-[200px]">
                    {item.llmProviderId ? (
                      <span className="truncate font-mono text-xs text-muted">{item.llmProviderId}</span>
                    ) : null}
                    <div onClick={(e) => e.preventDefault()}>
                      <Switch
                        isSelected={item.enabled}
                        isDisabled={togglingId === item.id}
                        onChange={() => void handleToggle(item, !item.enabled)}
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
