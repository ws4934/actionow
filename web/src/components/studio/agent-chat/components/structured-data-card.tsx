"use client";

import { useState } from "react";
import { Chip, Table, ProgressBar, Label, Description, Spinner } from "@heroui/react";
import { ChevronDown, ChevronRight, FileCode, Sparkles } from "lucide-react";
import type { StructuredDataMetadata } from "@/lib/api/dto";
import { MarkdownRenderer } from "./markdown-renderer";
import { useSchema, type JsonSchema } from "../hooks/use-skill-schemas";
import { SegmentCardShell, type SegmentPosition } from "./segment-card-shell";

type Data = Record<string, unknown>;

function getProps(schema: JsonSchema | null): Array<{ key: string; title?: string; type?: string; enum?: unknown[] }> {
  if (!schema) return [];
  const props = (schema.properties as Record<string, JsonSchema> | undefined) ?? {};
  const order = (schema.required as string[] | undefined) ?? [];
  const keys = [...order, ...Object.keys(props).filter((k) => !order.includes(k))];
  return keys.map((k) => {
    const p = props[k] ?? {};
    return {
      key: k,
      title: (p.title as string | undefined) ?? k,
      type: p.type as string | undefined,
      enum: p.enum as unknown[] | undefined,
    };
  });
}

function Scalar({ value }: { value: unknown }) {
  if (value == null) return <span className="text-muted">—</span>;
  if (typeof value === "boolean") {
    return <Chip size="sm" variant="soft" color={value ? "success" : "default"}>{value ? "true" : "false"}</Chip>;
  }
  if (typeof value === "object") {
    return (
      <pre className="whitespace-pre-wrap break-words text-[11px] text-foreground/80">
        {JSON.stringify(value, null, 2)}
      </pre>
    );
  }
  return <span>{String(value)}</span>;
}

function JsonFallback({ data }: { data: unknown }) {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-1 text-[11px] text-muted hover:text-foreground"
      >
        {open ? <ChevronDown className="size-3" /> : <ChevronRight className="size-3" />}
        <FileCode className="size-3" />
        JSON
      </button>
      {open && (
        <pre className="mt-1 max-h-64 overflow-auto rounded-md bg-background/60 p-2 text-[11px]">
          {JSON.stringify(data, null, 2)}
        </pre>
      )}
    </div>
  );
}

function CardRenderer({ data, schema }: { data: Data; schema: JsonSchema | null }) {
  const props = getProps(schema);
  const entries = props.length > 0
    ? props.map((p) => ({ key: p.key, label: p.title ?? p.key, value: data[p.key] }))
    : Object.entries(data).map(([k, v]) => ({ key: k, label: k, value: v }));
  return (
    <div className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-xs">
      {entries.map((e) => (
        <div key={e.key} className="contents">
          <Label className="text-muted">{e.label}</Label>
          <div className="text-foreground/90">
            <Scalar value={e.value} />
          </div>
        </div>
      ))}
    </div>
  );
}

function TableRenderer({ data, schema }: { data: Data; schema: JsonSchema | null }) {
  const rowsRaw = (data.rows ?? data.items) as unknown;
  const rows: Data[] = Array.isArray(rowsRaw) ? (rowsRaw as Data[]) : [data];
  const props = getProps(schema);
  const columns = props.length > 0
    ? props.map((p) => ({ key: p.key, title: p.title ?? p.key }))
    : Object.keys(rows[0] ?? {}).map((k) => ({ key: k, title: k }));
  if (columns.length === 0) return <JsonFallback data={data} />;

  return (
    <Table>
      <Table.ScrollContainer className="max-h-80 overflow-auto">
        <Table.Content aria-label="structured-table">
          <Table.Header>
            {columns.map((c) => (
              <Table.Column key={c.key} id={c.key}>{c.title}</Table.Column>
            ))}
          </Table.Header>
          <Table.Body>
            <Table.Collection items={rows.map((r, i) => ({ ...r, __k: String(i) }))}>
              {(row: Data & { __k: string }) => (
                <Table.Row id={row.__k}>
                  <Table.Collection items={columns}>
                    {(c) => (
                      <Table.Cell id={c.key}>
                        <Scalar value={row[c.key]} />
                      </Table.Cell>
                    )}
                  </Table.Collection>
                </Table.Row>
              )}
            </Table.Collection>
          </Table.Body>
        </Table.Content>
      </Table.ScrollContainer>
    </Table>
  );
}

