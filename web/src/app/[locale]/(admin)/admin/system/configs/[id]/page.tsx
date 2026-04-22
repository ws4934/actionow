"use client";

import { useEffect, useMemo, useState } from "react";
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
import { ArrowLeft, Database, Eye, EyeOff, Lock, Save, Settings2, Trash2 } from "lucide-react";
import type { Key as ReactKey } from "react";
import { adminService, getErrorFromException } from "@/lib/api";
import type { SaveSystemConfigRequestDTO } from "@/lib/api/dto";
import { ResizablePanels } from "@/components/ui/resizable-panels";
import { CodeEditor } from "@/components/admin/ai-model/code-editor";

// ─── Constants ───────────────────────────────────────────────────────────────

const CONFIG_TYPE_OPTIONS = ["SYSTEM", "FEATURE", "LIMIT", "AI_PROVIDER"];
const SCOPE_OPTIONS = ["GLOBAL", "WORKSPACE", "USER"];
const MODULE_OPTIONS = ["user", "agent", "task", "ai", "gateway", "project", "billing", "mq", "canvas", "system"];

function getChipColor(configType?: string): "accent" | "warning" | "success" | "danger" {
  switch (configType) {
    case "FEATURE": return "success";
    case "LIMIT": return "warning";
    case "AI_PROVIDER": return "accent";
    case "SYSTEM": return "danger";
    default: return "accent";
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

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function SystemConfigEditPage() {
  const locale = useLocale();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const isCreate = id === "new";

  const [loading, setLoading] = useState(!isCreate);
  const [submitting, setSubmitting] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);

  // ─── Basic fields ───────────────────────────────────────────────────────
  const [configKey, setConfigKey] = useState("");
  const [configValue, setConfigValue] = useState("");
  const [configType, setConfigType] = useState("SYSTEM");
  const [description, setDescription] = useState("");
  const [valueType, setValueType] = useState("");
  const [enabled, setEnabled] = useState(true);
  const [sensitive, setSensitive] = useState(false);
  const [revealed, setRevealed] = useState(isCreate);

  // ─── New fields ─────────────────────────────────────────────────────────
  const [displayName, setDisplayName] = useState("");
  const [module, setModule] = useState("system");
  const [groupName, setGroupName] = useState("");
  const [scope, setScope] = useState("GLOBAL");
  const [scopeId, setScopeId] = useState("");
  const [defaultValue, setDefaultValue] = useState("");
  const [sortOrder, setSortOrder] = useState(0);
  const [validation, setValidation] = useState("");

  const pageTitle = useMemo(() => (isCreate ? "新建系统配置" : "编辑系统配置"), [isCreate]);

  // ─── Load ────────────────────────────────────────────────────────────────

  useEffect(() => {
    if (isCreate) return;
    const load = async () => {
      try {
        setLoading(true);
        const detail = await adminService.getSystemConfigById(id);
        setConfigKey(detail.configKey || "");
        setConfigValue(detail.configValue || "");
        setConfigType(detail.configType || "SYSTEM");
        setDescription(detail.description || "");
        setValueType(detail.valueType || "");
        setEnabled(detail.enabled ?? true);
        setSensitive(detail.sensitive ?? false);
        setRevealed(false);
        // New fields
        setDisplayName(detail.displayName || "");
        setModule(detail.module || "system");
        setGroupName(detail.groupName || "");
        setScope(detail.scope || "GLOBAL");
        setScopeId(detail.scopeId || "");
        setDefaultValue(detail.defaultValue || "");
        setSortOrder(detail.sortOrder ?? 0);
        setValidation(detail.validation ? JSON.stringify(detail.validation, null, 2) : "");
      } catch (error) {
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [id, isCreate, locale]);

  // ─── Save ────────────────────────────────────────────────────────────────

  const handleSave = async () => {
    if (isCreate) {
      if (!configKey.trim() || !configValue.trim()) {
        toast.danger("配置键名和配置值不能为空");
        return;
      }
      if ((scope === "WORKSPACE" || scope === "USER") && !scopeId.trim()) {
        toast.danger(`${scope} 作用域必须填写 scopeId`);
        return;
      }

      let parsedValidation: unknown = undefined;
      if (validation.trim()) {
        try {
          parsedValidation = JSON.parse(validation);
        } catch {
          toast.danger("验证规则必须是合法的 JSON");
          return;
        }
      }

      const payload: SaveSystemConfigRequestDTO = {
        configKey: configKey.trim(),
        configValue,
        configType,
        scope,
        scopeId: scope !== "GLOBAL" ? scopeId.trim() : undefined,
        description: description.trim() || undefined,
        defaultValue: defaultValue.trim() || undefined,
        valueType: valueType.trim() || undefined,
        module: module || undefined,
        groupName: groupName.trim() || undefined,
        displayName: displayName.trim() || undefined,
        validation: parsedValidation,
        sortOrder: sortOrder || undefined,
        enabled,
        sensitive,
      };
      try {
        setSubmitting(true);
        const created = await adminService.createSystemConfig(payload);
        toast.success("创建成功");
        router.replace(`/${locale}/admin/system/configs/${created.id}`);
      } catch (error) {
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setSubmitting(false);
      }
      return;
    }

    if (!configValue.trim()) {
      toast.danger("配置值不能为空");
      return;
    }

    let parsedValidation: unknown = undefined;
    if (validation.trim()) {
      try {
        parsedValidation = JSON.parse(validation);
      } catch {
        toast.danger("验证规则必须是合法的 JSON");
        return;
      }
    }

    try {
      setSubmitting(true);
      await adminService.updateSystemConfig(id, {
        configValue,
        configType,
        description: description.trim() || undefined,
        displayName: displayName.trim() || undefined,
        module: module || undefined,
        groupName: groupName.trim() || undefined,
        scope,
        scopeId: scope !== "GLOBAL" ? scopeId.trim() : undefined,
        defaultValue: defaultValue.trim() || undefined,
        valueType: valueType.trim() || undefined,
        validation: parsedValidation,
        sortOrder: sortOrder || undefined,
        sensitive,
        enabled,
      });
      toast.success("保存成功");
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
      await adminService.deleteSystemConfig(id);
      toast.success("删除成功");
      router.replace(`/${locale}/admin/system/configs`);
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setSubmitting(false);
    }
  };

  const onTypeChange = (key: ReactKey | ReactKey[] | null) => {
    if (key && !Array.isArray(key)) setConfigType(String(key));
  };
  const onScopeChange = (key: ReactKey | ReactKey[] | null) => {
    if (key && !Array.isArray(key)) setScope(String(key));
  };
  const onModuleChange = (key: ReactKey | ReactKey[] | null) => {
    if (key && !Array.isArray(key)) setModule(String(key));
  };

  // ─── Skeleton ─────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex h-full flex-col">
        <Skeleton className="h-14 w-full shrink-0 rounded-none" />
        <div className="flex flex-1 min-h-0 gap-3 p-3">
          <Skeleton className="h-full w-[38%] rounded-xl" />
          <Skeleton className="h-full flex-1 rounded-xl" />
        </div>
      </div>
    );
  }

  // ─── Panels ───────────────────────────────────────────────────────────────

  const leftPanel = (
    <div className="flex h-full flex-col overflow-hidden rounded-xl border border-border bg-background">
      <div className="flex-1 overflow-y-auto p-4 space-y-5" style={{ scrollbarWidth: "thin" }}>

        {/* ── 基本信息 ── */}
        <section>
          <SectionLabel icon={Settings2} title="基本信息" iconCls="bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400" />
          <div className="space-y-2.5">
            <TextField>
              <Label className="text-xs">展示名称</Label>
              <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="人类可读的配置名（如：最大并发执行数）" className="text-xs" />
            </TextField>

            <TextField isRequired isDisabled={!isCreate}>
              <Label className="text-xs">配置键名</Label>
              <Input value={configKey} onChange={(e) => setConfigKey(e.target.value)} placeholder="如 runtime.agent.max_concurrent_executions" className="font-mono text-xs" />
            </TextField>

            <div className="grid grid-cols-2 gap-2">
              <Select value={configType} onChange={onTypeChange}>
                <Label className="text-xs">配置类型</Label>
                <Select.Trigger>
                  <Select.Value>{() => <span>{configType}</span>}</Select.Value>
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {CONFIG_TYPE_OPTIONS.map((v) => (
                      <ListBox.Item key={v} id={v} textValue={v}>
                        {v}
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>

              <TextField>
                <Label className="text-xs">值类型</Label>
                <Input value={valueType} onChange={(e) => setValueType(e.target.value)} placeholder="STRING" className="font-mono text-xs" />
              </TextField>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <Select value={module} onChange={onModuleChange}>
                <Label className="text-xs">所属模块</Label>
                <Select.Trigger>
                  <Select.Value>{() => <span>{module}</span>}</Select.Value>
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {MODULE_OPTIONS.map((v) => (
                      <ListBox.Item key={v} id={v} textValue={v}>
                        {v}
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>

              <TextField>
                <Label className="text-xs">分组名</Label>
                <Input value={groupName} onChange={(e) => setGroupName(e.target.value)} placeholder="如 execution" className="font-mono text-xs" />
              </TextField>
            </div>

            <div className="grid grid-cols-2 gap-2">
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

              {scope !== "GLOBAL" ? (
                <TextField isRequired>
                  <Label className="text-xs">作用域 ID</Label>
                  <Input value={scopeId} onChange={(e) => setScopeId(e.target.value)} placeholder="工作空间/用户 ID" className="font-mono text-xs" />
                </TextField>
              ) : (
                <TextField>
                  <Label className="text-xs">排序序号</Label>
                  <Input type="number" value={String(sortOrder)} onChange={(e) => setSortOrder(Number(e.target.value) || 0)} className="font-mono text-xs" />
                </TextField>
              )}
            </div>

            {scope === "GLOBAL" ? null : (
              <TextField>
                <Label className="text-xs">排序序号</Label>
                <Input type="number" value={String(sortOrder)} onChange={(e) => setSortOrder(Number(e.target.value) || 0)} className="font-mono text-xs" />
              </TextField>
            )}

            <TextField>
              <Label className="text-xs">默认值</Label>
              <Input value={defaultValue} onChange={(e) => setDefaultValue(e.target.value)} placeholder="配置默认值" className="font-mono text-xs" />
            </TextField>
          </div>
        </section>

        <Divider />

        {/* ── 描述与标志 ── */}
        <section>
          <SectionLabel icon={Database} title="描述与标志" iconCls="bg-violet-50 text-violet-600 dark:bg-violet-950/40 dark:text-violet-400" />
          <div className="space-y-2.5">
            <TextField>
              <Label className="text-xs">配置描述</Label>
              <TextArea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="说明该配置项的用途…" />
            </TextField>
            <div className="flex items-center justify-between rounded-lg border border-border bg-surface/40 px-3 py-2">
              <div>
                <p className="text-xs font-medium">启用</p>
                <p className="text-[10px] text-muted">禁用后该配置项不生效</p>
              </div>
              <Switch isSelected={enabled} onChange={setEnabled} size="sm" />
            </div>
            <div className="flex items-center justify-between rounded-lg border border-border bg-surface/40 px-3 py-2">
              <div>
                <p className="flex items-center gap-1 text-xs font-medium">
                  <Lock className="size-3 text-red-500" />
                  敏感配置
                </p>
                <p className="text-[10px] text-muted">列表中隐藏值，编辑时需手动展开</p>
              </div>
              <Switch isSelected={sensitive} onChange={setSensitive} size="sm" />
            </div>
          </div>
        </section>

        <Divider />

        {/* ── 验证规则 ── */}
        <section>
          <SectionLabel icon={Settings2} title="验证规则" iconCls="bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400" />
          <TextField>
            <Label className="text-xs">Validation (JSON Schema)</Label>
            <TextArea
              rows={4}
              value={validation}
              onChange={(e) => setValidation(e.target.value)}
              placeholder='{"min": 0, "max": 1000}'
              className="font-mono text-xs"
            />
          </TextField>
        </section>

        <div className="h-2" />
      </div>
    </div>
  );

  const rightPanel = (
    <div className="flex h-full flex-col overflow-hidden rounded-xl bg-[#1e1e1e] dark:bg-[#161616]">
      <div className="flex shrink-0 items-center gap-3 border-b border-[#2d2d2d] bg-[#252526] px-4 py-2">
        <span className="rounded bg-[#2d2d2d] px-1.5 py-0.5 font-mono text-[10px] text-[#9cdcfe]">Value</span>
        <span className="flex-1 text-[11px] text-[#6e6e6e]">配置值</span>
        {sensitive ? (
          <button
            type="button"
            onClick={() => setRevealed((v) => !v)}
            className="flex items-center gap-1 rounded px-2 py-1 text-[11px] text-[#6e6e6e] transition hover:bg-[#2d2d2d] hover:text-[#cccccc]"
          >
            {revealed ? <EyeOff className="size-3" /> : <Eye className="size-3" />}
            {revealed ? "隐藏" : "显示"}
          </button>
        ) : null}
      </div>
      {sensitive && !revealed ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3">
          <Lock className="size-8 text-[#6e6e6e]" />
          <p className="text-sm text-[#6e6e6e]">敏感配置已隐藏</p>
          <button
            type="button"
            onClick={() => setRevealed(true)}
            className="rounded-md bg-[#2d2d2d] px-4 py-1.5 text-xs text-[#cccccc] transition hover:bg-[#3e3e3e]"
          >
            点击显示
          </button>
        </div>
      ) : (
        <div className="flex-1 min-h-0">
          <CodeEditor
            value={configValue}
            onChange={setConfigValue}
            lang="json"
            placeholder="输入配置值..."
          />
        </div>
      )}
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
          onPress={() => router.push(`/${locale}/admin/system/configs`)}
          className="shrink-0 gap-1"
        >
          <ArrowLeft className="size-3.5" />
          返回
        </Button>

        <div className="mx-1 h-4 w-px shrink-0 bg-border" />

        <div className="flex min-w-0 flex-1 items-center gap-2">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-accent/10">
            <Database className="size-3.5 text-accent" />
          </div>
          <h1 className="truncate text-sm font-semibold text-foreground">
            {displayName || configKey || pageTitle}
          </h1>
          {displayName && configKey && (
            <span className="truncate font-mono text-[11px] text-muted">{configKey}</span>
          )}
          {configType ? (
            <Chip size="sm" variant="soft" color={getChipColor(configType)}>
              {configType}
            </Chip>
          ) : null}
          {scope && scope !== "GLOBAL" ? (
            <Chip size="sm" variant="soft" color="warning">
              {scope}
            </Chip>
          ) : null}
          {valueType ? (
            <span className="shrink-0 rounded bg-surface-secondary px-1.5 py-0.5 font-mono text-[10px] text-muted">{valueType}</span>
          ) : null}
          {sensitive ? (
            <span className="flex shrink-0 items-center gap-0.5 rounded bg-red-100 px-1.5 py-0.5 text-[10px] font-semibold text-red-600 dark:bg-red-950/40 dark:text-red-400">
              <Lock className="size-2.5" />
              敏感
            </span>
          ) : null}
        </div>

        <div className="flex shrink-0 items-center gap-2">
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
          defaultLeftWidth={36}
          minLeftWidth={26}
          maxLeftWidth={55}
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
                确认删除配置{" "}
                <span className="font-mono font-semibold text-foreground">{configKey}</span>{" "}
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
