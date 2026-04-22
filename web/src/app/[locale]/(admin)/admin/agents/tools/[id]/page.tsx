"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import { Button, Chip, Skeleton, toast } from "@heroui/react";
import { ArrowLeft, Braces, FileJson2, Settings2, ShieldCheck, Tags, Zap } from "lucide-react";
import { adminService, getErrorFromException } from "@/lib/api";
import type { AgentToolCatalogDTO, AgentToolCatalogParamDTO } from "@/lib/api/dto";
import { ResizablePanels } from "@/components/ui/resizable-panels";
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
  getToolCatalogPurpose,
  getToolCatalogQuotaText,
  getToolCatalogSkillNames,
  getToolCatalogSourceType,
  getToolCatalogSummary,
  getToolCatalogTags,
  getToolCatalogTitle,
  getToolCatalogUsageText,
  stringifyToolCatalogValue,
} from "@/lib/utils/tool-catalog";

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
        <FileJson2 className="size-3.5 text-[#9cdcfe]" />
        <span className="text-[11px] text-[#6e6e6e]">{title}</span>
      </div>
      <pre className="max-h-[320px] overflow-auto p-4 font-mono text-xs leading-6 text-[#d4d4d4]">
        {value || "{}"}
      </pre>
    </div>
  );
}

function ParamCard({ param, index }: { param: AgentToolCatalogParamDTO; index: number }) {
  const enumValues = Array.isArray(param.enumValues) ? param.enumValues : [];

  return (
    <div className="rounded-lg border border-border bg-surface/40 px-3 py-3">
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
        <p className="mt-1.5 text-xs leading-5 text-muted">{param.description}</p>
      ) : null}
      {(param.defaultValue !== undefined && param.defaultValue !== null) || (param.example !== undefined && param.example !== null) ? (
        <div className="mt-2 grid grid-cols-2 gap-2">
          {param.defaultValue !== undefined && param.defaultValue !== null ? (
            <InfoBlock label="defaultValue" value={String(param.defaultValue)} mono />
          ) : null}
          {param.example !== undefined && param.example !== null ? (
            <InfoBlock label="example" value={String(param.example)} mono />
          ) : null}
        </div>
      ) : null}
      {enumValues.length > 0 ? (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {enumValues.map((value, enumIndex) => (
            <Chip key={`${param.name}-${enumIndex}`} size="sm" variant="soft" color="default">
              {String(value)}
            </Chip>
          ))}
        </div>
      ) : null}
    </div>
  );
}

