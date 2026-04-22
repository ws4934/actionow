"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import {
  Button,
  Chip,
  ScrollShadow,
  SearchField,
  Skeleton,
  Spinner,
  Tabs,
  toast,
} from "@heroui/react";
import { Copy, Plus, RefreshCw, Wifi } from "lucide-react";
import { aiAdminService, getErrorFromException } from "@/lib/api";
import type { AiProviderType, ModelProviderDTO } from "@/lib/api/dto";
import { ViewToggle, type ViewMode } from "@/components/admin/view-toggle";

const PAGE_SIZE = 12;

const TYPE_TABS: Array<{ id: string; typeKey: AiProviderType | "" }> = [
  { id: "__all__", typeKey: "" },
  { id: "TEXT",   typeKey: "TEXT" },
  { id: "IMAGE",  typeKey: "IMAGE" },
  { id: "VIDEO",  typeKey: "VIDEO" },
  { id: "AUDIO",  typeKey: "AUDIO" },
];

// Map provider type → HeroUI Chip color
const TYPE_CHIP_COLOR: Record<string, "accent" | "success" | "danger" | "warning" | "default"> = {
  IMAGE: "warning",
  VIDEO: "warning",
  AUDIO: "success",
  TEXT:  "accent",
};

function getSupportedModes(item: ModelProviderDTO): string[] {
  if (item.supportedModes?.length) return item.supportedModes;
  const modes: string[] = [];
  if (item.supportsBlocking)  modes.push("BLOCKING");
  if (item.supportsStreaming) modes.push("STREAMING");
  if (item.supportsCallback)  modes.push("CALLBACK");
  if (item.supportsPolling)   modes.push("POLLING");
  return modes;
}

