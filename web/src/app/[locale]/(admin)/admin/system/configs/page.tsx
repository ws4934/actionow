"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocale } from "next-intl";
import {
  Button,
  Chip,
  Input,
  Form,
  Label,
  ListBox,
  Modal,
  NumberField,
  SearchField,
  Select,
  Skeleton,
  Spinner,
  Switch,
  Tabs,
  TextArea,
  TextField,
  toast,
} from "@heroui/react";
import { Braces, Eye, EyeOff, Lock, Plus, RefreshCw, RotateCcw, Save, Settings } from "lucide-react";
import { adminService, getErrorFromException } from "@/lib/api";
import type { SystemConfigDTO, SystemConfigModuleDTO } from "@/lib/api/dto";
import { CodeEditor } from "@/components/admin/ai-model/code-editor";

// ── TAB CACHE KEY ───────────────────────────────────────────────────────────

const TAB_CACHE_KEY = "admin-system-configs-tab";

function formatJsonPreview(value: string): string {
  try {
    const parsed = JSON.parse(value);
    return JSON.stringify(parsed);
  } catch {
    return value;
  }
}

// ── Inline value editor cell ────────────────────────────────────────────────

function ConfigValueCell({
  config,
  editedValue,
  onCommit,
  onSave,
  onOpenJsonEditor,
  saving,
}: {
  config: SystemConfigDTO;
  editedValue: string;
  onCommit: (value: string) => void;
  onSave: (valueOverride?: string) => void;
  onOpenJsonEditor: (config: SystemConfigDTO) => void;
  saving: boolean;
}) {
  const isBoolean = config.valueType === "BOOLEAN";
  const isInteger = config.valueType === "INTEGER";
  const isJson = config.valueType === "JSON";
  const [showPassword, setShowPassword] = useState(false);

  // Local state for the input — avoids parent re-render on every keystroke
  const [localValue, setLocalValue] = useState(editedValue);
  // Sync from parent when editedValue changes externally (e.g. after save)
  useEffect(() => {
    setLocalValue(editedValue);
  }, [editedValue]);

  const isUnchanged = localValue === (config.configValue ?? "");
  const isMaskedNoChange = config.sensitive && localValue === "********";

  // Flush local value to parent
  const commitLocal = () => {
    if (localValue !== editedValue) {
      onCommit(localValue);
    }
  };

  if (isBoolean) {
    return (
      <div className="flex items-center gap-2">
        <Switch
          isSelected={editedValue === "true"}
          onChange={(v) => {
            const next = String(v);
            onCommit(next);
            onSave(next);
          }}
          size="sm"
          isDisabled={saving}
        >
          <Switch.Control>
            <Switch.Thumb />
          </Switch.Control>
        </Switch>
        <span className={`text-xs font-medium ${editedValue === "true" ? "text-green-600 dark:text-green-400" : "text-muted"}`}>
          {editedValue === "true" ? "开启" : "关闭"}
        </span>
      </div>
    );
  }

  if (isJson) {
    return (
      <button
        type="button"
        onClick={() => onOpenJsonEditor(config)}
        className="flex max-w-full items-center gap-2 rounded-md border border-border bg-background px-2 py-1.5 text-left transition-colors hover:border-accent/40"
      >
        <Braces className="size-3 shrink-0 text-accent" />
        <span className="line-clamp-1 flex-1 font-mono text-[11px] text-muted">
          {formatJsonPreview(editedValue)}
        </span>
        <span className="shrink-0 text-[11px] font-medium text-accent">编辑</span>
      </button>
    );
  }

  return (
    <div className="flex items-center gap-1.5">
      <div className="relative min-w-0 flex-1">
        <input
          type={
            config.sensitive && !showPassword
              ? "password"
              : isInteger
              ? "number"
              : "text"
          }
          aria-label={config.configKey}
          value={localValue}
          onChange={(e) => setLocalValue(e.target.value)}
          onBlur={commitLocal}
          onFocus={() => {
            if (config.sensitive && localValue === "********") {
              setLocalValue("");
            }
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !isUnchanged && !isMaskedNoChange) {
              commitLocal();
              onSave(localValue);
            }
          }}
          placeholder={config.sensitive ? "输入值…" : undefined}
          className={`input w-full font-mono text-xs ${config.sensitive ? "pr-7" : ""}`}
        />
        {config.sensitive && (
          <button
            type="button"
            onClick={() => setShowPassword((v) => !v)}
            className="absolute right-2 top-1/2 -translate-y-1/2 text-muted hover:text-foreground"
          >
            {showPassword ? <EyeOff className="size-3" /> : <Eye className="size-3" />}
          </button>
        )}
      </div>
      <Button
        variant="primary"
        size="sm"
        isPending={saving}
        isDisabled={isUnchanged || isMaskedNoChange}
        onPress={() => {
          commitLocal();
          onSave(localValue);
        }}
        isIconOnly
        aria-label="保存"
        className="shrink-0"
      >
        {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Save className="size-3" />}</>)}
      </Button>
    </div>
  );
}


