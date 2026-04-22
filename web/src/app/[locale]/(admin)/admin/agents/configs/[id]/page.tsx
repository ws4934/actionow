"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import {
  Button,
  Chip,
  Input,
  Label,
  ListBox,
  Modal,
  Select,
  Skeleton,
  Spinner,
  Switch,
  TextArea,
  TextField,
  toast,
} from "@heroui/react";
import { ArrowLeft, Bot, CheckCircle2, Eye, Power, RotateCcw, Save, Settings2, Shield, Trash2, Users, Zap } from "lucide-react";
import type { Key as ReactKey } from "react";
import { adminService, agentService, getErrorFromException } from "@/lib/api";
import type {
  AgentCatalogItemDTO,
  AgentConfigVersionDTO,
  ResolvedAgentProfileDTO,
  ResolvedSkillInfoDTO,
  SaveAgentConfigRequestDTO,
  AgentToolCatalogDTO,
} from "@/lib/api/dto";
import { ResizablePanels } from "@/components/ui/resizable-panels";
import { CodeEditor } from "@/components/admin/ai-model/code-editor";
import { getToolCatalogIdentifier, getToolCatalogTitle } from "@/lib/utils/tool-catalog";

// ─── Helpers ──────────────────────────────────────────────────────────────────

function parseListInput(value: string): string[] | undefined {
  const items = value.split(/[\n,]/).map((item) => item.trim()).filter(Boolean);
  return items.length > 0 ? items : undefined;
}

function toListInput(values?: string[]): string {
  return values?.join("\n") || "";
}