function FormRenderer({ data, schema }: { data: Data; schema: JsonSchema | null }) {
  if (!schema) return <CardRenderer data={data} schema={null} />;
  const props = getProps(schema);
  return (
    <div className="flex flex-col gap-2">
      {props.map((p) => {
        const v = data[p.key];
        return (
          <div key={p.key} className="flex flex-col gap-0.5">
            <Label className="text-[11px] text-muted">{p.title ?? p.key}</Label>
            <div className="rounded-md border border-border/30 bg-background/60 px-2 py-1 text-xs">
              <Scalar value={v} />
            </div>
            {p.enum && (
              <Description className="text-[10px]">
                {p.enum.map(String).join(" | ")}
              </Description>
            )}
          </div>
        );
      })}
    </div>
  );
}

function ChartRenderer({ data }: { data: Data }) {
  const series = data.series as Array<{ label?: string; value?: number }> | undefined;
  if (!Array.isArray(series) || series.length === 0) return <JsonFallback data={data} />;
  const max = Math.max(...series.map((s) => Number(s.value) || 0), 1);
  return (
    <div className="flex flex-col gap-1.5">
      {series.map((s, i) => {
        const v = Number(s.value) || 0;
        const pct = Math.round((v / max) * 100);
        return (
          <div key={i} className="flex items-center gap-2 text-xs">
            <span className="w-24 shrink-0 truncate text-muted">{s.label ?? `#${i + 1}`}</span>
            <div className="min-w-0 flex-1">
              <ProgressBar aria-label={s.label ?? `series-${i}`} value={pct} size="sm" color="accent">
                <ProgressBar.Track>
                  <ProgressBar.Fill />
                </ProgressBar.Track>
              </ProgressBar>
            </div>
            <span className="w-12 shrink-0 text-right tabular-nums">{v}</span>
          </div>
        );
      })}
    </div>
  );
}

function MarkdownRendererWrapper({ data }: { data: Data }) {
  const md = typeof data.markdown === "string"
    ? data.markdown
    : typeof data.content === "string"
      ? data.content
      : "```json\n" + JSON.stringify(data, null, 2) + "\n```";
  return <MarkdownRenderer content={md} />;
}

export function StructuredDataCard({ meta, position }: { meta: StructuredDataMetadata; position?: SegmentPosition }) {
  const { schema, isLoading } = useSchema(meta.schemaRef);
  const hint = meta.rendererHint ?? "card";
  const data = (meta.data ?? {}) as Data;
  const title = (schema?.title as string | undefined) ?? hint;

  const body = (() => {
    switch (hint) {
      case "markdown":
        return <MarkdownRendererWrapper data={data} />;
      case "table":
        return <TableRenderer data={data} schema={schema} />;
      case "form":
        return <FormRenderer data={data} schema={schema} />;
      case "chart":
        return <ChartRenderer data={data} />;
      case "card":
      default:
        return <CardRenderer data={data} schema={schema} />;
    }
  })();

  return (
    <SegmentCardShell
      icon={<Sparkles className="size-3.5 text-emerald-400" />}
      title={<span className="truncate text-sm text-foreground/90">{title}</span>}
      bgClass="bg-emerald-400/10"
      position={position}
      meta={
        <div className="flex items-center gap-2">
          <Chip size="sm" variant="soft" color="accent">{hint}</Chip>
          <span className="truncate font-mono text-[10px] text-muted">{meta.schemaRef}</span>
        </div>
      }
      collapsible
      defaultCollapsed={false}
    >
      {isLoading && !schema ? (
        <div className="flex items-center gap-2 text-[11px] text-muted">
          <Spinner size="sm" color="current" />
          <span>loading schema…</span>
        </div>
      ) : (
        body
      )}
    </SegmentCardShell>
  );
}
