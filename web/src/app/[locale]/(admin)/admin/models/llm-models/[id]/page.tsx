"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import {
  Button,
  Input,
  Label,
  ListBox,
  Modal,
  Select,
  Skeleton,
  Switch,
  TextArea,
  TextField,
  Spinner,
  toast,
} from "@heroui/react";
import {
  ArrowLeft,
  Bot,
  Globe,
  RotateCcw,
  Save,
  Settings2,
  Timer,
  Trash2,
  Zap,
} from "lucide-react";
import type { Key as ReactKey } from "react";
import { aiAdminService, adminService, getErrorFromException } from "@/lib/api";
import type { SaveLlmProviderRequestDTO, SystemConfigDTO } from "@/lib/api/dto";
import { ResizablePanels } from "@/components/ui/resizable-panels";
import { CodeEditor } from "@/components/admin/ai-model/code-editor";

// ─── Constants ────────────────────────────────────────────────────────────────

const LLM_VENDOR_OPTIONS = [
  "OPENAI", "GOOGLE", "ANTHROPIC", "VOLCENGINE", "ZHIPU",
  "MOONSHOT", "BAIDU", "ALIBABA", "DASHSCOPE", "DEEPSEEK",
];

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

export default function LlmModelEditPage() {
  const locale = useLocale();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const isCreate = id === "new";

  const [loading, setLoading] = useState(!isCreate);
  const [submitting, setSubmitting] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [refreshingCache, setRefreshingCache] = useState(false);

  // ── Fields
  const [provider, setProvider] = useState("OPENAI");
  const [modelId, setModelId] = useState("");
  const [modelName, setModelName] = useState("");
  const [description, setDescription] = useState("");
  const [temperature, setTemperature] = useState("0.7");
  const [maxOutputTokens, setMaxOutputTokens] = useState("8192");
  const [topP, setTopP] = useState("");
  const [topK, setTopK] = useState("");
  const [contextWindow, setContextWindow] = useState("");
  const [maxInputTokens, setMaxInputTokens] = useState("");
  const [apiEndpoint, setApiEndpoint] = useState("");
  const [apiEndpointRef, setApiEndpointRef] = useState("");
  const [completionsPath, setCompletionsPath] = useState("");
  const [apiKeyRef, setApiKeyRef] = useState("");
  const [priority, setPriority] = useState("1");
  const [enabled, setEnabled] = useState(true);
  const [extraConfigText, setExtraConfigText] = useState("");

  // AI provider config refs for dropdowns
  const [apiKeyOptions, setApiKeyOptions] = useState<SystemConfigDTO[]>([]);
  const [baseUrlOptions, setBaseUrlOptions] = useState<SystemConfigDTO[]>([]);

  const pageTitle = useMemo(() => (isCreate ? "新建 LLM 模型" : "编辑 LLM 模型"), [isCreate]);

  // Load AI provider configs for ref dropdowns
  useEffect(() => {
    adminService.getAiProviderConfigs().then((configs) => {
      setApiKeyOptions(configs.filter((c) => c.configKey?.endsWith(".api_key")));
      setBaseUrlOptions(configs.filter((c) => c.configKey?.endsWith(".base_url")));
    }).catch(() => { /* non-critical */ });
  }, []);

  // ─── Load ─────────────────────────────────────────────────────────────────

  useEffect(() => {
    if (isCreate) return;
    const load = async () => {
      try {
        setLoading(true);
        const d = await aiAdminService.getLlmProviderById(id);
        setProvider(d.provider || "OPENAI");
        setModelId(d.modelId || "");
        setModelName(d.modelName || "");
        setDescription(d.description || "");
        setTemperature(d.temperature === undefined ? "" : String(d.temperature));
        setMaxOutputTokens(d.maxOutputTokens === undefined ? "" : String(d.maxOutputTokens));
        setTopP(d.topP === undefined ? "" : String(d.topP));
        setTopK(d.topK === undefined ? "" : String(d.topK));
        setContextWindow(d.contextWindow === undefined ? "" : String(d.contextWindow));
        setMaxInputTokens(d.maxInputTokens === undefined ? "" : String(d.maxInputTokens));
        setApiEndpoint(d.apiEndpoint || "");
        setApiEndpointRef(d.apiEndpointRef || "");
        setCompletionsPath(d.completionsPath || "");
        setApiKeyRef(d.apiKeyRef || "");
        setPriority(d.priority === undefined ? "" : String(d.priority));
        setEnabled(d.enabled ?? true);
        setExtraConfigText(d.extraConfig ? JSON.stringify(d.extraConfig, null, 2) : "");
      } catch (err) {
        toast.danger(getErrorFromException(err, locale));
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [id, isCreate, locale]);

  // ─── Helpers ──────────────────────────────────────────────────────────────

  const parseNum = (v: string): number | undefined => {
    const t = v.trim();
    if (!t) return undefined;
    const n = Number(t);
    return Number.isFinite(n) ? n : undefined;
  };

  // ─── Save ─────────────────────────────────────────────────────────────────

  const handleSave = async () => {
    if (!modelId.trim() || !modelName.trim()) {
      toast.danger("模型 ID 和模型名称不能为空");
      return;
    }
    let extraConfig: Record<string, unknown> | undefined;
    if (extraConfigText.trim()) {
      try {
        extraConfig = JSON.parse(extraConfigText) as Record<string, unknown>;
      } catch {
        toast.danger("extraConfig JSON 格式错误");
        return;
      }
    }
    const payload: SaveLlmProviderRequestDTO = {
      provider,
      modelId: modelId.trim(),
      modelName: modelName.trim(),
      description: description.trim() || undefined,
      temperature: parseNum(temperature),
      maxOutputTokens: parseNum(maxOutputTokens),
      topP: parseNum(topP),
      topK: parseNum(topK),
      contextWindow: parseNum(contextWindow),
      maxInputTokens: parseNum(maxInputTokens),
      apiEndpoint: apiEndpoint.trim() || undefined,
      apiEndpointRef: apiEndpointRef.trim() || undefined,
      completionsPath: completionsPath.trim() || undefined,
      apiKeyRef: apiKeyRef.trim() || undefined,
      priority: parseNum(priority),
      enabled,
      extraConfig,
    };
    try {
      setSubmitting(true);
      if (isCreate) {
        const created = await aiAdminService.createLlmProvider(payload);
        toast.success("创建成功");
        router.replace(`/${locale}/admin/models/llm-models/${created.id}`);
      } else {
        await aiAdminService.updateLlmProvider(id, payload);
        toast.success("保存成功");
      }
    } catch (err) {
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (isCreate) return;
    setDeleteModalOpen(false);
    try {
      setSubmitting(true);
      await aiAdminService.deleteLlmProvider(id);
      toast.success("删除成功");
      router.replace(`/${locale}/admin/models/llm-models`);
    } catch (err) {
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRefreshCache = async () => {
    if (isCreate) return;
    try {
      setRefreshingCache(true);
      await aiAdminService.refreshLlmProviderCacheById(id);
      toast.success("缓存已刷新");
    } catch (err) {
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setRefreshingCache(false);
    }
  };

  const onVendorChange = (key: ReactKey | ReactKey[] | null) => {
    if (key && !Array.isArray(key)) setProvider(String(key));
  };
  const onApiKeyRefChange = (key: ReactKey | ReactKey[] | null) => {
    if (!Array.isArray(key)) setApiKeyRef(key ? String(key) : "");
  };
  const onApiEndpointRefChange = (key: ReactKey | ReactKey[] | null) => {
    if (!Array.isArray(key)) setApiEndpointRef(key ? String(key) : "");
  };

  // ─── Loading skeleton ──────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex h-full flex-col">
        <Skeleton className="h-14 w-full shrink-0 rounded-none" />
        <div className="flex flex-1 min-h-0 gap-3 p-3">
          <Skeleton className="h-full w-[42%] rounded-xl" />
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
            <Select value={provider} onChange={onVendorChange}>
              <Label className="text-xs">厂商</Label>
              <Select.Trigger>
                <Select.Value>{() => <span>{provider}</span>}</Select.Value>
                <Select.Indicator />
              </Select.Trigger>
              <Select.Popover>
                <ListBox>
                  {LLM_VENDOR_OPTIONS.map((v) => (
                    <ListBox.Item key={v} id={v} textValue={v}>
                      {v}
                      <ListBox.ItemIndicator />
                    </ListBox.Item>
                  ))}
                </ListBox>
              </Select.Popover>
            </Select>

            <div className="grid grid-cols-2 gap-2">
              <TextField isRequired>
                <Label className="text-xs">模型 ID</Label>
                <Input value={modelId} onChange={(e) => setModelId(e.target.value)} placeholder="gpt-4o" className="font-mono text-xs" />
              </TextField>
              <TextField isRequired>
                <Label className="text-xs">模型名称</Label>
                <Input value={modelName} onChange={(e) => setModelName(e.target.value)} placeholder="GPT-4o" />
              </TextField>
            </div>

            <TextField>
              <Label className="text-xs">描述</Label>
              <TextArea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="模型功能说明…" />
            </TextField>
          </div>
        </section>

        <Divider />

        {/* ── 接口配置 ── */}
        <section>
          <SectionLabel icon={Globe} title="接口配置" iconCls="bg-violet-50 text-violet-600 dark:bg-violet-950/40 dark:text-violet-400" />
          <div className="space-y-2.5">
            <p className="text-[10px] text-muted">引用（Ref）优先于直接值。设置引用后，直接值仅作为回退。</p>

            <Select value={apiKeyRef || "__none__"} onChange={onApiKeyRefChange}>
              <Label className="text-xs">API Key 引用</Label>
              <Select.Trigger>
                <Select.Value>{() => <span className="font-mono text-xs">{apiKeyRef || "— 不使用引用 —"}</span>}</Select.Value>
                <Select.Indicator />
              </Select.Trigger>
              <Select.Popover>
                <ListBox>
                  <ListBox.Item key="__none__" id="__none__" textValue="不使用引用">
                    — 不使用引用 —
                    <ListBox.ItemIndicator />
                  </ListBox.Item>
                  {apiKeyOptions.map((c) => (
                    <ListBox.Item key={c.configKey} id={c.configKey} textValue={c.configKey ?? ""}>
                      <span className="font-mono text-xs">{c.configKey}</span>
                      {c.description && <span className="ml-2 text-[10px] text-muted">{c.description}</span>}
                      <ListBox.ItemIndicator />
                    </ListBox.Item>
                  ))}
                </ListBox>
              </Select.Popover>
            </Select>

            <Select value={apiEndpointRef || "__none__"} onChange={onApiEndpointRefChange}>
              <Label className="text-xs">API 端点引用</Label>
              <Select.Trigger>
                <Select.Value>{() => <span className="font-mono text-xs">{apiEndpointRef || "— 不使用引用 —"}</span>}</Select.Value>
                <Select.Indicator />
              </Select.Trigger>
              <Select.Popover>
                <ListBox>
                  <ListBox.Item key="__none__" id="__none__" textValue="不使用引用">
                    — 不使用引用 —
                    <ListBox.ItemIndicator />
                  </ListBox.Item>
                  {baseUrlOptions.map((c) => (
                    <ListBox.Item key={c.configKey} id={c.configKey} textValue={c.configKey ?? ""}>
                      <span className="font-mono text-xs">{c.configKey}</span>
                      {c.description && <span className="ml-2 text-[10px] text-muted">{c.description}</span>}
                      <ListBox.ItemIndicator />
                    </ListBox.Item>
                  ))}
                </ListBox>
              </Select.Popover>
            </Select>

            <TextField>
              <Label className="text-xs">API Endpoint（直接值，回退用）</Label>
              <Input value={apiEndpoint} onChange={(e) => setApiEndpoint(e.target.value)} placeholder="https://api.openai.com" />
            </TextField>
            <div className="grid grid-cols-2 gap-2">
              <TextField>
                <Label className="text-xs">Completions Path</Label>
                <Input value={completionsPath} onChange={(e) => setCompletionsPath(e.target.value)} placeholder="/v1/chat/completions" className="font-mono text-xs" />
              </TextField>
            </div>
          </div>
        </section>

        <Divider />

        {/* ── 模型参数 ── */}
        <section>
          <SectionLabel icon={Zap} title="模型参数" iconCls="bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400" />
          <div className="grid grid-cols-2 gap-2">
            {[
              { label: "temperature", val: temperature, set: setTemperature, placeholder: "0.7" },
              { label: "maxOutputTokens", val: maxOutputTokens, set: setMaxOutputTokens, placeholder: "8192" },
              { label: "topP", val: topP, set: setTopP, placeholder: "—" },
              { label: "topK", val: topK, set: setTopK, placeholder: "—" },
              { label: "contextWindow", val: contextWindow, set: setContextWindow, placeholder: "—" },
              { label: "maxInputTokens", val: maxInputTokens, set: setMaxInputTokens, placeholder: "—" },
            ].map(({ label, val, set, placeholder }) => (
              <TextField key={label}>
                <Label className="text-xs">{label}</Label>
                <Input value={val} onChange={(e) => set(e.target.value)} placeholder={placeholder} className="font-mono text-xs" />
              </TextField>
            ))}
          </div>
        </section>

        <Divider />

        {/* ── 运行配置 ── */}
        <section>
          <SectionLabel icon={Timer} title="运行配置" iconCls="bg-green-50 text-green-600 dark:bg-green-950/40 dark:text-green-400" />
          <div className="space-y-2.5">
            <TextField>
              <Label className="text-xs">优先级</Label>
              <Input value={priority} onChange={(e) => setPriority(e.target.value)} placeholder="1" className="font-mono text-xs" />
            </TextField>

            <div className="flex items-center justify-between rounded-lg border border-border bg-surface/40 px-3 py-2">
              <div>
                <p className="text-xs font-medium">启用状态</p>
                <p className="text-[10px] text-muted">在生产环境提供服务</p>
              </div>
              <Switch isSelected={enabled} onChange={() => setEnabled((v) => !v)} />
            </div>
          </div>
        </section>

        <div className="h-2" />
      </div>
    </div>
  );

  const rightPanel = (
    <div className="flex h-full flex-col overflow-hidden rounded-xl bg-[#1e1e1e] dark:bg-[#161616]">
      <div className="flex shrink-0 items-center gap-3 border-b border-[#2d2d2d] bg-[#252526] px-4 py-2">
        <span className="rounded bg-[#2d2d2d] px-1.5 py-0.5 font-mono text-[10px] text-[#9cdcfe]">JSON</span>
        <span className="text-[11px] text-[#6e6e6e]">extraConfig — 额外模型配置（JSON 对象）</span>
      </div>
      <div className="flex-1 min-h-0">
        <CodeEditor
          value={extraConfigText}
          onChange={setExtraConfigText}
          lang="json"
          placeholder='{\n  "key": "value"\n}'
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
          onPress={() => router.push(`/${locale}/admin/models/llm-models`)}
          className="shrink-0 gap-1"
        >
          <ArrowLeft className="size-3.5" />
          返回
        </Button>

        <div className="mx-1 h-4 w-px bg-border shrink-0" />

        <div className="flex min-w-0 flex-1 items-center gap-2">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-accent/10">
            <Bot className="size-3.5 text-accent" />
          </div>
          <h1 className="truncate text-sm font-semibold text-foreground">{modelName || pageTitle}</h1>
          {provider && (
            <span className="shrink-0 rounded-md bg-blue-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
              {provider}
            </span>
          )}
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <div className="flex items-center gap-2">
            <Switch isSelected={enabled} onChange={() => setEnabled((v) => !v)} size="sm">
              <span className={`text-xs font-medium ${enabled ? "text-green-600 dark:text-green-400" : "text-muted"}`}>
                {enabled ? "启用" : "禁用"}
              </span>
            </Switch>
          </div>
          <div className="h-4 w-px bg-border" />
          {!isCreate && (
            <Button variant="tertiary" size="sm" isPending={refreshingCache} onPress={handleRefreshCache} className="gap-1">
              {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <RotateCcw className="size-3.5" />}刷新缓存</>)}
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

      {/* ── Resizable split panels ── */}
      <div className="flex-1 min-h-0">
        <ResizablePanels
          defaultLeftWidth={42}
          minLeftWidth={30}
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
                确认删除「<span className="font-medium text-foreground">{modelName}</span>」吗？此操作不可撤销。
              </p>
            </Modal.Body>
            <Modal.Footer>
              <Button variant="secondary" slot="close">取消</Button>
              <Button variant="danger" onPress={handleDeleteConfirm} isPending={submitting}>
                {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : null}删除</>)}
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </div>
  );
}