function formatJson(value: unknown): string {
  if (value === undefined) return "";
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
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

const SCOPE_OPTIONS = ["SYSTEM", "WORKSPACE", "USER"];
const SKILL_LOAD_MODE_OPTIONS = ["ALL_ENABLED", "DEFAULT_ONLY", "REQUEST_SCOPED", "DISABLED"];
const EXECUTION_MODE_OPTIONS = ["CHAT", "MISSION", "BOTH"];

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AgentConfigEditPage() {
  const locale = useLocale();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const isCreate = id === "new";

  const [loading, setLoading] = useState(!isCreate);
  const [submitting, setSubmitting] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  const [agentType, setAgentType] = useState("");
  const [agentName, setAgentName] = useState("");
  const [scope, setScope] = useState("SYSTEM");
  const [workspaceId, setWorkspaceId] = useState("");
  const [llmProviderId, setLlmProviderId] = useState("");
  const [promptContent, setPromptContent] = useState("");
  const [includesText, setIncludesText] = useState("");
  const [defaultSkillNamesText, setDefaultSkillNamesText] = useState("");
  const [allowedSkillNamesText, setAllowedSkillNamesText] = useState("");
  const [skillLoadMode, setSkillLoadMode] = useState("ALL_ENABLED");
  const [executionMode, setExecutionMode] = useState("BOTH");
  const [subAgentTypesText, setSubAgentTypesText] = useState("");
  const [tagsText, setTagsText] = useState("");
  const [iconUrl, setIconUrl] = useState("");
  const [description, setDescription] = useState("");
  const [changeSummary, setChangeSummary] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [isSystem, setIsSystem] = useState(false);
  const [isCoordinator, setIsCoordinator] = useState(false);
  const [standaloneEnabled, setStandaloneEnabled] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState("");
  const [availableAgents, setAvailableAgents] = useState<AgentCatalogItemDTO[]>([]);
  const [standaloneAgents, setStandaloneAgents] = useState<AgentCatalogItemDTO[]>([]);
  const [coordinatorAgents, setCoordinatorAgents] = useState<AgentCatalogItemDTO[]>([]);
  const [resolvedProfile, setResolvedProfile] = useState<ResolvedAgentProfileDTO | null>(null);
  const [resolvedSkills, setResolvedSkills] = useState<ResolvedSkillInfoDTO[]>([]);
  const [resolvedTools, setResolvedTools] = useState<AgentToolCatalogDTO[]>([]);
  const [versionsOpen, setVersionsOpen] = useState(false);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [versionsError, setVersionsError] = useState("");
  const [versions, setVersions] = useState<AgentConfigVersionDTO[]>([]);
  const [rollbackingVersion, setRollbackingVersion] = useState<number | null>(null);

  const pageTitle = useMemo(() => (isCreate ? "新建 Agent 配置" : "编辑 Agent 配置"), [isCreate]);

  // ─── Load ────────────────────────────────────────────────────────────────

  const loadConfigDetail = useCallback(async () => {
    const detail = await adminService.getAgentConfigById(id);
    setAgentType(detail.agentType || "");
    setAgentName(detail.agentName || "");
    setScope(detail.scope || "SYSTEM");
    setWorkspaceId(detail.workspaceId || "");
    setLlmProviderId(detail.llmProviderId || "");
    setPromptContent(detail.promptContent || "");
    setIncludesText(toListInput(detail.includes));
    setDefaultSkillNamesText(toListInput(detail.defaultSkillNames));
    setAllowedSkillNamesText(toListInput(detail.allowedSkillNames));
    setSkillLoadMode(detail.skillLoadMode || "ALL_ENABLED");
    setExecutionMode(detail.executionMode || "BOTH");
    setSubAgentTypesText(toListInput(detail.subAgentTypes));
    setTagsText(toListInput(detail.tags));
    setIconUrl(detail.iconUrl || "");
    setDescription(detail.description || "");
    setEnabled(detail.enabled ?? true);
    setIsSystem(detail.isSystem ?? false);
    setIsCoordinator(detail.isCoordinator ?? false);
    setStandaloneEnabled(detail.standaloneEnabled ?? false);
  }, [id]);

  useEffect(() => {
    if (isCreate) return;
    const load = async () => {
      try {
        setLoading(true);
        await loadConfigDetail();
      } catch (error) {
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [isCreate, loadConfigDetail, locale]);

  // ─── Save ────────────────────────────────────────────────────────────────

  const handleSave = async () => {
    if (!agentType.trim() || !agentName.trim() || !promptContent.trim()) {
      toast.danger("Agent类型、名称、提示词内容不能为空");
      return;
    }
    if (scope === "WORKSPACE" && !workspaceId.trim()) {
      toast.danger("WORKSPACE 作用域必须填写 workspaceId");
      return;
    }
    const payload: SaveAgentConfigRequestDTO = {
      agentType: agentType.trim(),
      agentName: agentName.trim(),
      scope,
      workspaceId: workspaceId.trim() || undefined,
      llmProviderId: llmProviderId.trim() || undefined,
      promptContent,
      includes: parseListInput(includesText),
      defaultSkillNames: parseListInput(defaultSkillNamesText),
      allowedSkillNames: parseListInput(allowedSkillNamesText),
      skillLoadMode: skillLoadMode || undefined,
      executionMode: executionMode || undefined,
      enabled,
      isSystem,
      isCoordinator,
      subAgentTypes: parseListInput(subAgentTypesText),
      standaloneEnabled,
      iconUrl: iconUrl.trim() || undefined,
      tags: parseListInput(tagsText),
      description: description.trim() || undefined,
      changeSummary: changeSummary.trim() || undefined,
    };
    try {
      setSubmitting(true);
      if (isCreate) {
        const created = await adminService.createAgentConfig(payload);
        toast.success("创建成功");
        router.replace(`/${locale}/admin/agents/configs/${created.id}`);
      } else {
        await adminService.updateAgentConfig(id, payload);
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
    setDeleteModalOpen(false);
    try {
      setSubmitting(true);
      await adminService.deleteAgentConfig(id);
      toast.success("删除成功");
      router.replace(`/${locale}/admin/agents/configs`);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setSubmitting(false);
    }
  };

  const onScopeChange = (key: ReactKey | ReactKey[] | null) => {
    if (key && !Array.isArray(key)) setScope(String(key));
  };

  const onSkillLoadModeChange = (key: ReactKey | ReactKey[] | null) => {
    if (key && !Array.isArray(key)) setSkillLoadMode(String(key));
  };

  const onExecutionModeChange = (key: ReactKey | ReactKey[] | null) => {
    if (key && !Array.isArray(key)) setExecutionMode(String(key));
  };

  const handleOpenPreview = async () => {
    if (!agentType.trim()) {
      toast.danger("请先填写 Agent 类型");
      return;
    }

    try {
      setPreviewOpen(true);
      setPreviewLoading(true);
      setPreviewError("");

      const [available, standalone, coordinators, resolved, skills, tools] = await Promise.all([
        agentService.getAvailableAgents(),
        agentService.getStandaloneAgents(),
        agentService.getCoordinatorAgents(),
        agentService.getResolvedAgent(agentType.trim()),
        agentService.getResolvedAgentSkills(agentType.trim()),
        agentService.getResolvedAgentTools(agentType.trim()),
      ]);

      setAvailableAgents(available);
      setStandaloneAgents(standalone);
      setCoordinatorAgents(coordinators);
      setResolvedProfile(resolved);
      setResolvedSkills(skills);
      setResolvedTools(tools);
    } catch (error) {
      setPreviewError(getErrorFromException(error, locale));
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleOpenVersions = async () => {
    if (isCreate) return;
    try {
      setVersionsOpen(true);
      setVersionsLoading(true);
      setVersionsError("");
      const result = await adminService.getAgentConfigVersions(id);
      setVersions(result);
    } catch (error) {
      setVersionsError(getErrorFromException(error, locale));
    } finally {
      setVersionsLoading(false);
    }
  };

  const handleRollbackVersion = async (version: number) => {
    if (isCreate) return;
    try {
      setRollbackingVersion(version);
      await adminService.rollbackAgentConfig(id, version);
      await Promise.all([
        loadConfigDetail(),
        adminService.getAgentConfigVersions(id).then((result) => setVersions(result)),
      ]);
      toast.success(`已回滚到版本 ${version}`);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setRollbackingVersion(null);
    }
  };

  // ─── Skeleton ─────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex h-full flex-col">
        <Skeleton className="h-14 w-full shrink-0 rounded-none" />
        <div className="flex flex-1 min-h-0 gap-3 p-3">
          <Skeleton className="h-full w-[45%] rounded-xl" />
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
          <SectionLabel icon={Bot} title="基本信息" iconCls="bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400" />
          <div className="space-y-2.5">
            <div className="grid grid-cols-2 gap-2">
              <TextField isRequired>
                <Label className="text-xs">Agent类型</Label>
                <Input value={agentType} onChange={(e) => setAgentType(e.target.value)} placeholder="STORYBOARD_EXPERT" className="font-mono text-xs" />
              </TextField>
              <TextField isRequired>
                <Label className="text-xs">Agent名称</Label>
                <Input value={agentName} onChange={(e) => setAgentName(e.target.value)} placeholder="分镜专家" />
              </TextField>
            </div>

            <Select value={scope} onChange={onScopeChange}>
              <Label className="text-xs">作用域</Label>
              <Select.Trigger>
                <Select.Value>{() => <span>{scope}</span>}</Select.Value>
                <Select.Indicator />
              </Select.Trigger>
              <Select.Popover>
                <ListBox>
                  {SCOPE_OPTIONS.map((v) => (
                    <ListBox.Item key={v} id={v} textValue={v}>
                      {v}
                      <ListBox.ItemIndicator />
                    </ListBox.Item>
                  ))}
                </ListBox>
              </Select.Popover>
            </Select>

            {scope === "WORKSPACE" ? (
              <TextField isRequired>
                <Label className="text-xs">workspaceId</Label>
                <Input value={workspaceId} onChange={(e) => setWorkspaceId(e.target.value)} placeholder="scope=WORKSPACE 时必填" className="font-mono text-xs" />
              </TextField>
            ) : null}

            <TextField>
              <Label className="text-xs">LLM Provider ID</Label>
              <Input value={llmProviderId} onChange={(e) => setLlmProviderId(e.target.value)} className="font-mono text-xs" />
            </TextField>
          </div>
        </section>

        <Divider />

        {/* ── 展示配置 ── */}
        <section>
          <SectionLabel icon={Settings2} title="展示配置" iconCls="bg-violet-50 text-violet-600 dark:bg-violet-950/40 dark:text-violet-400" />
          <div className="space-y-2.5">
            <TextField>
              <Label className="text-xs">iconUrl</Label>
              <Input value={iconUrl} onChange={(e) => setIconUrl(e.target.value)} />
            </TextField>
            <TextField>
              <Label className="text-xs">changeSummary</Label>
              <Input value={changeSummary} onChange={(e) => setChangeSummary(e.target.value)} />
            </TextField>
            <TextField>
              <Label className="text-xs">描述</Label>
              <TextArea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
            </TextField>
          </div>
        </section>

        <Divider />

        {/* ── Skill 策略 ── */}
        <section>
          <SectionLabel icon={Users} title="Skill 策略" iconCls="bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400" />
          <div className="space-y-2.5">
            <div className="grid grid-cols-2 gap-2">
              <Select value={skillLoadMode} onChange={onSkillLoadModeChange}>
                <Label className="text-xs">skillLoadMode</Label>
                <Select.Trigger>
                  <Select.Value>{() => <span>{skillLoadMode}</span>}</Select.Value>
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {SKILL_LOAD_MODE_OPTIONS.map((v) => (
                      <ListBox.Item key={v} id={v} textValue={v}>
                        {v}
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
              <Select value={executionMode} onChange={onExecutionModeChange}>
                <Label className="text-xs">executionMode</Label>
                <Select.Trigger>
                  <Select.Value>{() => <span>{executionMode}</span>}</Select.Value>
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {EXECUTION_MODE_OPTIONS.map((v) => (
                      <ListBox.Item key={v} id={v} textValue={v}>
                        {v}
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <TextField>
                <Label className="text-xs">defaultSkillNames（每行一个）</Label>
                <TextArea rows={4} value={defaultSkillNamesText} onChange={(e) => setDefaultSkillNamesText(e.target.value)} className="font-mono text-xs" />
              </TextField>
              <TextField>
                <Label className="text-xs">allowedSkillNames（每行一个）</Label>
                <TextArea rows={4} value={allowedSkillNamesText} onChange={(e) => setAllowedSkillNamesText(e.target.value)} className="font-mono text-xs" />
              </TextField>
            </div>
          </div>
        </section>

        <Divider />

        {/* ── 协调者配置 ── */}
        <section>
          <SectionLabel icon={Zap} title="协调者配置" iconCls="bg-green-50 text-green-600 dark:bg-green-950/40 dark:text-green-400" />
          <div className="grid grid-cols-3 gap-2">
            <TextField>
              <Label className="text-xs">includes（每行一个）</Label>
              <TextArea rows={4} value={includesText} onChange={(e) => setIncludesText(e.target.value)} className="font-mono text-xs" />
            </TextField>
            <TextField>
              <Label className="text-xs">subAgentTypes（每行一个）</Label>
              <TextArea rows={4} value={subAgentTypesText} onChange={(e) => setSubAgentTypesText(e.target.value)} className="font-mono text-xs" />
            </TextField>
            <TextField>
              <Label className="text-xs">tags（每行一个）</Label>
              <TextArea rows={4} value={tagsText} onChange={(e) => setTagsText(e.target.value)} className="font-mono text-xs" />
            </TextField>
          </div>
        </section>

        <Divider />

        {/* ── 运行时预览 ── */}
        <section>
          <SectionLabel icon={Eye} title="解析预览" iconCls="bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-400" />
          <div className="space-y-2.5">
            <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
              <p className="text-xs font-medium">运行时解析结果</p>
              <p className="text-[10px] text-muted">基于当前已保存的 Agent 配置查询可用目录、解析出的 Skills 和最终 Tools。</p>
            </div>
            <Button variant="secondary" onPress={() => void handleOpenPreview()} className="gap-1">
              <Eye className="size-3.5" />
              查看解析结果
            </Button>
          </div>
        </section>

        <Divider />

        {/* ── 配置标志 ── */}
        <section>
          <SectionLabel icon={Zap} title="配置标志" iconCls="bg-green-50 text-green-600 dark:bg-green-950/40 dark:text-green-400" />
          <div className="grid grid-cols-2 gap-1.5">
            {([
              { key: "enabled", label: "启用", desc: "在生产环境中可用", Icon: Power, val: enabled, toggle: setEnabled },
              { key: "isSystem", label: "系统配置", desc: "系统内置，不可删除", Icon: Shield, val: isSystem, toggle: setIsSystem },
              { key: "isCoordinator", label: "协调者", desc: "可调度其他 Agent", Icon: Users, val: isCoordinator, toggle: setIsCoordinator },
              { key: "standalone", label: "支持独立调用", desc: "允许直接调用此 Agent", Icon: Zap, val: standaloneEnabled, toggle: setStandaloneEnabled },
            ] as const).map(({ key, label, desc, Icon, val, toggle }) => (
              <button
                key={key}
                type="button"
                onClick={() => toggle(!val)}
                className={`relative flex items-center gap-2 rounded-lg border p-2.5 text-left transition-all ${
                  val
                    ? "border-accent bg-accent/5"
                    : "border-border hover:border-muted hover:bg-surface"
                }`}
              >
                <Icon className="size-3.5 shrink-0 text-muted" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-medium leading-none">{label}</p>
                  <p className="mt-1 truncate text-[10px] text-muted">{desc}</p>
                </div>
                {val && (
                  <CheckCircle2 className="absolute right-1.5 top-1.5 size-3 text-accent" />
                )}
              </button>
            ))}
          </div>
        </section>

        <div className="h-2" />
      </div>
    </div>
  );

  const rightPanel = (
    <div className="flex h-full flex-col overflow-hidden rounded-xl bg-[#1e1e1e] dark:bg-[#161616]">
      <div className="flex shrink-0 items-center gap-3 border-b border-[#2d2d2d] bg-[#252526] px-4 py-2">
        <span className="rounded bg-[#2d2d2d] px-1.5 py-0.5 font-mono text-[10px] text-[#9cdcfe]">Prompt</span>
        <span className="text-[11px] text-[#6e6e6e]">提示词内容（System Prompt）</span>
      </div>
      <div className="flex-1 min-h-0">
        <CodeEditor
          value={promptContent}
          onChange={setPromptContent}
          lang="markdown"
          placeholder="输入该 Agent 的系统提示词..."
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
          onPress={() => router.push(`/${locale}/admin/agents/configs`)}
          className="shrink-0 gap-1"
        >
          <ArrowLeft className="size-3.5" />
          返回
        </Button>

        <div className="mx-1 h-4 w-px shrink-0 bg-border" />

        <div className="flex min-w-0 flex-1 items-center gap-2">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-accent/10">
            <Bot className="size-3.5 text-accent" />
          </div>
          <h1 className="truncate text-sm font-semibold text-foreground">{agentName || pageTitle}</h1>
          {agentType ? (
            <span className="shrink-0 rounded-md bg-blue-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
              {agentType}
            </span>
          ) : null}
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <Switch isSelected={enabled} onChange={setEnabled} size="sm">
            <Switch.Control>
              <Switch.Thumb />
            </Switch.Control>
            <span className={`text-xs font-medium ${enabled ? "text-green-600 dark:text-green-400" : "text-muted"}`}>
              {enabled ? "启用" : "禁用"}
            </span>
          </Switch>
          <div className="h-4 w-px bg-border" />
          {!isCreate && (
            <Button variant="secondary" size="sm" isDisabled={submitting} onPress={() => void handleOpenVersions()} className="gap-1">
              <RotateCcw className="size-3.5" />
              版本
            </Button>
          )}
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
          defaultLeftWidth={45}
          minLeftWidth={32}
          maxLeftWidth={65}
          leftPanel={leftPanel}
          rightPanel={rightPanel}
        />
      </div>

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
                确认删除配置「<span className="font-medium text-foreground">{agentName || agentType}</span>」吗？此操作不可撤销。
              </p>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">取消</Button>
              <Button variant="danger" onPress={handleDeleteConfirm} isPending={submitting}>{({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}删除</>)}</Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* ── Preview modal ── */}
      <Modal.Backdrop isOpen={previewOpen} onOpenChange={setPreviewOpen}>
        <Modal.Container size="lg">
          <Modal.Dialog className="max-w-5xl">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>Agent 解析预览</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4 max-h-[70vh] overflow-y-auto">
              {previewLoading ? (
                <>
                  <Skeleton className="h-20 rounded-xl" />
                  <Skeleton className="h-48 rounded-xl" />
                </>
              ) : previewError ? (
                <div className="rounded-xl border border-danger/20 bg-danger/5 px-3 py-3 text-sm text-danger">
                  {previewError}
                </div>
              ) : (
                <>
                  <div className="flex flex-wrap gap-2">
                    <Chip
                      size="sm"
                      variant="soft"
                      color={availableAgents.some((item) => item.agentType === agentType.trim()) ? "success" : "default"}
                    >
                      {availableAgents.some((item) => item.agentType === agentType.trim()) ? "在 available 中" : "不在 available 中"}
                    </Chip>
                    <Chip
                      size="sm"
                      variant="soft"
                      color={coordinatorAgents.some((item) => item.agentType === agentType.trim()) ? "success" : "default"}
                    >
                      {coordinatorAgents.some((item) => item.agentType === agentType.trim()) ? "可作为协调者" : "非协调者"}
                    </Chip>
                    <Chip
                      size="sm"
                      variant="soft"
                      color={standaloneAgents.some((item) => item.agentType === agentType.trim()) ? "success" : "default"}
                    >
                      {standaloneAgents.some((item) => item.agentType === agentType.trim()) ? "可独立调用" : "不可独立调用"}
                    </Chip>
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <p className="text-[10px] text-muted">executionMode</p>
                      <p className="mt-1 text-sm text-foreground">{resolvedProfile?.executionMode || "-"}</p>
                    </div>
                    <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <p className="text-[10px] text-muted">skillLoadMode</p>
                      <p className="mt-1 text-sm text-foreground">{resolvedProfile?.skillLoadMode || "-"}</p>
                    </div>
                    <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <p className="text-[10px] text-muted">resolvedSkills</p>
                      <p className="mt-1 text-sm text-foreground">{resolvedSkills.length}</p>
                    </div>
                    <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
                      <p className="text-[10px] text-muted">resolvedTools</p>
                      <p className="mt-1 text-sm text-foreground">{resolvedTools.length}</p>
                    </div>
                  </div>

                  <div className="grid gap-4 lg:grid-cols-2">
                    <div className="rounded-xl border border-border bg-surface/40 px-3 py-3">
                      <p className="text-xs font-medium text-foreground">Resolved Skills</p>
                      <div className="mt-2 space-y-2">
                        {resolvedSkills.length > 0 ? resolvedSkills.map((skill) => (
                          <div key={skill.name} className="rounded-lg border border-border/60 bg-background/50 px-3 py-2">
                            <p className="font-mono text-sm text-foreground">{skill.displayName || skill.name}</p>
                            <p className="mt-0.5 font-mono text-[11px] text-muted">{skill.name}</p>
                          </div>
                        )) : <p className="text-sm text-muted">无解析出的 Skill</p>}
                      </div>
                    </div>

                    <div className="rounded-xl border border-border bg-surface/40 px-3 py-3">
                      <p className="text-xs font-medium text-foreground">Resolved Tools</p>
                      <div className="mt-2 space-y-2">
                        {resolvedTools.length > 0 ? resolvedTools.map((tool) => (
                          <div key={getToolCatalogIdentifier(tool) || getToolCatalogTitle(tool)} className="rounded-lg border border-border/60 bg-background/50 px-3 py-2">
                            <div className="flex flex-wrap items-center gap-2">
                              <p className="font-mono text-sm text-foreground">{getToolCatalogTitle(tool)}</p>
                              {tool.actionType ? (
                                <Chip size="sm" variant="soft" color="default">
                                  {tool.actionType}
                                </Chip>
                              ) : null}
                            </div>
                            <p className="mt-0.5 font-mono text-[11px] text-muted">{getToolCatalogIdentifier(tool) || "-"}</p>
                          </div>
                        )) : <p className="text-sm text-muted">无解析出的 Tool</p>}
                      </div>
                    </div>
                  </div>

                  <div className="rounded-xl border border-border bg-surface/40 px-3 py-3">
                    <p className="text-xs font-medium text-foreground">Resolved Profile JSON</p>
                    <pre className="mt-2 max-h-72 overflow-y-auto rounded-lg bg-background/60 p-3 font-mono text-xs leading-6 text-muted">
                      {formatJson(resolvedProfile)}
                    </pre>
                  </div>
                </>
              )}
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">关闭</Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* ── Versions modal ── */}
      <Modal.Backdrop isOpen={versionsOpen} onOpenChange={setVersionsOpen}>
        <Modal.Container size="lg">
          <Modal.Dialog className="max-w-4xl">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>版本历史</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="space-y-4 max-h-[70vh] overflow-y-auto">
              {versionsLoading ? (
                <>
                  <Skeleton className="h-20 rounded-xl" />
                  <Skeleton className="h-20 rounded-xl" />
                </>
              ) : versionsError ? (
                <div className="rounded-xl border border-danger/20 bg-danger/5 px-3 py-3 text-sm text-danger">
                  {versionsError}
                </div>
              ) : versions.length === 0 ? (
                <div className="rounded-xl border border-dashed border-border bg-surface p-8 text-center text-sm text-muted">
                  暂无版本历史
                </div>
              ) : (
                <div className="space-y-3">
                  {versions.map((version) => (
                    <div key={version.id} className="rounded-xl border border-border bg-surface px-4 py-3">
                      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="text-sm font-semibold text-foreground">版本 {version.versionNumber}</p>
                            {version.executionMode ? (
                              <Chip size="sm" variant="soft" color="default">
                                {version.executionMode}
                              </Chip>
                            ) : null}
                            {version.skillLoadMode ? (
                              <Chip size="sm" variant="soft" color="default">
                                {version.skillLoadMode}
                              </Chip>
                            ) : null}
                            {version.isCoordinator ? (
                              <Chip size="sm" variant="soft" color="accent">
                                COORDINATOR
                              </Chip>
                            ) : null}
                            {version.standaloneEnabled ? (
                              <Chip size="sm" variant="soft" color="success">
                                STANDALONE
                              </Chip>
                            ) : null}
                          </div>
                          {version.changeSummary ? (
                            <p className="mt-1 text-sm text-foreground">{version.changeSummary}</p>
                          ) : null}
                          <p className="mt-1 text-xs text-muted">{version.createdAt ? new Date(version.createdAt).toLocaleString() : "-"}</p>
                          <div className="mt-2 grid gap-2 md:grid-cols-2">
                            <div className="rounded-lg border border-border/60 bg-background/50 px-3 py-2">
                              <p className="text-[10px] text-muted">defaultSkillNames</p>
                              <p className="mt-1 font-mono text-xs text-foreground">{version.defaultSkillNames?.join(", ") || "-"}</p>
                            </div>
                            <div className="rounded-lg border border-border/60 bg-background/50 px-3 py-2">
                              <p className="text-[10px] text-muted">allowedSkillNames</p>
                              <p className="mt-1 font-mono text-xs text-foreground">{version.allowedSkillNames?.join(", ") || "-"}</p>
                            </div>
                            <div className="rounded-lg border border-border/60 bg-background/50 px-3 py-2">
                              <p className="text-[10px] text-muted">subAgentTypes</p>
                              <p className="mt-1 font-mono text-xs text-foreground">{version.subAgentTypes?.join(", ") || "-"}</p>
                            </div>
                            <div className="rounded-lg border border-border/60 bg-background/50 px-3 py-2">
                              <p className="text-[10px] text-muted">llmProviderId</p>
                              <p className="mt-1 font-mono text-xs text-foreground">{version.llmProviderId || "-"}</p>
                            </div>
                          </div>
                        </div>
                        <div className="shrink-0">
                          <Button
                            variant="secondary"
                            size="sm"
                            isPending={rollbackingVersion === version.versionNumber}
                            onPress={() => void handleRollbackVersion(version.versionNumber)}
                            className="gap-1"
                          >
                            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <RotateCcw className="size-3.5" />}回滚到此版本</>)}
                          </Button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">关闭</Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}
