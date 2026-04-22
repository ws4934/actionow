"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import {
  Button,
  Chip,
  Input,
  Label,
  Modal,
  SearchField,
  Skeleton,
  Spinner,
  Switch,
  TextArea,
  TextField,
  toast,
} from "@heroui/react";
import { ArrowLeft, Code2, Eye, Plus, RefreshCw, Save, Settings2, Trash2, Zap } from "lucide-react";
import { adminService, getErrorFromException } from "@/lib/api";
import type { AgentToolCatalogDTO, CreateAgentSkillRequestDTO, UpdateAgentSkillRequestDTO } from "@/lib/api/dto";
import { ResizablePanels } from "@/components/ui/resizable-panels";
import { CodeEditor } from "@/components/admin/ai-model/code-editor";
import {
  getToolCatalogAccessMode,
  getToolCatalogActionType,
  getToolCatalogCallbackName,
  getToolCatalogCategory,
  getToolCatalogDescription,
  getToolCatalogIdentifier,
  getToolCatalogMachineName,
  getToolCatalogMethodSignature,
  getToolCatalogOutputValue,
  getToolCatalogParams,
  getToolCatalogQuotaText,
  getToolCatalogSkillNames,
  getToolCatalogSourceType,
  getToolCatalogTags,
  getToolCatalogTitle,
  getToolCatalogUsageText,
  stringifyToolCatalogValue,
} from "@/lib/utils/tool-catalog";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function uniqueList(values?: string[]): string[] {
  return Array.from(
    new Set(
      (values ?? [])
        .map((item) => item.trim())
        .filter(Boolean)
    )
  );
}

function parseJsonInput(value: string): Record<string, unknown> | null | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  try {
    return JSON.parse(trimmed) as Record<string, unknown>;
  } catch {
    return undefined;
  }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function SectionLabel({
  icon: Icon,
  title,
  iconCls,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  iconCls: string;
}) {
  return (
    <div className="mb-3 flex items-center gap-2">
      <div className={`flex size-5 shrink-0 items-center justify-center rounded ${iconCls}`}>
        <Icon className="size-3" />
      </div>
      <span className="text-[11px] font-semibold uppercase tracking-widest text-muted">{title}</span>
    </div>
  );
}

function Divider() {
  return <div className="border-t border-border/50" />;
}

function InfoBlock({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
      <p className="text-[10px] text-muted">{label}</p>
      <p className={`mt-1 text-sm text-foreground ${mono ? "font-mono" : ""}`}>{value || "-"}</p>
    </div>
  );
}