export default function AiModelsPage() {
  const locale = useLocale();
  const t = useTranslations("admin.model.providerType");
  const router = useRouter();
  const [viewMode, setViewMode] = useState<ViewMode>("card");
  const [name, setName] = useState("");
  const [providerType, setProviderType] = useState<"" | AiProviderType>("");

  const [records, setRecords] = useState<ModelProviderDTO[]>([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalRecords, setTotalRecords] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [duplicatingId, setDuplicatingId] = useState<string | null>(null);

  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const requestIdRef = useRef(0);

  const fetchPage = useCallback(
    async (pageNum: number, mode: "reset" | "append") => {
      const requestId = ++requestIdRef.current;
      if (mode === "append") setLoadingMore(true);
      else setLoading(true);
      try {
        const page = await aiAdminService.getModelProviderPage({
          pageNum,
          pageSize: PAGE_SIZE,
          name: name.trim() || undefined,
          providerType: providerType || undefined,
        });
        if (requestId !== requestIdRef.current) return;
        setTotalRecords(page.total);
        setCurrentPage(page.current);
        setHasMore(page.current < page.pages);
        if (mode === "reset") { setRecords(page.records); return; }
        setRecords((prev) => {
          const existed = new Set(prev.map((r) => r.id));
          const merged = [...prev];
          for (const item of page.records) {
            if (!existed.has(item.id)) { merged.push(item); existed.add(item.id); }
          }
          return merged;
        });
      } catch (error) {
        if (requestId === requestIdRef.current) { toast.danger(getErrorFromException(error, locale)); setHasMore(false); }
      } finally {
        if (requestId === requestIdRef.current) { setLoading(false); setLoadingMore(false); }
      }
    },
    [locale, name, providerType]
  );

  const loadFirstPage = useCallback(() => {
    setRecords([]); setCurrentPage(0); setTotalRecords(0); setHasMore(true);
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

  const handleToggle = async (item: ModelProviderDTO) => {
    try {
      setTogglingId(item.id);
      if (item.enabled) await aiAdminService.disableModelProvider(item.id);
      else await aiAdminService.enableModelProvider(item.id);
      setRecords((prev) => prev.map((r) => r.id === item.id ? { ...r, enabled: !item.enabled } : r));
      toast.success("状态已更新");
    } catch (error) { toast.danger(getErrorFromException(error, locale)); }
    finally { setTogglingId(null); }
  };

  const handleTest = async (item: ModelProviderDTO) => {
    try {
      setTestingId(item.id);
      const result = await aiAdminService.testModelProviderConnection(item.id);
      if (result.connected) toast.success(`连接成功 (${result.latencyMs ?? "-"}ms)`);
      else toast.danger(result.message || "连接失败");
    } catch (error) { toast.danger(getErrorFromException(error, locale)); }
    finally { setTestingId(null); }
  };

  const handleDuplicate = async (item: ModelProviderDTO) => {
    try {
      setDuplicatingId(item.id);
      const d = await aiAdminService.getModelProviderById(item.id);
      const created = await aiAdminService.createModelProvider({
        name: `${d.name} (Copy)`, providerType: d.providerType,
        pluginId: `${d.pluginId}_copy_${Date.now()}`, pluginType: d.pluginType,
        description: d.description ?? undefined, baseUrl: d.baseUrl, endpoint: d.endpoint,
        httpMethod: d.httpMethod, authType: d.authType, authConfig: d.authConfig ?? undefined,
        apiKeyRef: d.apiKeyRef ?? undefined, baseUrlRef: d.baseUrlRef ?? undefined,
        llmProviderId: d.llmProviderId ?? undefined, systemPrompt: d.systemPrompt ?? undefined,
        responseSchema: d.responseSchema ?? undefined,
        requestBuilderScript: d.requestBuilderScript ?? undefined,
        responseMapperScript: d.responseMapperScript ?? undefined,
        customLogicScript: d.customLogicScript ?? undefined,
        iconUrl: d.iconUrl ?? undefined, creditCost: d.creditCost,
        pricingRules: d.pricingRules ?? undefined, pricingScript: d.pricingScript ?? undefined,
        priority: d.priority, timeout: d.timeout, maxRetries: d.maxRetries, rateLimit: d.rateLimit,
        supportsStreaming: d.supportsStreaming, supportsBlocking: d.supportsBlocking,
        supportsCallback: d.supportsCallback, supportsPolling: d.supportsPolling,
        callbackConfig: d.callbackConfig ?? undefined, pollingConfig: d.pollingConfig ?? undefined,
        inputSchema: d.inputSchema ?? undefined, inputGroups: d.inputGroups ?? undefined,
        outputSchema: d.outputSchema ?? undefined, exclusiveGroups: d.exclusiveGroups ?? undefined,
        enabled: false,
      });
      toast.success("复制成功");
      router.push(`/${locale}/admin/models/ai-models/${created.id}`);
    } catch (error) { toast.danger(getErrorFromException(error, locale)); }
    finally { setDuplicatingId(null); }
  };

  const renderContent = () => {
    if (loading) {
      return (
        <div className="grid gap-3 md:grid-cols-3">
          {[...Array(6)].map((_, i) => <Skeleton key={i} className="h-44 rounded-xl" />)}
        </div>
      );
    }
    if (records.length === 0) {
      return (
        <div className="rounded-xl border border-dashed border-border bg-surface p-10 text-center text-sm text-muted">
          暂无模型数据
        </div>
      );
    }

    if (viewMode === "card") {
      return (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {records.map((item) => {
            const modes = getSupportedModes(item);
            const typeColor = TYPE_CHIP_COLOR[item.providerType] ?? "default";
            return (
              <div
                key={item.id}
                role="button"
                tabIndex={0}
                onClick={() => router.push(`/${locale}/admin/models/ai-models/${item.id}`)}
                onKeyDown={(e) => { if (e.key === "Enter") router.push(`/${locale}/admin/models/ai-models/${item.id}`); }}
                className="group flex cursor-pointer flex-col overflow-hidden rounded-xl border border-border bg-surface shadow-sm transition-all hover:border-accent/60 hover:shadow-md"
              >
                {/* Header */}
                <div className="flex items-center gap-3 p-4 pb-3">
                  {item.iconUrl ? (
                    <img src={item.iconUrl} alt="" className="size-9 shrink-0 rounded-lg border border-border object-contain p-1" />
                  ) : (
                    <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface-secondary text-sm font-bold text-foreground">
                      {(item.name || "?").slice(0, 1).toUpperCase()}
                    </div>
                  )}
                  <div className="min-w-0 flex-1">
                    <h3 className="line-clamp-1 text-sm font-semibold text-foreground">{item.name}</h3>
                    <p className="mt-0.5 line-clamp-1 font-mono text-[10px] text-muted">{item.pluginId}</p>
                  </div>
                </div>

                {/* Description */}
                <p className="line-clamp-2 min-h-[2.5rem] px-4 text-xs text-muted">
                  {item.description || "暂无描述"}
                </p>

                {/* Chips */}
                <div className="mt-3 flex flex-wrap items-center gap-1.5 px-4">
                  <Chip
                    size="sm"
                    variant="soft"
                    color={item.enabled ? "success" : "default"}
                    className="cursor-pointer"
                    onClick={(e) => { e.stopPropagation(); void handleToggle(item); }}
                    aria-disabled={togglingId === item.id}
                  >
                    {togglingId === item.id ? "…" : item.enabled ? "启用" : "禁用"}
                  </Chip>
                  <Chip size="sm" variant="soft" color={typeColor}>
                    {t(item.providerType)}
                  </Chip>
                  {item.pluginType && (
                    <Chip size="sm" variant="soft" color="default">{item.pluginType}</Chip>
                  )}
                </div>

                {/* Stats */}
                <div className="mt-3 flex items-center gap-3 px-4 pb-3 text-[11px] text-muted">
                  <span>积分 <b className="text-foreground">{item.creditCost ?? "-"}</b></span>
                  <span className="h-3 w-px bg-border" />
                  <span>优先级 <b className="text-foreground">{item.priority ?? "-"}</b></span>
                  {modes.length > 0 && (
                    <>
                      <span className="h-3 w-px bg-border" />
                      <span className="line-clamp-1">{modes.join(" · ")}</span>
                    </>
                  )}
                </div>

                {/* Footer actions */}
                <div
                  className="flex items-center gap-1 border-t border-border px-3 py-2"
                  onClick={(e) => e.stopPropagation()}
                  onKeyDown={(e) => e.stopPropagation()}
                  role="group"
                >
                  <Button
                    size="sm" variant="ghost"
                    isPending={testingId === item.id}
                    onPress={() => void handleTest(item)}
                    className="h-7 gap-1.5 px-2 text-xs"
                  >
                    {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Wifi className="size-3" />}测试</>)}
                  </Button>
                  <Button
                    size="sm" variant="ghost"
                    isPending={duplicatingId === item.id}
                    onPress={() => handleDuplicate(item)}
                    className="h-7 gap-1.5 px-2 text-xs"
                  >
                    {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Copy className="size-3" />}复制</>)}
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      );
    }

    // List view
    return (
      <div className="space-y-1.5">
        {records.map((item) => {
          const modes = getSupportedModes(item);
          const typeColor = TYPE_CHIP_COLOR[item.providerType] ?? "default";
          return (
            <div
              key={item.id}
              role="button"
              tabIndex={0}
              onClick={() => router.push(`/${locale}/admin/models/ai-models/${item.id}`)}
              onKeyDown={(e) => { if (e.key === "Enter") router.push(`/${locale}/admin/models/ai-models/${item.id}`); }}
              className="group flex cursor-pointer items-center gap-4 rounded-xl border border-border bg-surface px-4 py-3 transition hover:border-accent/60 hover:bg-surface-secondary/30"
            >
              {/* Icon */}
              {item.iconUrl ? (
                <img src={item.iconUrl} alt="" className="size-8 shrink-0 rounded-lg border border-border object-contain p-1" />
              ) : (
                <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface-secondary text-xs font-bold text-foreground">
                  {(item.name || "?").slice(0, 1).toUpperCase()}
                </div>
              )}

              {/* Name + badges + description */}
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-1.5">
                  <p className="text-sm font-semibold text-foreground">{item.name}</p>
                  <Chip size="sm" variant="soft" color={item.enabled ? "success" : "default"}
                    className="cursor-pointer"
                    onClick={(e) => { e.stopPropagation(); void handleToggle(item); }}
                    aria-disabled={togglingId === item.id}
                  >
                    {togglingId === item.id ? "…" : item.enabled ? "启用" : "禁用"}
                  </Chip>
                  <Chip size="sm" variant="soft" color={typeColor}>{t(item.providerType)}</Chip>
                  {item.pluginType && <Chip size="sm" variant="soft" color="default">{item.pluginType}</Chip>}
                </div>
                <p className="mt-0.5 font-mono text-[11px] text-muted">{item.pluginId}</p>
              </div>

              {/* Stats */}
              <div className="hidden shrink-0 items-center gap-5 text-xs text-muted lg:flex">
                <div className="text-center">
                  <p className="font-semibold text-foreground">{item.creditCost ?? "-"}</p>
                  <p className="text-[10px]">积分</p>
                </div>
                <div className="text-center">
                  <p className="font-semibold text-foreground">{item.priority ?? "-"}</p>
                  <p className="text-[10px]">优先级</p>
                </div>
                <div className="max-w-[160px] text-right">
                  <p className="line-clamp-1 text-[11px]">{modes.join(" · ") || "-"}</p>
                  <p className="text-[10px]">支持模式</p>
                </div>
              </div>

              {/* Actions */}
              <div
                className="flex shrink-0 items-center gap-1"
                onClick={(e) => e.stopPropagation()}
                onKeyDown={(e) => e.stopPropagation()}
                role="group"
              >
                <Button size="sm" variant="ghost" isPending={testingId === item.id}
                  onPress={() => void handleTest(item)} className="h-7 gap-1.5 px-2 text-xs">
                  {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Wifi className="size-3" />}测试</>)}
                </Button>
                <Button size="sm" variant="ghost" isPending={duplicatingId === item.id}
                  onPress={() => handleDuplicate(item)} className="h-7 gap-1.5 px-2 text-xs">
                  {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Copy className="size-3" />}复制</>)}
                </Button>
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className="flex h-full flex-col">
      {/* ── Toolbar ── */}
      <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
        {/* Left: type tabs */}
        <Tabs
          selectedKey={providerType || "__all__"}
          onSelectionChange={(key) => {
            const val = String(key);
            setProviderType(val === "__all__" ? "" : (val as AiProviderType));
          }}
        >
          <Tabs.ListContainer>
            <Tabs.List aria-label="模型类型">
              {TYPE_TABS.map((tab) => (
                <Tabs.Tab key={tab.id} id={tab.id} className="px-3 text-xs">
                  {t(tab.typeKey === "" ? "all" : tab.typeKey)}
                  <Tabs.Indicator />
                </Tabs.Tab>
              ))}
            </Tabs.List>
          </Tabs.ListContainer>
        </Tabs>

        {/* Right: search + view + refresh + new */}
        <div className="flex items-center gap-2">
          <SearchField aria-label="模型名称" value={name} onChange={setName} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-40" placeholder="按名称搜索" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <ViewToggle value={viewMode} onChange={setViewMode} />
          <Button variant="ghost" size="sm" isIconOnly onPress={loadFirstPage} aria-label="刷新">
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
          <Button size="sm" onPress={() => router.push(`/${locale}/admin/models/ai-models/new`)}>
            <Plus className="size-4" />
            新建模型
          </Button>
        </div>
      </div>

      {/* ── Content ── */}
      <ScrollShadow className="min-h-0 flex-1 overflow-y-auto">
        {renderContent()}

        <div ref={sentinelRef} className="h-10" />

        {loadingMore ? (
          <div className="mt-2 grid gap-3 md:grid-cols-3">
            {[...Array(3)].map((_, i) => <Skeleton key={i} className="h-32 rounded-xl" />)}
          </div>
        ) : null}

        {!loading && !hasMore && records.length > 0 ? (
          <p className="py-4 text-center text-xs text-muted">已加载全部 {totalRecords} 条数据</p>
        ) : null}
      </ScrollShadow>
    </div>
  );
}