// ── Page ─────────────────────────────────────────────────────────────────────

export default function SystemConfigsPage() {
  const locale = useLocale();
  const [activeTab, setActiveTab] = useState("all");

  // Restore cached tab after hydration to avoid SSR mismatch
  useEffect(() => {
    try {
      const cached = localStorage.getItem(TAB_CACHE_KEY);
      if (cached) setActiveTab(cached);
    } catch { /* ignore */ }
  }, []);

  const handleTabChange = useCallback((key: string) => {
    setActiveTab(key);
    try { localStorage.setItem(TAB_CACHE_KEY, key); } catch { /* ignore */ }
  }, []);
  const [keyword, setKeyword] = useState("");

  // ── Grouped configs state
  const [groupedModules, setGroupedModules] = useState<SystemConfigModuleDTO[]>([]);
  const [loading, setLoading] = useState(true);

  // ── Inline edit state (all configs)
  const [editedValues, setEditedValues] = useState<Record<string, string>>({});
  const [savingId, setSavingId] = useState<string | null>(null);

  // ── JSON editor modal state
  const [jsonEditorConfig, setJsonEditorConfig] = useState<SystemConfigDTO | null>(null);
  const [jsonEditorValue, setJsonEditorValue] = useState("");
  const [jsonEditorError, setJsonEditorError] = useState<string | null>(null);

  // ── Create config modal state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [newConfig, setNewConfig] = useState({
    configKey: "",
    configValue: "",
    displayName: "",
    description: "",
    valueType: "STRING",
    configType: "SYSTEM",
    scope: "GLOBAL",
    module: "",
    groupName: "",
    defaultValue: "",
    sortOrder: 0,
    sensitive: false,
  });

  const resetCreateForm = () => {
    setNewConfig({ configKey: "", configValue: "", displayName: "", description: "", valueType: "STRING", configType: "SYSTEM", scope: "GLOBAL", module: "", groupName: "", defaultValue: "", sortOrder: 0, sensitive: false });
  };

  // ── Agent rebuild / cache
  const [rebuildingAgents, setRebuildingAgents] = useState(false);
  const [reloadModalOpen, setReloadModalOpen] = useState(false);
  const [refreshingCache, setRefreshingCache] = useState(false);

  // ── Fetch grouped configs
  const fetchGroupedConfigs = useCallback(async () => {
    try {
      setLoading(true);
      const modules = await adminService.getGroupedConfigs();
      setGroupedModules(modules);
    } catch (err) {
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setLoading(false);
    }
  }, [locale]);

  useEffect(() => {
    void fetchGroupedConfigs();
  }, [fetchGroupedConfigs]);

  // ── Initialize editedValues from grouped configs (only on first load)
  const editedValuesInitRef = useRef(false);
  useEffect(() => {
    if (groupedModules.length === 0) return;
    if (editedValuesInitRef.current) return;
    editedValuesInitRef.current = true;
    const init: Record<string, string> = {};
    for (const mod of groupedModules) {
      for (const group of mod.groups) {
        for (const cfg of group.configs) {
          init[cfg.id] = cfg.configValue ?? "";
        }
      }
    }
    setEditedValues(init);
  }, [groupedModules]);

  // ── Dynamic tabs
  const moduleTabs = useMemo(() => {
    const tabs: Array<{ key: string; label: string }> = [
      { key: "all", label: "全部" },
    ];
    for (const mod of groupedModules) {
      tabs.push({
        key: mod.module,
        label: mod.moduleDisplayName || mod.module,
      });
    }
    return tabs;
  }, [groupedModules]);

  // ── Filtered modules
  const filteredModules = useMemo(() => {
    let modules = groupedModules;

    if (activeTab !== "all") {
      modules = modules.filter((m) => m.module === activeTab);
    }

    const kw = keyword.trim().toLowerCase();
    if (!kw) return modules;

    return modules
      .map((mod) => ({
        ...mod,
        groups: mod.groups
          .map((group) => ({
            ...group,
            configs: group.configs.filter(
              (c) =>
                c.configKey?.toLowerCase().includes(kw) ||
                c.displayName?.toLowerCase().includes(kw) ||
                c.description?.toLowerCase().includes(kw)
            ),
          }))
          .filter((group) => group.configs.length > 0),
      }))
      .filter((mod) => mod.groups.length > 0);
  }, [groupedModules, activeTab, keyword]);


  const totalConfigCount = useMemo(() =>
    filteredModules.reduce((sum, mod) => sum + mod.groups.reduce((gs, g) => gs + g.configs.length, 0), 0),
    [filteredModules]
  );

  // ── Generic save handler
  const handleSaveConfig = async (config: SystemConfigDTO, valueOverride?: string) => {
    const newValue = valueOverride ?? editedValues[config.id] ?? "";
    if (config.sensitive && newValue === "********") return;

    const prevValue = config.configValue ?? "";
    setEditedValues((prev) => ({ ...prev, [config.id]: newValue }));

    try {
      setSavingId(config.id);
      await adminService.updateSystemConfig(config.id, {
        configValue: newValue,
        configKey: config.configKey,
        description: config.description ?? undefined,
        sensitive: config.sensitive,
        enabled: config.enabled,
      });
      const maskedValue = config.sensitive ? "********" : newValue;
      setGroupedModules((prev) =>
        prev.map((mod) => ({
          ...mod,
          groups: mod.groups.map((g) => ({
            ...g,
            configs: g.configs.map((c) =>
              c.id === config.id ? { ...c, configValue: maskedValue } : c
            ),
          })),
        }))
      );
      setEditedValues((prev) => ({ ...prev, [config.id]: maskedValue }));
      toast.success("保存成功");
    } catch (err) {
      toast.danger(getErrorFromException(err, locale));
      setEditedValues((prev) => ({ ...prev, [config.id]: prevValue }));
    } finally {
      setSavingId(null);
    }
  };

  // ── JSON editor handlers
  const openJsonEditor = (config: SystemConfigDTO) => {
    const raw = editedValues[config.id] ?? config.configValue ?? "";
    let pretty = raw;
    try {
      pretty = JSON.stringify(JSON.parse(raw), null, 2);
    } catch { /* keep raw */ }
    setJsonEditorConfig(config);
    setJsonEditorValue(pretty);
    setJsonEditorError(null);
  };

  const closeJsonEditor = () => {
    setJsonEditorConfig(null);
    setJsonEditorValue("");
    setJsonEditorError(null);
  };

  const handleSaveJsonEditor = async () => {
    if (!jsonEditorConfig) return;
    let compact: string;
    try {
      compact = JSON.stringify(JSON.parse(jsonEditorValue));
    } catch {
      setJsonEditorError("JSON 格式错误");
      return;
    }
    setJsonEditorError(null);
    const target = jsonEditorConfig;
    closeJsonEditor();
    await handleSaveConfig(target, compact);
  };

  // ── Agent rebuild
  const handleReloadAgentConfigs = async () => {
    setReloadModalOpen(false);
    try {
      setRebuildingAgents(true);
      await adminService.reloadAgentConfigs();
      toast.success("Agent 已全量重建");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setRebuildingAgents(false);
    }
  };

  // ── Create config
  const handleCreateConfig = async () => {
    if (!newConfig.configKey.trim()) {
      toast.danger("请填写 Config Key");
      return;
    }
    try {
      setCreating(true);
      await adminService.createSystemConfig({
        configKey: newConfig.configKey.trim(),
        configValue: newConfig.configValue,
        displayName: newConfig.displayName.trim() || undefined,
        description: newConfig.description.trim() || undefined,
        valueType: newConfig.valueType,
        configType: newConfig.configType,
        scope: newConfig.scope,
        module: newConfig.module.trim() || undefined,
        groupName: newConfig.groupName.trim() || undefined,
        defaultValue: newConfig.defaultValue.trim() || undefined,
        sortOrder: newConfig.sortOrder || undefined,
        sensitive: newConfig.sensitive,
        enabled: true,
      });
      toast.success("配置项创建成功");
      setCreateModalOpen(false);
      resetCreateForm();
      await fetchGroupedConfigs();
      editedValuesInitRef.current = false;
    } catch (err) {
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setCreating(false);
    }
  };

  // ── Refresh cache
  const handleRefreshCache = async () => {
    try {
      setRefreshingCache(true);
      await adminService.refreshConfigCache();
      toast.success("配置缓存已刷新");
    } catch (error) {
      toast.danger(getErrorFromException(error, locale));
    } finally {
      setRefreshingCache(false);
    }
  };

  return (
    <div className="flex h-full flex-col">
      {/* ── Toolbar ── */}
      <div className="shrink-0 space-y-3 pb-3">
        {/* Row 1: Tabs + actions */}
        <div className="flex items-center justify-between gap-3">
          <Tabs
            selectedKey={activeTab}
            onSelectionChange={(k) => handleTabChange(String(k))}
          >
            <Tabs.ListContainer>
              <Tabs.List aria-label="配置模块">
                {moduleTabs.map((tab) => (
                  <Tabs.Tab key={tab.key} id={tab.key} className="whitespace-nowrap">
                    {tab.label}
                    <Tabs.Indicator />
                  </Tabs.Tab>
                ))}
              </Tabs.List>
            </Tabs.ListContainer>
          </Tabs>
          <div className="flex items-center gap-2">
            <Button
              variant="tertiary"
              size="sm"
              isPending={refreshingCache}
              onPress={handleRefreshCache}
              className="gap-1"
            >
              {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <RefreshCw className="size-3.5" />}刷新缓存</>)}
            </Button>
            <Button
              variant="tertiary"
              size="sm"
              isPending={rebuildingAgents}
              onPress={() => setReloadModalOpen(true)}
              className="gap-1"
            >
              {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <RotateCcw className="size-3.5" />}重建 Agent</>)}
            </Button>
          </div>
        </div>
        {/* Row 2: Search + count */}
        <div className="flex items-center gap-2">
          <SearchField
            aria-label="搜索"
            value={keyword}
            onChange={setKeyword}
            variant="secondary"
          >
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input
                className="w-64"
                placeholder="按 Key / 名称 / 描述搜索"
              />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <span className="text-xs text-muted">共 {totalConfigCount} 项</span>
          <div className="flex-1" />
          <Button
            variant="primary"
            size="sm"
            onPress={() => setCreateModalOpen(true)}
            className="gap-1"
          >
            <Plus className="size-3.5" />
            新增配置
          </Button>
          <Button
            variant="ghost"
            size="sm"
            isIconOnly
            onPress={fetchGroupedConfigs}
            aria-label="刷新"
          >
            <RefreshCw className="size-3.5" />
          </Button>
        </div>
      </div>

      {/* ── Config list ── */}
      <div className="min-h-0 flex-1 overflow-y-auto">
        {loading ? (
          <div className="space-y-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="space-y-2">
                <Skeleton className="h-5 w-48 rounded" />
                {Array.from({ length: 3 }).map((_, j) => (
                  <Skeleton key={j} className="h-12 rounded-lg" />
                ))}
              </div>
            ))}
          </div>
        ) : filteredModules.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 py-20 text-center">
            <Settings className="size-10 text-muted/30" />
            <p className="text-sm text-muted">
              {keyword.trim() ? "未找到匹配的配置项" : "暂无系统配置"}
            </p>
          </div>
        ) : (
          <div className="space-y-5 pb-4">
            {filteredModules.map((mod) =>
              mod.groups.map((group) => {
                const label = `${mod.moduleDisplayName || mod.module} / ${group.groupName}`;
                const sorted = [...group.configs].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
                return (
                  <section key={`${mod.module}-${group.groupName}`}>
                    {/* Group header */}
                    <div className="mb-2 flex items-center gap-2 px-1">
                      <span className="text-xs font-semibold text-foreground">{label}</span>
                      <span className="text-[11px] text-muted">({sorted.length})</span>
                    </div>
                    {/* Card with table-like rows */}
                    <div className="overflow-hidden rounded-xl border border-border bg-surface">
                      {/* Column header */}
                      <div className="grid grid-cols-[200px_180px_1fr_72px] items-center gap-3 border-b border-border bg-surface-secondary/50 px-4 py-2 text-[11px] font-medium text-muted">
                        <span>配置项</span>
                        <span>Key</span>
                        <span>值</span>
                        <span className="text-center">类型</span>
                      </div>
                      {/* Config rows */}
                      {sorted.map((cfg, idx) => {
                        const editedValue = editedValues[cfg.id] ?? cfg.configValue ?? "";
                        return (
                          <div
                            key={cfg.id}
                            className={`grid grid-cols-[200px_180px_1fr_72px] items-center gap-3 px-4 py-2.5 ${
                              idx < sorted.length - 1 ? "border-b border-border/50" : ""
                            }`}
                          >
                            {/* Name column */}
                            <div className="min-w-0">
                              <div className="flex items-center gap-1.5">
                                <span className="truncate text-sm font-medium text-foreground">
                                  {cfg.displayName || cfg.configKey?.split(".").pop()}
                                </span>
                                {cfg.sensitive && <Lock className="size-3 shrink-0 text-red-400" />}
                              </div>
                              {cfg.description && (
                                <p className="mt-0.5 line-clamp-1 text-[11px] text-muted">
                                  {cfg.description}
                                </p>
                              )}
                            </div>
                            {/* Key column */}
                            <div className="min-w-0">
                              <code className="line-clamp-1 rounded bg-surface-secondary px-1.5 py-0.5 font-mono text-[11px] text-muted">
                                {cfg.configKey}
                              </code>
                            </div>
                            {/* Value column */}
                            <div className="min-w-0">
                              <ConfigValueCell
                                config={cfg}
                                editedValue={editedValue}
                                onCommit={(v) =>
                                  setEditedValues((prev) => ({ ...prev, [cfg.id]: v }))
                                }
                                onSave={(v) => handleSaveConfig(cfg, v)}
                                onOpenJsonEditor={openJsonEditor}
                                saving={savingId === cfg.id}
                              />
                            </div>
                            {/* Type column */}
                            <div className="text-center">
                              <Chip size="sm" variant="soft" color={cfg.valueType === "BOOLEAN" ? "accent" : cfg.valueType === "JSON" ? "warning" : "default"}>
                                {cfg.valueType}
                              </Chip>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </section>
                );
              })
            )}
          </div>
        )}
      </div>

      {/* ── JSON editor modal ── */}
      <Modal.Backdrop
        isOpen={jsonEditorConfig !== null}
        onOpenChange={(open) => !open && closeJsonEditor()}
      >
        <Modal.Container size="md">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>
                {jsonEditorConfig?.displayName || jsonEditorConfig?.configKey}
              </Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              {jsonEditorConfig?.description && (
                <p className="mb-2 text-xs text-muted">
                  {jsonEditorConfig.description}
                </p>
              )}
              <p className="mb-2 font-mono text-[10px] text-muted/60">
                {jsonEditorConfig?.configKey}
              </p>
              <div className="h-64 overflow-hidden rounded-lg border border-border">
                <CodeEditor
                  value={jsonEditorValue}
                  onChange={(v) => {
                    setJsonEditorValue(v);
                    if (jsonEditorError) setJsonEditorError(null);
                  }}
                  lang="json"
                  placeholder='{"key": "value"}'
                />
              </div>
              {jsonEditorError && (
                <p className="mt-2 text-xs text-danger">{jsonEditorError}</p>
              )}
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">
                取消
              </Button>
              <Button
                variant="primary"
                onPress={handleSaveJsonEditor}
                isPending={
                  jsonEditorConfig ? savingId === jsonEditorConfig.id : false
                }
                className="gap-1"
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Save className="size-3.5" />}保存</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* ── Create config modal ── */}
      <Modal.Backdrop
        isOpen={createModalOpen}
        onOpenChange={(open) => { if (!open) { setCreateModalOpen(false); resetCreateForm(); } }}
      >
        <Modal.Container size="lg">
          <Modal.Dialog className="overflow-visible">
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>新增配置项</Modal.Heading>
            </Modal.Header>
            <Form onSubmit={(e) => { e.preventDefault(); handleCreateConfig(); }}>
              <Modal.Body className="space-y-4 overflow-visible">
                <TextField className="w-full" isRequired variant="secondary" value={newConfig.configKey} onChange={(v) => setNewConfig((p) => ({ ...p, configKey: v }))}>
                  <Label>Config Key</Label>
                  <Input placeholder="例: runtime.agent.max-iterations" className="font-mono text-sm" />
                </TextField>

                <div className="grid grid-cols-2 gap-4">
                  <TextField className="w-full" variant="secondary" value={newConfig.displayName} onChange={(v) => setNewConfig((p) => ({ ...p, displayName: v }))}>
                    <Label>显示名称</Label>
                    <Input placeholder="可选" />
                  </TextField>
                  <TextField className="w-full" variant="secondary" value={newConfig.description} onChange={(v) => setNewConfig((p) => ({ ...p, description: v }))}>
                    <Label>描述</Label>
                    <Input placeholder="可选" />
                  </TextField>
                </div>

                <div className="grid grid-cols-3 gap-4">
                  <Select
                    className="w-full"
                    variant="secondary"
                    value={newConfig.valueType}
                    onChange={(v) => setNewConfig((p) => ({ ...p, valueType: String(v) }))}
                  >
                    <Label>值类型</Label>
                    <Select.Trigger>
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        <ListBox.Item id="STRING" textValue="STRING">STRING<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="BOOLEAN" textValue="BOOLEAN">BOOLEAN<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="INTEGER" textValue="INTEGER">INTEGER<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="LONG" textValue="LONG">LONG<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="FLOAT" textValue="FLOAT">FLOAT<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="JSON" textValue="JSON">JSON<ListBox.ItemIndicator /></ListBox.Item>
                      </ListBox>
                    </Select.Popover>
                  </Select>
                  <Select
                    className="w-full"
                    variant="secondary"
                    value={newConfig.configType}
                    onChange={(v) => setNewConfig((p) => ({ ...p, configType: String(v) }))}
                  >
                    <Label>配置类型</Label>
                    <Select.Trigger>
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        <ListBox.Item id="SYSTEM" textValue="SYSTEM">SYSTEM<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="FEATURE" textValue="FEATURE">FEATURE<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="LIMIT" textValue="LIMIT">LIMIT<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="NOTIFICATION" textValue="NOTIFICATION">NOTIFICATION<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="AI" textValue="AI">AI<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="STORAGE" textValue="STORAGE">STORAGE<ListBox.ItemIndicator /></ListBox.Item>
                      </ListBox>
                    </Select.Popover>
                  </Select>
                  <Select
                    className="w-full"
                    variant="secondary"
                    value={newConfig.scope}
                    onChange={(v) => setNewConfig((p) => ({ ...p, scope: String(v) }))}
                  >
                    <Label>作用域</Label>
                    <Select.Trigger>
                      <Select.Value />
                      <Select.Indicator />
                    </Select.Trigger>
                    <Select.Popover>
                      <ListBox>
                        <ListBox.Item id="GLOBAL" textValue="GLOBAL">GLOBAL<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="WORKSPACE" textValue="WORKSPACE">WORKSPACE<ListBox.ItemIndicator /></ListBox.Item>
                        <ListBox.Item id="USER" textValue="USER">USER<ListBox.ItemIndicator /></ListBox.Item>
                      </ListBox>
                    </Select.Popover>
                  </Select>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <TextField className="w-full" variant="secondary" value={newConfig.configValue} onChange={(v) => setNewConfig((p) => ({ ...p, configValue: v }))}>
                    <Label>值</Label>
                    {newConfig.valueType === "JSON" ? (
                      <TextArea placeholder='{"key": "value"}' rows={3} className="font-mono text-sm" />
                    ) : (
                      <Input
                        placeholder={newConfig.valueType === "BOOLEAN" ? "true / false" : "配置值"}
                        className="font-mono text-sm"
                      />
                    )}
                  </TextField>
                  <TextField className="w-full" variant="secondary" value={newConfig.defaultValue} onChange={(v) => setNewConfig((p) => ({ ...p, defaultValue: v }))}>
                    <Label>默认值</Label>
                    <Input placeholder="可选" className="font-mono text-sm" />
                  </TextField>
                </div>

                <div className="grid grid-cols-4 gap-4">
                  <TextField className="w-full" variant="secondary" value={newConfig.module} onChange={(v) => setNewConfig((p) => ({ ...p, module: v }))}>
                    <Label>模块</Label>
                    <Input placeholder="自动推断" />
                  </TextField>
                  <TextField className="w-full" variant="secondary" value={newConfig.groupName} onChange={(v) => setNewConfig((p) => ({ ...p, groupName: v }))}>
                    <Label>分组</Label>
                    <Input placeholder="例: limits" />
                  </TextField>
                  <NumberField className="w-full" variant="secondary" value={newConfig.sortOrder} onChange={(v) => setNewConfig((p) => ({ ...p, sortOrder: v }))}>
                    <Label>排序</Label>
                    <Input placeholder="0" />
                  </NumberField>
                  <div className="flex flex-col justify-end pb-0.5">
                    <Switch
                      isSelected={newConfig.sensitive}
                      onChange={(v) => setNewConfig((p) => ({ ...p, sensitive: v }))}
                      size="sm"
                    >
                      <Switch.Control>
                        <Switch.Thumb />
                      </Switch.Control>
                      <span className="text-sm">敏感字段</span>
                    </Switch>
                  </div>
                </div>
              </Modal.Body>
              <Modal.Footer>
                <Button variant="secondary" slot="close">
                  取消
                </Button>
                <Button
                  type="submit"
                  isPending={creating}
                  isDisabled={!newConfig.configKey.trim()}
                  className="gap-1"
                >
                  {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <Plus className="size-3.5" />}创建</>)}
                </Button>
              </Modal.Footer>
            </Form>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>

      {/* ── Reload confirmation modal ── */}
      <Modal.Backdrop
        isOpen={reloadModalOpen}
        onOpenChange={(open) => !open && setReloadModalOpen(false)}
      >
        <Modal.Container size="sm">
          <Modal.Dialog>
            <Modal.CloseTrigger />
            <Modal.Header>
              <Modal.Heading>确认全量重建 Agent</Modal.Heading>
            </Modal.Header>
            <Modal.Body>
              <p className="text-sm text-muted">
                此操作将调用{" "}
                <code className="rounded bg-surface-secondary px-1 font-mono text-xs">
                  saaAgentFactory.forceRebuild()
                </code>
                ，清除所有 LLM 缓存并重建 SupervisorAgent。
              </p>
              <p className="mt-2 text-sm text-muted">
                正在处理中的请求不受影响，但重建期间新请求可能短暂延迟。
              </p>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">
                取消
              </Button>
              <Button
                variant="danger"
                isPending={rebuildingAgents}
                onPress={handleReloadAgentConfigs}
              >
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}确认重建</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}