export default function AgentToolDetailPage() {
  const locale = useLocale();
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const isCreate = id === "new";

  const [loading, setLoading] = useState(!isCreate);
  const [tool, setTool] = useState<AgentToolCatalogDTO | null>(null);

  useEffect(() => {
    if (isCreate) return;
    const load = async () => {
      try {
        setLoading(true);
        const detail = await adminService.getToolCatalogByToolId(id);
        setTool(detail);
      } catch (error) {
        toast.danger(getErrorFromException(error, locale));
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, [id, isCreate, locale]);

  const title = useMemo(() => getToolCatalogTitle(tool), [tool]);
  const toolId = useMemo(() => getToolCatalogIdentifier(tool) || id, [id, tool]);
  const machineName = useMemo(() => getToolCatalogMachineName(tool), [tool]);
  const callbackName = useMemo(() => getToolCatalogCallbackName(tool), [tool]);
  const category = useMemo(() => getToolCatalogCategory(tool), [tool]);
  const actionType = useMemo(() => getToolCatalogActionType(tool), [tool]);
  const accessMode = useMemo(() => getToolCatalogAccessMode(tool), [tool]);
  const sourceType = useMemo(() => getToolCatalogSourceType(tool), [tool]);
  const methodSignature = useMemo(() => getToolCatalogMethodSignature(tool), [tool]);
  const description = useMemo(() => getToolCatalogDescription(tool), [tool]);
  const summary = useMemo(() => getToolCatalogSummary(tool), [tool]);
  const purpose = useMemo(() => getToolCatalogPurpose(tool), [tool]);
  const tags = useMemo(() => getToolCatalogTags(tool), [tool]);
  const skillNames = useMemo(() => getToolCatalogSkillNames(tool), [tool]);
  const paramsList = useMemo(() => getToolCatalogParams(tool), [tool]);
  const inputSchemaText = useMemo(() => stringifyToolCatalogValue(tool?.inputSchema), [tool]);
  const outputText = useMemo(() => stringifyToolCatalogValue(getToolCatalogOutputValue(tool)), [tool]);
  const exampleInputText = useMemo(() => stringifyToolCatalogValue(tool?.exampleInput), [tool]);
  const exampleOutputText = useMemo(() => stringifyToolCatalogValue(tool?.exampleOutput), [tool]);
  const metadataText = useMemo(() => stringifyToolCatalogValue(tool?.metadata), [tool]);
  const rawJsonText = useMemo(() => stringifyToolCatalogValue(tool), [tool]);

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

  const leftPanel = (
    <div className="flex h-full flex-col overflow-hidden rounded-xl border border-border bg-background">
      <div className="flex-1 overflow-y-auto p-4 space-y-5" style={{ scrollbarWidth: "thin" }}>
        <section>
          <SectionLabel icon={Settings2} title="基本信息" iconCls="bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-400" />
          <div className="space-y-2.5">
            <div className="grid grid-cols-2 gap-2">
              <InfoBlock label="Tool ID" value={toolId} mono />
              <InfoBlock label="Tool Name" value={machineName || "-"} mono />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <InfoBlock label="Callback" value={callbackName || "-"} mono />
              <InfoBlock label="Implementation" value={methodSignature || "-"} mono />
            </div>
            {description ? (
              <InfoBlock label="Description" value={description} />
            ) : null}
            {summary && summary !== description ? (
              <InfoBlock label="Summary" value={summary} />
            ) : null}
            {purpose ? (
              <InfoBlock label="Purpose" value={purpose} />
            ) : null}
          </div>
        </section>

        <Divider />

        <section>
          <SectionLabel icon={ShieldCheck} title="运行信息" iconCls="bg-violet-50 text-violet-600 dark:bg-violet-950/40 dark:text-violet-400" />
          <div className="space-y-2.5">
            <div className="flex flex-wrap gap-2">
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
              {sourceType ? (
                <Chip size="sm" variant="soft" color="default">
                  {sourceType}
                </Chip>
              ) : null}
              {tool?.enabled !== undefined ? (
                <Chip size="sm" variant="soft" color={tool.enabled ? "success" : "default"}>
                  {tool.enabled ? "启用" : "禁用"}
                </Chip>
              ) : null}
            </div>
            <div className="grid grid-cols-2 gap-2">
              <InfoBlock label="Return Type" value={tool?.returnType || tool?.output?.type || "-"} mono />
              <InfoBlock label="Quota" value={getToolCatalogQuotaText(tool)} />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <InfoBlock label="Used Today" value={getToolCatalogUsageText(tool)} />
              <InfoBlock label="Available" value={tool?.available == null ? "-" : tool.available ? "true" : "false"} />
            </div>
          </div>
        </section>

        <Divider />

        <section>
          <SectionLabel icon={Tags} title="挂载与标签" iconCls="bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-400" />
          <div className="space-y-2.5">
            <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
              <p className="text-[10px] text-muted">Skill Names</p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {skillNames.length > 0 ? skillNames.map((skill) => (
                  <Chip key={skill} size="sm" variant="soft" color="accent">
                    {skill}
                  </Chip>
                )) : <p className="text-sm text-muted">未挂载 Skill</p>}
              </div>
            </div>
            <div className="rounded-lg border border-border bg-surface/40 px-3 py-2">
              <p className="text-[10px] text-muted">Tags</p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {tags.length > 0 ? tags.map((tag) => (
                  <Chip key={tag} size="sm" variant="soft" color="default">
                    {tag}
                  </Chip>
                )) : <p className="text-sm text-muted">未标记标签</p>}
              </div>
            </div>
          </div>
        </section>

        <Divider />

        <section>
          <SectionLabel icon={Braces} title="输入参数" iconCls="bg-green-50 text-green-600 dark:bg-green-950/40 dark:text-green-400" />
          {paramsList.length > 0 ? (
            <div className="space-y-2.5">
              {paramsList.map((param, index) => (
                <ParamCard key={`${param.name || "param"}-${index}`} param={param} index={index} />
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted">接口未返回结构化参数定义。</p>
          )}
        </section>

        {(tool?.usageNotes?.length || tool?.errorCases?.length) ? (
          <>
            <Divider />

            <section>
              <SectionLabel icon={Zap} title="补充信息" iconCls="bg-rose-50 text-rose-600 dark:bg-rose-950/40 dark:text-rose-400" />
              <div className="space-y-2.5">
                {tool?.usageNotes?.length ? (
                  <InfoBlock label="usageNotes" value={tool.usageNotes.join("\n")} />
                ) : null}
                {tool?.errorCases?.length ? (
                  <InfoBlock label="errorCases" value={tool.errorCases.join("\n")} />
                ) : null}
              </div>
            </section>
          </>
        ) : null}

        <div className="h-2" />
      </div>
    </div>
  );

  const rightPanel = (
    <div className="flex h-full flex-col overflow-hidden rounded-xl bg-[#1e1e1e] dark:bg-[#161616]">
      <div className="flex shrink-0 items-center gap-3 border-b border-[#2d2d2d] bg-[#252526] px-4 py-2">
        <span className="rounded bg-[#2d2d2d] px-1.5 py-0.5 font-mono text-[10px] text-[#9cdcfe]">Tool</span>
        <span className="text-[11px] text-[#6e6e6e]">接口结构与原始响应</span>
      </div>
      <div className="flex-1 overflow-y-auto p-4 space-y-4" style={{ scrollbarWidth: "thin" }}>
        <JsonPanel title="Input Schema" value={inputSchemaText || "{\n  \"type\": \"object\"\n}"} />
        <JsonPanel title="Output" value={outputText || "{\n  \"returnType\": \"unknown\"\n}"} />
        {exampleInputText ? <JsonPanel title="Example Input" value={exampleInputText} /> : null}
        {exampleOutputText ? <JsonPanel title="Example Output" value={exampleOutputText} /> : null}
        {metadataText ? <JsonPanel title="Metadata" value={metadataText} /> : null}
        <JsonPanel title="Raw Response" value={rawJsonText} />
      </div>
    </div>
  );

  return (
    <div className="flex h-full flex-col">
      <div className="flex shrink-0 items-center gap-2">
        <Button
          variant="tertiary"
          size="sm"
          onPress={() => router.push(`/${locale}/admin/agents/tools`)}
          className="shrink-0 gap-1"
        >
          <ArrowLeft className="size-3.5" />
          返回
        </Button>

        <div className="mx-1 h-4 w-px shrink-0 bg-border" />

        <div className="flex min-w-0 flex-1 items-center gap-2">
          <div className="flex size-7 shrink-0 items-center justify-center rounded-md bg-accent/10">
            <ShieldCheck className="size-3.5 text-accent" />
          </div>
          <h1 className="truncate text-sm font-semibold text-foreground">{title || toolId}</h1>
          {category ? (
            <span className="shrink-0 rounded bg-surface-secondary px-1.5 py-0.5 font-mono text-[10px] text-muted">
              {category}
            </span>
          ) : null}
          {actionType ? (
            <span className="shrink-0 rounded-md bg-blue-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
              {actionType}
            </span>
          ) : null}
        </div>
      </div>

      <div className="flex-1 min-h-0">
        <ResizablePanels
          defaultLeftWidth={42}
          minLeftWidth={30}
          maxLeftWidth={68}
          leftPanel={leftPanel}
          rightPanel={rightPanel}
        />
      </div>
    </div>
  );
}
