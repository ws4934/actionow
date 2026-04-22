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
  Spinner,
  Switch,
  TextArea,
  TextField,
  toast,
} from "@heroui/react";
import {
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  FileCode2,
  Save,
  Settings2,
  TestTube2,
  Trash2,
  Zap,
} from "lucide-react";
import type { Key as ReactKey } from "react";
import { aiAdminService, getErrorFromException } from "@/lib/api";
import type { GroovyTemplateType, SaveGroovyTemplateRequestDTO } from "@/lib/api/dto";
import { ResizablePanels } from "@/components/ui/resizable-panels";
import { CodeEditor } from "@/components/admin/ai-model/code-editor";

// ─── Constants ────────────────────────────────────────────────────────────────

const TYPE_OPTIONS: Array<{ value: GroovyTemplateType; label: string }> = [
  { value: "REQUEST_BUILDER", label: "REQUEST_BUILDER" },
  { value: "RESPONSE_MAPPER", label: "RESPONSE_MAPPER" },
  { value: "CUSTOM_LOGIC", label: "CUSTOM_LOGIC" },
];

type RightTab = "script" | "docs";

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

export default function GroovyTemplateEditPage() {
  const locale = useLocale();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const isCreate = id === "new";

  const [loading, setLoading] = useState(!isCreate);
  const [submitting, setSubmitting] = useState(false);
  const [testing, setTesting] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [rightTab, setRightTab] = useState<RightTab>("script");

  // ── Fields
  const [name, setName] = useState("");
  const [templateType, setTemplateType] = useState<GroovyTemplateType>("REQUEST_BUILDER");
  const [generationType, setGenerationType] = useState("");
  const [scriptVersion, setScriptVersion] = useState("");
  const [description, setDescription] = useState("");
  const [scriptContent, setScriptContent] = useState("");
  const [documentation, setDocumentation] = useState("");
  const [exampleInputText, setExampleInputText] = useState("");
  const [exampleOutputText, setExampleOutputText] = useState("");
  const [isSystem, setIsSystem] = useState(false);
  const [enabled, setEnabled] = useState(true);

  // ── Test
  const [testInputs, setTestInputs] = useState('{\n  "prompt": "hello world"\n}');
  const [testConfig, setTestConfig] = useState("{}");
  const [testResult, setTestResult] = useState("");
  const [testSuccess, setTestSuccess] = useState<boolean | null>(null);

  const pageTitle = useMemo(() => (isCreate ? "新建 Groovy 模板" : "编辑 Groovy 模板"), [isCreate]);

  // ─── Load ─────────────────────────────────────────────────────────────────

  useEffect(() => {
    if (isCreate) return;
    const load = async () => {
      try {
        setLoading(true);
        const d = await aiAdminService.getGroovyTemplateById(id);
        setName(d.name || "");
        setTemplateType((d.templateType as GroovyTemplateType) || "REQUEST_BUILDER");
        setGenerationType(d.generationType || "");
        setScriptVersion(d.scriptVersion || "");
        setDescription(d.description || "");
        setScriptContent(d.scriptContent || "");
        setDocumentation(d.documentation || "");
        setExampleInputText(d.exampleInput ? JSON.stringify(d.exampleInput, null, 2) : "");
        setExampleOutputText(d.exampleOutput ? JSON.stringify(d.exampleOutput, null, 2) : "");
        setIsSystem(d.isSystem ?? false);
        setEnabled(d.enabled ?? true);
      } catch (err) {
        toast.danger(getErrorFromException(err, locale));
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [id, isCreate, locale]);

  // ─── Save ─────────────────────────────────────────────────────────────────

  const handleSave = async () => {
    if (!name.trim() || !scriptContent.trim()) {
      toast.danger("模板名称和脚本内容不能为空");
      return;
    }

    let parsedExampleInput: unknown = undefined;
    let parsedExampleOutput: unknown = undefined;
    try {
      if (exampleInputText.trim()) parsedExampleInput = JSON.parse(exampleInputText);
      if (exampleOutputText.trim()) parsedExampleOutput = JSON.parse(exampleOutputText);
    } catch {
      toast.danger("示例输入 / 输出 JSON 格式错误");
      return;
    }

    const payload: SaveGroovyTemplateRequestDTO = {
      name: name.trim(),
      templateType,
      generationType: generationType.trim() || undefined,
      scriptVersion: scriptVersion.trim() || undefined,
      scriptContent,
      description: description.trim() || undefined,
      documentation: documentation.trim() || undefined,
      exampleInput: parsedExampleInput,
      exampleOutput: parsedExampleOutput,
      isSystem,
      enabled,
    };

    try {
      setSubmitting(true);
      if (isCreate) {
        const created = await aiAdminService.createGroovyTemplate(payload);
        toast.success("创建成功");
        router.replace(`/${locale}/admin/models/groovy/${created.id}`);
      } else {
        await aiAdminService.updateGroovyTemplate(id, payload);
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
      await aiAdminService.deleteGroovyTemplate(id);
      toast.success("删除成功");
      router.replace(`/${locale}/admin/models/groovy`);
    } catch (err) {
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setSubmitting(false);
    }
  };

  const handleTest = async () => {
    try {
      setTesting(true);
      setTestResult("");
      setTestSuccess(null);
      const result = await aiAdminService.testGroovyScript({
        scriptContent,
        templateType,
        inputs: JSON.parse(testInputs) as Record<string, unknown>,
        config: JSON.parse(testConfig) as Record<string, unknown>,
      });
      setTestResult(JSON.stringify(result, null, 2));
      setTestSuccess(true);
      toast.success("测试完成");
    } catch (err) {
      setTestSuccess(false);
      toast.danger(getErrorFromException(err, locale));
    } finally {
      setTesting(false);
    }
  };

  const onTypeChange = (key: ReactKey | ReactKey[] | null) => {
    if (key && !Array.isArray(key)) setTemplateType(String(key) as GroovyTemplateType);
  };

  // ─── Loading skeleton ──────────────────────────────────────────────────────

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
      <div className="flex-1 overflow-y-auto p-4 space-y-4" style={{ scrollbarWidth: "thin" }}>

        {/* ── 基本信息 ── */}
        <section>
          <SectionLabel icon={Settings2} title="基本信息" iconCls="bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400" />
          <div className="space-y-2.5">
            <TextField isRequired>
              <Label className="text-xs">模板名称</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="My Request Builder" />
            </TextField>

            <div className="grid grid-cols-2 gap-2">
              <Select value={templateType} onChange={onTypeChange}>
                <Label className="text-xs">模板类型</Label>
                <Select.Trigger>
                  <Select.Value>{() => <span className="font-mono text-xs">{templateType}</span>}</Select.Value>
                  <Select.Indicator />
                </Select.Trigger>
                <Select.Popover>
                  <ListBox>
                    {TYPE_OPTIONS.map((opt) => (
                      <ListBox.Item key={opt.value} id={opt.value} textValue={opt.label}>
                        {opt.label}
                        <ListBox.ItemIndicator />
                      </ListBox.Item>
                    ))}
                  </ListBox>
                </Select.Popover>
              </Select>

              <TextField>
                <Label className="text-xs">版本号</Label>
                <Input value={scriptVersion} onChange={(e) => setScriptVersion(e.target.value)} placeholder="1.0.0" className="font-mono text-xs" />
              </TextField>
            </div>

            <TextField>
              <Label className="text-xs">generationType</Label>
              <Input value={generationType} onChange={(e) => setGenerationType(e.target.value)} placeholder="ALL / IMAGE / VIDEO / …" className="font-mono text-xs" />
            </TextField>

            <TextField>
              <Label className="text-xs">描述</Label>
              <TextArea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="模板用途说明…" />
            </TextField>

            <div className="grid grid-cols-2 gap-2">
              <div className="flex items-center justify-between rounded-lg border border-border bg-surface/40 px-3 py-2">
                <div>
                  <p className="text-xs font-medium">启用</p>
                  <p className="text-[10px] text-muted">对外提供服务</p>
                </div>
                <Switch isSelected={enabled} onChange={setEnabled} size="sm" />
              </div>

              <div className="flex items-center justify-between rounded-lg border border-border bg-surface/40 px-3 py-2">
                <div>
                  <p className="text-xs font-medium">系统</p>
                  <p className="text-[10px] text-muted">内置不可删除</p>
                </div>
                <Switch isSelected={isSystem} onChange={setIsSystem} size="sm" />
              </div>
            </div>
          </div>
        </section>

        <Divider />

        {/* ── 示例 ── */}
        <section>
          <SectionLabel icon={FileCode2} title="输入 / 输出示例" iconCls="bg-teal-50 text-teal-600 dark:bg-teal-950/40 dark:text-teal-400" />
          <div className="space-y-2">
            <div>
              <p className="mb-1 text-[10px] text-muted">exampleInput (JSON)</p>
              <div className="h-36 overflow-hidden rounded-lg border border-border">
                <CodeEditor
                  value={exampleInputText}
                  onChange={setExampleInputText}
                  lang="json"
                  placeholder='{"inputs": {"prompt": "..."}, "config": {}}'
                />
              </div>
            </div>
            <div>
              <p className="mb-1 text-[10px] text-muted">exampleOutput (JSON)</p>
              <div className="h-36 overflow-hidden rounded-lg border border-border">
                <CodeEditor
                  value={exampleOutputText}
                  onChange={setExampleOutputText}
                  lang="json"
                  placeholder='{"url": "https://...", "status": "success"}'
                />
              </div>
            </div>
          </div>
        </section>

        <Divider />

        {/* ── 脚本测试 ── */}
        <section>
          <SectionLabel icon={TestTube2} title="脚本测试" iconCls="bg-purple-50 text-purple-600 dark:bg-purple-950/40 dark:text-purple-400" />
          <div className="space-y-2.5">
            <div>
              <p className="mb-1 text-[10px] text-muted">测试输入 (JSON)</p>
              <div className="h-40 overflow-hidden rounded-lg border border-border">
                <CodeEditor
                  value={testInputs}
                  onChange={setTestInputs}
                  lang="json"
                  placeholder='{"prompt": "hello world"}'
                />
              </div>
            </div>
            <div>
              <p className="mb-1 text-[10px] text-muted">测试配置 (JSON)</p>
              <div className="h-28 overflow-hidden rounded-lg border border-border">
                <CodeEditor
                  value={testConfig}
                  onChange={setTestConfig}
                  lang="json"
                  placeholder="{}"
                />
              </div>
            </div>
            <Button variant="secondary" size="sm" isPending={testing} onPress={handleTest} className="gap-1">
              {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <TestTube2 className="size-3.5" />}运行测试</>)}
            </Button>
            {testResult && (
              <div className={`rounded-lg border p-3 ${testSuccess ? "border-green-200 bg-green-50/50 dark:border-green-900/40 dark:bg-green-950/20" : "border-red-200 bg-red-50/50 dark:border-red-900/40 dark:bg-red-950/20"}`}>
                <div className="mb-1 flex items-center gap-1.5">
                  <CheckCircle2 className={`size-3 ${testSuccess ? "text-green-600" : "text-red-500"}`} />
                  <p className="text-[10px] font-medium text-muted">测试结果</p>
                </div>
                <pre className="max-h-48 overflow-y-auto whitespace-pre-wrap font-mono text-xs">{testResult}</pre>
              </div>
            )}
          </div>
        </section>

        <div className="h-2" />
      </div>
    </div>
  );

  const rightPanel = (
    <div className="flex h-full flex-col overflow-hidden rounded-xl bg-[#1e1e1e] dark:bg-[#161616]">
      {/* Tab bar */}
      <div className="flex shrink-0 items-end gap-0 border-b border-[#3a3a3a] bg-[#252526] px-2 pt-1.5">
        {(
          [
            { id: "script" as RightTab, label: "脚本", Icon: Zap },
            { id: "docs" as RightTab, label: "文档", Icon: BookOpen },
          ]
        ).map(({ id: tabId, label, Icon }) => {
          const isActive = rightTab === tabId;
          return (
            <button
              key={tabId}
              type="button"
              onClick={() => setRightTab(tabId)}
              className={`flex items-center gap-1.5 rounded-t-md border-x border-t px-3 py-1.5 text-[11px] font-medium transition-all ${
                isActive
                  ? "border-[#3a3a3a] bg-[#1e1e1e] text-white"
                  : "border-transparent text-[#858585] hover:text-[#ccc]"
              }`}
            >
              <Icon className="size-3" />
              {label}
            </button>
          );
        })}
      </div>

      {/* Meta bar */}
      <div className="flex shrink-0 items-center gap-3 border-b border-[#2d2d2d] bg-[#1e1e1e] px-4 py-1">
        {rightTab === "script" ? (
          <>
            <span className="rounded bg-[#2d2d2d] px-1.5 py-0.5 font-mono text-[10px] text-[#9cdcfe]">Groovy</span>
            <span className="text-[11px] text-[#6e6e6e]">脚本内容 · 绑定变量: inputs, config, authConfig</span>
          </>
        ) : (
          <>
            <span className="rounded bg-[#2d2d2d] px-1.5 py-0.5 font-mono text-[10px] text-[#9cdcfe]">Markdown</span>
            <span className="text-[11px] text-[#6e6e6e]">模板使用文档</span>
          </>
        )}
      </div>

      {/* Editor */}
      <div className="flex-1 min-h-0">
        {rightTab === "script" ? (
          <CodeEditor
            value={scriptContent}
            onChange={setScriptContent}
            lang="groovy"
            placeholder="// Groovy script\ndef body = [:]\nbody.prompt = inputs.prompt\nreturn body"
          />
        ) : (
          <CodeEditor
            value={documentation}
            onChange={setDocumentation}
            lang="markdown"
            placeholder="## 模板说明&#10;&#10;### 输入参数&#10;- `param`: 说明&#10;&#10;### 功能&#10;1. ..."
          />
        )}
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
          onPress={() => router.push(`/${locale}/admin/models/groovy`)}
          className="shrink-0 gap-1"
        >
          <ArrowLeft className="size-3.5" />
          返回
        </Button>

        <div className="mx-1 h-4 w-px bg-border shrink-0" />

        <div className="flex min-w-0 flex-1 items-center gap-2">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-amber-500/10">
            <Zap className="size-3.5 text-amber-500" />
          </div>
          <h1 className="truncate text-sm font-semibold text-foreground">{name || pageTitle}</h1>
          <span className="shrink-0 rounded-md bg-surface-secondary px-1.5 py-0.5 font-mono text-[10px] text-muted">
            {templateType}
          </span>
          {scriptVersion && (
            <span className="shrink-0 rounded-md bg-surface-secondary px-1.5 py-0.5 font-mono text-[10px] text-muted">
              v{scriptVersion}
            </span>
          )}
          {isSystem && (
            <span className="shrink-0 rounded-md bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700 dark:bg-amber-950/40 dark:text-amber-300">
              系统
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
          <Button variant="secondary" size="sm" isPending={testing} onPress={handleTest} className="gap-1">
            {({isPending}) => (<>{isPending ? <Spinner color="current" size="sm" /> : <TestTube2 className="size-3.5" />}测试</>)}
          </Button>
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
          defaultLeftWidth={38}
          minLeftWidth={25}
          maxLeftWidth={60}
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
                确认删除「<span className="font-medium text-foreground">{name}</span>」吗？此操作不可撤销。
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
