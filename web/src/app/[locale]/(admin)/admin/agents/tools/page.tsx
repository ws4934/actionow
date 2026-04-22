"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useLocale } from "next-intl";
import {
  Button,
  Chip,
  ScrollShadow,
  SearchField,
  Skeleton,
  toast,
} from "@heroui/react";
import { RefreshCw } from "lucide-react";
import { adminService, getErrorFromException } from "@/lib/api";
import type { AgentToolCatalogDTO } from "@/lib/api/dto";
import {
  getToolCatalogAccessMode,
  getToolCatalogActionType,
  getToolCatalogCategory,
  getToolCatalogDescription,
  getToolCatalogIdentifier,
  getToolCatalogMachineName,
  getToolCatalogMethodSignature,
  getToolCatalogParams,
  getToolCatalogQuotaText,
  getToolCatalogSkillNames,
  getToolCatalogSourceType,
  getToolCatalogTags,
  getToolCatalogTitle,
  getToolCatalogUsageText,
} from "@/lib/utils/tool-catalog";
import { ViewToggle, type ViewMode } from "@/components/admin/view-toggle";

const PAGE_SIZE = 12;

export default function AgentToolsPage() {
  const locale = useLocale();
  const [viewMode, setViewMode] = useState<ViewMode>("card");
  const [keyword, setKeyword] = useState("");
  const [actionType, setActionType] = useState("");
  const [tag, setTag] = useState("");

  const [records, setRecords] = useState<AgentToolCatalogDTO[]>([]);
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
        const page = await adminService.getToolCatalogPage({
          current: pageNum,
          size: PAGE_SIZE,
          keyword: keyword.trim() || undefined,
          actionType: actionType.trim() || undefined,
          tag: tag.trim() || undefined,
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
          const existed = new Set(prev.map((item) => getToolCatalogIdentifier(item)));
          const merged = [...prev];
          for (const item of page.records) {
            const identifier = getToolCatalogIdentifier(item);
            if (!existed.has(identifier)) {
              merged.push(item);
              existed.add(identifier);
            }
          }
          return merged;
        });
      } catch (error) {
        if (requestId === requestIdRef.current) {
          toast.danger(getErrorFromException(error, locale));
          setHasMore(false);
        }
      } finally {
        if (requestId === requestIdRef.current) {
          setLoading(false);
          setLoadingMore(false);
        }
      }
    },
    [actionType, keyword, locale, tag]
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

  useEffect(() => {
    loadFirstPage();
  }, [loadFirstPage]);

  useEffect(() => {
    const target = sentinelRef.current;
    if (!target || !hasMore) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadNextPage();
      },
      { root: null, rootMargin: "280px 0px", threshold: 0.1 }
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [hasMore, loadNextPage]);

  return (
    <div className="flex h-full flex-col">
      {/* ── Toolbar ── */}
      <div className="flex shrink-0 items-center justify-between gap-3 pb-4">
        <div className="flex flex-wrap items-center gap-2">
          <SearchField aria-label="关键字" value={keyword} onChange={setKeyword} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-44" placeholder="名称 / toolName / toolId" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <SearchField aria-label="Action Type" value={actionType} onChange={setActionType} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-28 font-mono text-xs" placeholder="如 WRITE" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <SearchField aria-label="Tag" value={tag} onChange={setTag} variant="secondary">
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input className="w-24 font-mono text-xs" placeholder="如 character" onKeyDown={(e) => e.key === "Enter" && loadFirstPage()} />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
        </div>
        <div className="flex items-center gap-2">
          <ViewToggle value={viewMode} onChange={setViewMode} />
          <Button variant="ghost" size="sm" isIconOnly onPress={loadFirstPage} aria-label="刷新">
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          </Button>
        </div>
      </div>

      <ScrollShadow className="min-h-0 flex-1 overflow-y-auto pt-0.5">
        {loading ? (
          <div className="grid gap-3 md:grid-cols-3">
            <Skeleton className="h-36 rounded-xl" />
            <Skeleton className="h-36 rounded-xl" />
            <Skeleton className="h-36 rounded-xl" />
          </div>
        ) : records.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border bg-surface p-10 text-center text-sm text-muted">
            暂无工具目录数据
          </div>
        ) : viewMode === "card" ? (
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {records.map((item) => {
              const toolId = getToolCatalogIdentifier(item);
              const title = getToolCatalogTitle(item);
              const machineName = getToolCatalogMachineName(item);
              const description = getToolCatalogDescription(item);
              const methodSignature = getToolCatalogMethodSignature(item);
              const skillNames = getToolCatalogSkillNames(item);
              const tags = getToolCatalogTags(item);
              const href = toolId ? `/${locale}/admin/agents/tools/${encodeURIComponent(toolId)}` : `/${locale}/admin/agents/tools`;

              return (
                <Link
                  key={toolId || title}
                  href={href}
                  className="group rounded-xl border border-border bg-surface p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-accent/70 hover:shadow-md"
                >
                  <div className="mb-2 flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h3 className="line-clamp-1 text-base font-semibold text-foreground">{title}</h3>
                      <p className="mt-0.5 font-mono text-xs text-muted">{toolId || "-"}</p>
                    </div>
                    <div className="flex flex-wrap justify-end gap-1">
                      {getToolCatalogCategory(item) ? (
                        <Chip size="sm" variant="soft" color="default">
                          {getToolCatalogCategory(item)}
                        </Chip>
                      ) : null}
                      {getToolCatalogActionType(item) ? (
                        <Chip size="sm" variant="soft" color="default">
                          {getToolCatalogActionType(item)}
                        </Chip>
                      ) : null}
                    </div>
                  </div>

                  {machineName && machineName !== title ? (
                    <p className="font-mono text-xs text-accent">{machineName}</p>
                  ) : null}

                  {description ? (
                    <p className="mt-2 line-clamp-2 text-xs text-muted">{description}</p>
                  ) : null}

                  {methodSignature ? (
                    <p className="mt-1.5 line-clamp-1 font-mono text-[11px] text-muted">{methodSignature}</p>
                  ) : null}

                  <div className="mt-2 flex flex-wrap items-center gap-1.5">
                    {getToolCatalogAccessMode(item) ? (
                      <Chip size="sm" variant="soft" color="default">
                        {getToolCatalogAccessMode(item)}
                      </Chip>
                    ) : null}
                    {getToolCatalogSourceType(item) ? (
                      <Chip size="sm" variant="soft" color="default">
                        {getToolCatalogSourceType(item)}
                      </Chip>
                    ) : null}
                    {item.enabled !== undefined ? (
                      <Chip size="sm" variant="soft" color={item.enabled ? "success" : "default"}>
                        {item.enabled ? "启用" : "禁用"}
                      </Chip>
                    ) : null}
                  </div>

                  <div className="mt-2 flex flex-wrap gap-1">
                    {skillNames.map((skill) => (
                      <span key={skill} className="rounded bg-accent/10 px-1.5 py-0.5 text-[10px] text-accent">
                        {skill}
                      </span>
                    ))}
                    {tags.map((toolTag) => (
                      <span key={toolTag} className="rounded bg-surface-secondary px-1.5 py-0.5 text-[10px] text-muted">
                        {toolTag}
                      </span>
                    ))}
                  </div>

                  <div className="mt-2 flex items-center gap-3 text-[11px] text-muted">
                    <span>{`${getToolCatalogParams(item).length} 个参数`}</span>
                    <span>{getToolCatalogQuotaText(item)}</span>
                    <span>{getToolCatalogUsageText(item)}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        ) : (
          <div className="space-y-2">
            {records.map((item) => {
              const toolId = getToolCatalogIdentifier(item);
              const title = getToolCatalogTitle(item);
              const machineName = getToolCatalogMachineName(item);
              const description = getToolCatalogDescription(item);
              const skillNames = getToolCatalogSkillNames(item);
              const tags = getToolCatalogTags(item);
              const href = toolId ? `/${locale}/admin/agents/tools/${encodeURIComponent(toolId)}` : `/${locale}/admin/agents/tools`;

              return (
                <Link
                  key={toolId || title}
                  href={href}
                  className="block rounded-xl border border-border bg-surface px-4 py-3 transition hover:border-accent/70 hover:bg-surface-secondary/30"
                >
                  <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="line-clamp-1 text-sm font-semibold text-foreground">{title}</p>
                        {getToolCatalogCategory(item) ? (
                          <Chip size="sm" variant="soft" color="default">
                            {getToolCatalogCategory(item)}
                          </Chip>
                        ) : null}
                        {getToolCatalogActionType(item) ? (
                          <Chip size="sm" variant="soft" color="default">
                            {getToolCatalogActionType(item)}
                          </Chip>
                        ) : null}
                        {getToolCatalogAccessMode(item) ? (
                          <Chip size="sm" variant="soft" color="default">
                            {getToolCatalogAccessMode(item)}
                          </Chip>
                        ) : null}
                      </div>
                      <p className="mt-0.5 font-mono text-xs text-muted">{toolId || "-"}</p>
                      {machineName && machineName !== title ? (
                        <p className="mt-0.5 font-mono text-xs text-accent">{machineName}</p>
                      ) : null}
                      {description ? (
                        <p className="mt-0.5 line-clamp-1 text-xs text-muted">{description}</p>
                      ) : null}
                      {(skillNames.length > 0 || tags.length > 0) ? (
                        <div className="mt-1 flex flex-wrap gap-1">
                          {skillNames.map((skill) => (
                            <span key={skill} className="rounded bg-accent/10 px-1.5 py-0.5 text-[10px] text-accent">
                              {skill}
                            </span>
                          ))}
                          {tags.map((toolTag) => (
                            <span key={toolTag} className="rounded bg-surface-secondary px-1.5 py-0.5 text-[10px] text-muted">
                              {toolTag}
                            </span>
                          ))}
                        </div>
                      ) : null}
                    </div>
                    <div className="flex flex-wrap items-center gap-3 lg:min-w-[260px] lg:justify-end">
                      {getToolCatalogSourceType(item) ? (
                        <span className="text-xs text-muted">{getToolCatalogSourceType(item)}</span>
                      ) : null}
                      <span className="text-xs text-muted">{`${getToolCatalogParams(item).length} 个参数`}</span>
                      <span className="text-xs text-muted">{getToolCatalogQuotaText(item)}</span>
                      <span className="text-xs text-muted">{getToolCatalogUsageText(item)}</span>
                    </div>
                  </div>
                </Link>
              );
            })}
          </div>
        )}

        <div ref={sentinelRef} className="h-10" />

        {loadingMore ? (
          <div className="mt-2 grid gap-3 md:grid-cols-3">
            <Skeleton className="h-28 rounded-xl" />
            <Skeleton className="h-28 rounded-xl" />
            <Skeleton className="h-28 rounded-xl" />
          </div>
        ) : null}

        {!loading && !hasMore && records.length > 0 ? (
          <p className="py-4 text-center text-xs text-muted">已加载全部 {totalRecords} 条数据</p>
        ) : null}
      </ScrollShadow>
    </div>
  );
}