function JsonPanel({ title, value }: { title: string; value: string }) {
  return (
    <div className="overflow-hidden rounded-xl border border-[#2d2d2d] bg-[#1e1e1e] dark:bg-[#161616]">
      <div className="flex shrink-0 items-center gap-3 border-b border-[#2d2d2d] bg-[#252526] px-4 py-2">
        <span className="rounded bg-[#2d2d2d] px-1.5 py-0.5 font-mono text-[10px] text-[#9cdcfe]">{title}</span>
      </div>
      <pre className="max-h-72 overflow-auto p-4 font-mono text-xs leading-6 text-[#d4d4d4]">
        {value || "{}"}
      </pre>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AgentSkillEditPage() {
  const locale = useLocale();
  const router = useRouter();
  const params = useParams<{ name: string }>();
  const nameParam = Array.isArray(params.name) ? params.name[0] : params.name;
  const isCreate = nameParam === "new";

  const [loading, setLoading] = useState(!isCreate);
  const [submitting, setSubmitting] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const [name, setName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [content, setContent] = useState("");
  const [selectedToolIds, setSelectedToolIds] = useState<string[]>([]);
  const [outputSchemaText, setOutputSchemaText] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [jsonError, setJsonError] = useState("");
  const [selectedTools, setSelectedTools] = useState<AgentToolCatalogDTO[]>([]);
  const [selectedToolsLoading, setSelectedToolsLoading] = useState(false);
  const [selectedToolsError, setSelectedToolsError] = useState("");
  const [visibleTools, setVisibleTools] = useState<AgentToolCatalogDTO[]>([]);
  const [visibleToolsLoading, setVisibleToolsLoading] = useState(false);
  const [visibleToolsError, setVisibleToolsError] = useState("");
  const [toolPickerOpen, setToolPickerOpen] = useState(false);
  const [toolPickerKeyword, setToolPickerKeyword] = useState("");
  const [toolPickerActionType, setToolPickerActionType] = useState("");
  const [toolPickerRecords, setToolPickerRecords] = useState<AgentToolCatalogDTO[]>([]);
  const [toolPickerCurrentPage, setToolPickerCurrentPage] = useState(0);
  const [toolPickerHasMore, setToolPickerHasMore] = useState(true);
  const [toolPickerLoading, setToolPickerLoading] = useState(false);
  const [toolPickerLoadingMore, setToolPickerLoadingMore] = useState(false);
  const [viewingTool, setViewingTool] = useState<AgentToolCatalogDTO | null>(null);
  const [toolDetailOpen, setToolDetailOpen] = useState(false);
  const [toolDetailLoading, setToolDetailLoading] = useState(false);

  const selectedToolIdSet = useMemo(() => new Set(selectedToolIds), [selectedToolIds]);
  const visibleToolIds = useMemo(
    () => uniqueList(visibleTools.map((tool) => getToolCatalogIdentifier(tool)).filter(Boolean)),
    [visibleTools]
  );
  const visibleToolIdSet = useMemo(() => new Set(visibleToolIds), [visibleToolIds]);
  const addedToolIds = useMemo(
    () => selectedToolIds.filter((toolId) => !visibleToolIdSet.has(toolId)),
    [selectedToolIds, visibleToolIdSet]
  );
  const removedToolIds = useMemo(
    () => visibleToolIds.filter((toolId) => !selectedToolIdSet.has(toolId)),
    [selectedToolIdSet, visibleToolIds]
  );
  const hasPendingToolChanges = useMemo(() => {
    if (selectedToolIds.length !== visibleToolIds.length) return true;
    const selected = [...selectedToolIds].sort();
    const visible = [...visibleToolIds].sort();
    return selected.some((toolId, index) => toolId !== visible[index]);
  }, [selectedToolIds, visibleToolIds]);

  const pageTitle = useMemo(
    () => (isCreate ? "新建 Skill" : `编辑 Skill`),
    [isCreate]
  );

  // ─── Load ─────────────────────────────────────────────────────────────────

  useEffect(() => {
    if (isCreate) return;
    const load = async () => {
      try {
        setLoading(true);
        const detail = await adminService.getSkillByName(nameParam);
        setName(detail.name || "");
        setDisplayName(detail.displayName || "");
        setDescription(detail.description || "");
        setContent(detail.content || "");
        setSelectedToolIds(uniqueList(detail.groupedToolIds));
        setOutputSchemaText(detail.outputSchema ? JSON.stringify(detail.outputSchema, null, 2) : "");
        setEnabled(detail.enabled ?? true);
      } catch (error) {
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [isCreate, locale, nameParam]);

  useEffect(() => {
    const ids = selectedToolIds;
    if (ids.length === 0) {
      setSelectedTools([]);
      setSelectedToolsError("");
      setSelectedToolsLoading(false);
      return;
    }

    let cancelled = false;

    const loadSelectedTools = async () => {
      try {
        setSelectedToolsLoading(true);
        setSelectedToolsError("");
        const results = await Promise.allSettled(ids.map((toolId) => adminService.getToolCatalogByToolId(toolId)));
        if (cancelled) return;

        const nextTools: AgentToolCatalogDTO[] = [];
        const failedIds: string[] = [];

        results.forEach((result, index) => {
          if (result.status === "fulfilled") {
            nextTools.push(result.value);
          } else {
            failedIds.push(ids[index]);
          }
        });

        setSelectedTools(nextTools);
        setSelectedToolsError(failedIds.length > 0 ? `部分工具详情加载失败：${failedIds.join(", ")}` : "");
      } finally {
        if (!cancelled) setSelectedToolsLoading(false);
      }
    };

    void loadSelectedTools();

    return () => {
      cancelled = true;
    };
  }, [selectedToolIds]);

  const loadVisibleTools = useCallback(async () => {
    if (isCreate) return;
    try {
      setVisibleToolsLoading(true);
      setVisibleToolsError("");
      const tools = await adminService.getSkillVisibleTools(nameParam);
      setVisibleTools(tools);
    } catch (error) {
      setVisibleTools([]);
      setVisibleToolsError(getErrorFromException(error, locale));
    } finally {
      setVisibleToolsLoading(false);
    }
  }, [isCreate, locale, nameParam]);

  useEffect(() => {
    void loadVisibleTools();
  }, [loadVisibleTools]);

  useEffect(() => {
    if (!toolPickerOpen) return;

    let cancelled = false;
    let requestId = 0;

    const fetchFirstPage = async () => {
      const nextRequestId = ++requestId;
      try {
        setToolPickerLoading(true);
        const page = await adminService.getToolCatalogPage({
          current: 1,
          size: 12,
          keyword: toolPickerKeyword.trim() || undefined,
          actionType: toolPickerActionType.trim() || undefined,
        });
        if (cancelled || nextRequestId !== requestId) return;
        setToolPickerRecords(page.records);
        setToolPickerCurrentPage(page.current);
        setToolPickerHasMore(page.current < page.pages);
      } catch (error) {
        if (!cancelled) toast.danger(getErrorFromException(error, locale));
      } finally {
        if (!cancelled) setToolPickerLoading(false);
      }
    };

    void fetchFirstPage();

    return () => {
      cancelled = true;
    };
  }, [locale, toolPickerActionType, toolPickerKeyword, toolPickerOpen]);

  // ─── Save ─────────────────────────────────────────────────────────────────

  const validateOutputSchema = (): Record<string, unknown> | null | undefined => {
    const trimmed = outputSchemaText.trim();
    if (!trimmed) return undefined;
    const parsed = parseJsonInput(trimmed);
    if (parsed === undefined) {
      setJsonError("outputSchema JSON 格式不正确");
      return null;
    }
    setJsonError("");
    return parsed;
  };

  const handleSave = async () => {
    if (!description.trim() || !content.trim()) {
      toast.danger("描述和内容不能为空");
      return;
    }
    if (isCreate && !name.trim()) {
      toast.danger("Skill名称不能为空");
      return;
    }
    const outputSchema = validateOutputSchema();
    if (outputSchema === null) return;

    try {
      setSubmitting(true);
      if (isCreate) {
        const payload: CreateAgentSkillRequestDTO = {
          name: name.trim(),
          displayName: displayName.trim() || undefined,
          description: description.trim(),
          content,
          groupedToolIds: selectedToolIds.length > 0 ? selectedToolIds : undefined,
          outputSchema,
        };
        await adminService.createSkill(payload);
        toast.success("创建成功");
        router.replace(`/${locale}/admin/agents/skills/${name.trim()}`);
      } else {
        const payload: UpdateAgentSkillRequestDTO = {
          displayName: displayName.trim() || undefined,
          description: description.trim() || undefined,
          content: content || undefined,
          groupedToolIds: selectedToolIds.length > 0 ? selectedToolIds : undefined,
          outputSchema,
        };
        await adminService.updateSkill(nameParam, payload);
        toast.success("保存成功");
      }
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (isCreate) return;
    try {
      setSubmitting(true);
      await adminService.deleteSkill(nameParam);
      toast.success("删除成功");
      router.replace(`/${locale}/admin/agents/skills`);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setSubmitting(false);
      setDeleteModalOpen(false);
    }
  };

  const handleAddTool = (toolId: string) => {
    if (!toolId.trim()) return;
    setSelectedToolIds((prev) => {
      if (prev.includes(toolId)) return prev;
      return [...prev, toolId];
    });
    toast.success("工具已加入当前 Skill");
  };

  const handleRemoveTool = (toolId: string) => {
    setSelectedToolIds((prev) => prev.filter((item) => item !== toolId));
  };

  const handleLoadMoreTools = async () => {
    if (toolPickerLoading || toolPickerLoadingMore || !toolPickerHasMore) return;
    try {
      setToolPickerLoadingMore(true);
      const page = await adminService.getToolCatalogPage({
        current: toolPickerCurrentPage + 1,
        size: 12,
        keyword: toolPickerKeyword.trim() || undefined,
        actionType: toolPickerActionType.trim() || undefined,
      });
      setToolPickerRecords((prev) => {
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
      setToolPickerCurrentPage(page.current);
      setToolPickerHasMore(page.current < page.pages);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setToolPickerLoadingMore(false);
    }
  };

  const handleOpenToolDetail = async (tool: AgentToolCatalogDTO | string) => {
    const toolId = typeof tool === "string" ? tool : getToolCatalogIdentifier(tool);
    if (!toolId) return;

    setToolDetailOpen(true);

    if (typeof tool !== "string") {
      setViewingTool(tool);
      setToolDetailLoading(false);
      return;
    }

    try {
      setToolDetailLoading(true);
      const detail = await adminService.getToolCatalogByToolId(toolId);
      setViewingTool(detail);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
      setToolDetailOpen(false);
    } finally {
      setToolDetailLoading(false);
    }
  };

  // ─── Skeleton ─────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex h-full flex-col">
        <Skeleton className="h-14 w-full shrink-0 rounded-none" />
        <div className="flex flex-1 min-h-0 gap-3 p-3">
          <Skeleton className="h-full w-[40%] rounded-xl" />
          <Skeleton className="h-full flex-1 rounded-xl" />
        </div>
      </div>
    );
  }

  // ─── Panels ───────────────────────────────────────────────────────────────

  const leftPanel = (
    <div className="flex h-full flex-col overflow-hidden rounded-xl border border-border bg-background">
      <div className="flex-1 overflow-y-auto p-4 space-y-4" style={{ scrollbarWidth: "thin" }}>

        {/* ── 基本信息 ── */}
        <section>
          <SectionLabel icon={Settings2} title="基本信息" iconCls="bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400" />
          <div className="space-y-2.5">
            <div className="grid grid-cols-3 gap-2">
              <TextField isRequired isDisabled={!isCreate}>
                <Label className="text-xs">Skill名称</Label>
                <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="^[a-z][a-z0-9_]{1,63}$" className="font-mono text-xs" />
              </TextField>
              <TextField className="col-span-2">
                <Label className="text-xs">显示名称</Label>
                <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="用户可见的名称" />
              </TextField>
            </div>
            <TextField isRequired>
              <Label className="text-xs">描述</Label>
              <TextArea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="简短描述该 Skill 的用途" />
            </TextField>
          </div>
        </section>

        <Divider />

        {/* ── 工具管理 ── */}
        <section>
          <SectionLabel icon={Zap} title="工具管理" iconCls="bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400" />
          <div className="space-y-2.5">
            <div className="flex items-center justify-between rounded-lg border border-border bg-surface/40 px-3 py-2">
              <div>
                <p className="text-xs font-medium">工具绑定</p>
              </div>
              <div className="flex items-center gap-2">
                <Chip size="sm" variant="soft" color="default">
                  共 {selectedToolIds.length} 个
                </Chip>
                <Chip size="sm" variant="soft" color={hasPendingToolChanges ? "warning" : "success"}>
                  {hasPendingToolChanges ? "有未保存变更" : "已与当前生效一致"}
                </Chip>
                <Button variant="secondary" size="sm" onPress={() => setToolPickerOpen(true)} className="gap-1">
                  <Plus className="size-3.5" />
                  添加工具
                </Button>
              </div>
            </div>

            {visibleToolsLoading ? (
              <div className="rounded-xl border border-border bg-surface/40 px-3 py-2 text-xs text-muted">
                正在同步当前生效状态…
              </div>
            ) : null}

            {visibleToolsError ? (
              <div className="rounded-xl border border-danger/20 bg-danger/5 px-3 py-2 text-xs text-danger">
                {visibleToolsError}
              </div>
            ) : null}

            {hasPendingToolChanges ? (
              <div className="rounded-xl border border-warning/20 bg-warning/5 px-3 py-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Chip size="sm" variant="soft" color="warning">
                    待新增 {addedToolIds.length}
                  </Chip>
                  <Chip size="sm" variant="soft" color="warning">
                    待移除 {removedToolIds.length}
                  </Chip>
                  <Button variant="tertiary" size="sm" onPress={() => void loadVisibleTools()} className="gap-1">
                    <RefreshCw className="size-3.5" />
                    刷新当前状态
                  </Button>
                </div>
                {removedToolIds.length > 0 ? (
                  <div className="mt-2">
                    <p className="text-[10px] text-warning">保存后将移除</p>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {removedToolIds.map((toolId) => (
                        <Chip key={toolId} size="sm" variant="soft" color="warning">
                          {toolId}
                        </Chip>
                      ))}
                    </div>
                  </div>
                ) : null}
              </div>
            ) : null}

            <div className="grid gap-3 lg:grid-cols-2">
              <div className="space-y-2 lg:col-span-2">
                {selectedToolsLoading ? (
                  <div className="space-y-2">
                    <Skeleton className="h-14 rounded-xl" />
                    <Skeleton className="h-14 rounded-xl" />
                  </div>
                ) : selectedToolIds.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-border bg-surface/40 px-3 py-4 text-center text-xs text-muted">
                    当前还没有选择工具
                  </div>
                ) : (
                  <div className="space-y-2">
                    {selectedToolIds.map((toolId) => {
                      const tool = selectedTools.find((item) => getToolCatalogIdentifier(item) === toolId);
                      const title = getToolCatalogTitle(tool) || toolId;
                      const actionType = getToolCatalogActionType(tool);
                      const isEffective = visibleToolIdSet.has(toolId);
                      const isAdded = addedToolIds.includes(toolId);

                      return (
                        <div key={toolId} className="rounded-xl border border-border bg-surface/40 px-3 py-2.5">
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                <p className="line-clamp-1 font-mono text-sm font-semibold text-foreground">{title}</p>
                                {actionType ? (
                                  <Chip size="sm" variant="soft" color="default">
                                    {actionType}
                                  </Chip>
                                ) : null}
                                <Chip size="sm" variant="soft" color={isAdded ? "warning" : isEffective ? "success" : "default"}>
                                  {isAdded ? "待新增" : isEffective ? "已生效" : "未生效"}
                                </Chip>
                              </div>
                              <p className="mt-1 font-mono text-[11px] text-muted">{toolId}</p>
                            </div>
                            <div className="flex shrink-0 items-center gap-2">
                              <Button variant="tertiary" size="sm" onPress={() => void handleOpenToolDetail(tool ?? toolId)} className="gap-1">
                                <Eye className="size-3.5" />
                                详情
                              </Button>
                              <Button variant="danger" size="sm" onPress={() => handleRemoveTool(toolId)} className="gap-1">
                                <Trash2 className="size-3.5" />
                                移除
                              </Button>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}

                {selectedToolsError ? (
                  <div className="rounded-xl border border-warning/20 bg-warning/5 px-3 py-2 text-xs text-warning">
                    {selectedToolsError}
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </section>

        <Divider />

        <section>
          <SectionLabel icon={Settings2} title="输出结构" iconCls="bg-violet-50 text-violet-600 dark:bg-violet-950/40 dark:text-violet-400" />
          <TextField isInvalid={!!jsonError} validationBehavior="aria">
            <Label className="text-xs">outputSchema（JSON，可选）</Label>
            <TextArea
              rows={4}
              value={outputSchemaText}
              onChange={(e) => {
                setOutputSchemaText(e.target.value);
                setJsonError("");
              }}
              placeholder='{"type":"object","properties":{}}'
              className="font-mono text-xs"
            />
            {jsonError ? <p className="mt-1 text-xs text-danger">{jsonError}</p> : null}
          </TextField>
        </section>

        <Divider />

        {/* ── 启用状态 ── */}
        <section>
          <div className="flex items-center justify-between rounded-lg border border-border bg-surface/40 px-3 py-2">
            <div>
              <p className="text-xs font-medium">启用状态</p>
              <p className="text-[10px] text-muted">在生产环境提供服务</p>
            </div>
            <Switch isSelected={enabled} onChange={setEnabled} size="sm" />
          </div>
        </section>

        <div className="h-2" />
      </div>
    </div>
  );

  const rightPanel = (
    <div className="flex h-full flex-col overflow-hidden rounded-xl bg-[#1e1e1e] dark:bg-[#161616]">
      <div className="flex shrink-0 items-center gap-3 border-b border-[#2d2d2d] bg-[#252526] px-4 py-2">
        <span className="rounded bg-[#2d2d2d] px-1.5 py-0.5 font-mono text-[10px] text-[#9cdcfe]">Skill</span>
        <span className="text-[11px] text-[#6e6e6e]">Skill 内容（System Prompt）</span>
      </div>
      <div className="flex-1 min-h-0">
        <CodeEditor
          value={content}
          onChange={setContent}
          lang="markdown"
          placeholder="输入该 Skill 的完整系统提示词内容..."
        />
      </div>
    </div>
  );

  // ─── Render ───────────────────────────────────────────────────────────────

  return (
    <div className="flex h-full flex-col">

      {/* ── Top bar ── */}
      <div className="flex shrink-0 items-center gap-2">
        <Button
          variant="tertiary"
          size="sm"
          onPress={() => router.push(`/${locale}/admin/agents/skills`)}
          className="shrink-0 gap-1"
        >
          <ArrowLeft className="size-3.5" />
          返回
        </Button>

        <div className="mx-1 h-4 w-px shrink-0 bg-border" />

        <div className="flex min-w-0 flex-1 items-center gap-2">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-accent/10">
            <Code2 className="size-3.5 text-accent" />
          </div>
          <h1 className="truncate text-sm font-semibold text-foreground">{displayName || name || pageTitle}</h1>
          {!isCreate && (
            <span className="shrink-0 rounded-md bg-surface-secondary px-1.5 py-0.5 font-mono text-[10px] text-muted">
              {nameParam}
            </span>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <Switch isSelected={enabled} onChange={setEnabled} size="sm">
            <span className={`text-xs font-medium ${enabled ? "text-green-600 dark:text-green-400" : "text-muted"}`}>
              {enabled ? "启用" : "禁用"}
            </span>
          </Switch>
          <div className="h-4 w-px bg-border" />
          {!isCreate && (
            <Button variant="danger" size="sm" isDisabled={submitting} onPress={() => setDeleteModalOpen(true)} className="gap-1">
              <Trash2 className="size-3.5" />
              删除
            </Button>
          )}
          <Button variant="primary" size="sm" isPending={submitting} onPress={handleSave} className="gap-1">
            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Save className="size-3.5" />}保存</>)}
          </Button>
        </div>
      </div>

      {/* ── Resizable panels ── */}
      <div className="flex-1 min-h-0">
        <ResizablePanels
          defaultLeftWidth={40}
          minLeftWidth={28}
          maxLeftWidth={60}
          leftPanel={leftPanel}
          rightPanel={rightPanel}
        />
      </div>

      {/* ── Tool Picker Modal ── */}
      <Modal.Backdrop isOpen={toolPickerOpen} onOpenChange={setToolPickerOpen}>
        <Modal.Container size="lg">
          <Modal.Dialog className="max-w-4xl">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>添加工具</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4 overflow-visible">
              <div className="flex flex-wrap items-end gap-3">
                <SearchField
                  aria-label="搜索工具"
                  value={toolPickerKeyword}
                  onChange={setToolPickerKeyword}
                  variant="secondary"
                >
                  <SearchField.Group>
                    <SearchField.SearchIcon />
                    <SearchField.Input className="w-64" placeholder="搜索 displayName / toolName / toolId" />
                    <SearchField.ClearButton />
                  </SearchField.Group>
                </SearchField>
                <TextField className="min-w-40">
                  <Label className="text-xs text-muted">Action Type</Label>
                  <Input
                    value={toolPickerActionType}
                    onChange={(e) => setToolPickerActionType(e.target.value)}
                    placeholder="如 WRITE"
                    className="font-mono text-xs"
                  />
                </TextField>
                <Button variant="tertiary" onPress={() => {
                  setToolPickerKeyword("");
                  setToolPickerActionType("");
                }} className="gap-1">
                  <RefreshCw className="size-4" />
                  重置
                </Button>
              </div>

              <div className="max-h-[55vh] overflow-y-auto space-y-2 pr-1">
                {toolPickerLoading ? (
                  <>
                    <Skeleton className="h-20 rounded-xl" />
                    <Skeleton className="h-20 rounded-xl" />
                  </>
                ) : toolPickerRecords.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-border bg-surface p-8 text-center text-sm text-muted">
                    没有找到可添加的工具
                  </div>
                ) : (
                  toolPickerRecords.map((tool) => {
                    const toolId = getToolCatalogIdentifier(tool);
                    const title = getToolCatalogTitle(tool);
                    const machineName = getToolCatalogMachineName(tool);
                    const actionType = getToolCatalogActionType(tool);
                    const category = getToolCatalogCategory(tool);
                    const accessMode = getToolCatalogAccessMode(tool);
                    const description = getToolCatalogDescription(tool);
                    const disabled = !toolId || selectedToolIdSet.has(toolId);

                    return (
                      <div key={toolId || title} className="rounded-xl border border-border bg-surface/40 px-3 py-3">
                        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                          <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                              <p className="font-mono text-sm font-semibold text-foreground">{title}</p>
                              {category ? (
                                <Chip size="sm" variant="soft" color="default">
                                  {category}
                                </Chip>
                              ) : null}
                              {actionType ? (
                                <Chip size="sm" variant="soft" color="default">
                                  {actionType}
                                </Chip>
                              ) : null}
                              {accessMode ? (
                                <Chip size="sm" variant="soft" color="default">
                                  {accessMode}
                                </Chip>
                              ) : null}
                            </div>
                            {machineName ? (
                              <p className="mt-1 font-mono text-xs text-accent">{machineName}</p>
                            ) : null}
                            <p className="mt-1 font-mono text-[11px] text-muted">{toolId || "-"}</p>
                            {description ? (
                              <p className="mt-1 line-clamp-2 text-xs leading-5 text-muted">{description}</p>
                            ) : null}
                          </div>
                          <div className="flex shrink-0 items-center gap-2">
                            <Button variant="tertiary" size="sm" onPress={() => void handleOpenToolDetail(tool)} className="gap-1">
                              <Eye className="size-3.5" />
                              详情
                            </Button>
                            <Button
                              variant="primary"
                              size="sm"
                              isDisabled={disabled}
                              onPress={() => {
                                if (!toolId) return;
                                handleAddTool(toolId);
                              }}
                              className="gap-1"
                            >
                              <Plus className="size-3.5" />
                              {disabled ? "已添加" : "添加"}
                            </Button>
                          </div>
                        </div>
                      </div>
                    );
                  })
                )}

                {toolPickerLoadingMore ? <Skeleton className="h-16 rounded-xl" /> : null}
              </div>
            </Modal.Body>
            <Modal.Footer>
              {toolPickerHasMore ? (
                <Button variant="secondary" onPress={() => void handleLoadMoreTools()} isPending={toolPickerLoadingMore}>
                  {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}加载更多</>)}
                </Button>
              ) : <div />}
              <Button variant="secondary" slot="close">
                关闭
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* ── Tool Detail Modal ───────────────────────────────────────────── */}
      <Modal.Backdrop
        isOpen={toolDetailOpen}
        onOpenChange={(open) => {
          setToolDetailOpen(open);
          if (!open) setViewingTool(null);
        }}
      >
        <Modal.Container size="lg">
          <Modal.Dialog className="max-w-5xl">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>{getToolCatalogTitle(viewingTool) || "工具详情"}</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4 max-h-[70vh] overflow-y-auto">
              {toolDetailLoading ? (
                <>
                  <Skeleton className="h-20 rounded-xl" />
                  <Skeleton className="h-48 rounded-xl" />
                </>
              ) : viewingTool ? (
                <>
                  <div className="flex flex-wrap gap-2">
                    {getToolCatalogCategory(viewingTool) ? (
                      <Chip size="sm" variant="soft" color="default">
                        {getToolCatalogCategory(viewingTool)}
                      </Chip>
                    ) : null}
                    {getToolCatalogActionType(viewingTool) ? (
                      <Chip size="sm" variant="soft" color="default">
                        {getToolCatalogActionType(viewingTool)}
                      </Chip>
                    ) : null}
                    {getToolCatalogAccessMode(viewingTool) ? (
                      <Chip size="sm" variant="soft" color="default">
                        {getToolCatalogAccessMode(viewingTool)}
                      </Chip>
                    ) : null}
                    {getToolCatalogSourceType(viewingTool) ? (
                      <Chip size="sm" variant="soft" color="default">
                        {getToolCatalogSourceType(viewingTool)}
                      </Chip>
                    ) : null}
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <InfoBlock label="toolId" value={getToolCatalogIdentifier(viewingTool)} mono />
                    <InfoBlock label="toolName" value={getToolCatalogMachineName(viewingTool)} mono />
                    <InfoBlock label="callbackName" value={getToolCatalogCallbackName(viewingTool)} mono />
                    <InfoBlock label="implementation" value={getToolCatalogMethodSignature(viewingTool)} mono />
                    <InfoBlock label="quota" value={getToolCatalogQuotaText(viewingTool)} />
                    <InfoBlock label="usedToday" value={getToolCatalogUsageText(viewingTool)} />
                  </div>

                  {getToolCatalogDescription(viewingTool) ? (
                    <InfoBlock label="description" value={getToolCatalogDescription(viewingTool)} />
                  ) : null}

                  {getToolCatalogSkillNames(viewingTool).length > 0 ? (
                    <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <p className="text-[10px] text-muted">skillNames</p>
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {getToolCatalogSkillNames(viewingTool).map((skill) => (
                          <Chip key={skill} size="sm" variant="soft" color="accent">
                            {skill}
                          </Chip>
                        ))}
                      </div>
                    </div>
                  ) : null}

                  {getToolCatalogTags(viewingTool).length > 0 ? (
                    <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <p className="text-[10px] text-muted">tags</p>
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {getToolCatalogTags(viewingTool).map((tag) => (
                          <Chip key={tag} size="sm" variant="soft" color="default">
                            {tag}
                          </Chip>
                        ))}
                      </div>
                    </div>
                  ) : null}

                  {getToolCatalogParams(viewingTool).length > 0 ? (
                    <div className="space-y-2">
                      <p className="text-xs font-medium text-foreground">参数</p>
                      {getToolCatalogParams(viewingTool).map((param, index) => (
                        <div key={`${param.name || "param"}-${index}`} className="rounded-lg border border-border bg-surface/40 px-3 py-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="font-mono text-sm font-semibold text-foreground">{param.name || `param_${index + 1}`}</p>
                            {param.type ? (
                              <Chip size="sm" variant="soft" color="default">
                                {param.type}
                              </Chip>
                            ) : null}
                            {param.required !== undefined ? (
                              <Chip size="sm" variant="soft" color={param.required ? "success" : "default"}>
                                {param.required ? "必填" : "可选"}
                              </Chip>
                            ) : null}
                          </div>
                          {param.description ? (
                            <p className="mt-1 text-xs leading-5 text-muted">{param.description}</p>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  ) : null}

                  <JsonPanel title="inputSchema" value={stringifyToolCatalogValue(viewingTool.inputSchema)} />
                  <JsonPanel title="output" value={stringifyToolCatalogValue(getToolCatalogOutputValue(viewingTool))} />
                  {viewingTool.exampleInput ? (
                    <JsonPanel title="exampleInput" value={stringifyToolCatalogValue(viewingTool.exampleInput)} />
                  ) : null}
                  {viewingTool.exampleOutput ? (
                    <JsonPanel title="exampleOutput" value={stringifyToolCatalogValue(viewingTool.exampleOutput)} />
                  ) : null}
                </>
              ) : (
                <p className="text-sm text-muted">未找到工具详情</p>
              )}
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">关闭</Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* ── Delete modal ── */}
      <Modal.Backdrop isOpen={deleteModalOpen} onOpenChange={(open) => !open && setDeleteModalOpen(false)}>
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>确认删除</Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              <p className="text-sm text-muted">
                确认删除 Skill{" "}
                <span className="font-mono font-semibold text-foreground">{nameParam}</span>{" "}
                吗？此操作不可撤销。
              </p>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">取消</Button>
              <Button variant="danger" onPress={handleDeleteConfirm} isPending={submitting}>{({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}删除</>)}</Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}
