"use client";

import { useEffect, useMemo, useState } from "react";
import { Button, Card, Separator, Tabs } from "@heroui/react";
import { AskUserCard } from "@/components/studio/agent-chat/components/ask-user-card";
import { StatusIndicator } from "@/components/studio/agent-chat/components/status-indicator";
import { StructuredDataCard } from "@/components/studio/agent-chat/components/structured-data-card";
import type {
  AskUserMetadata,
  StatusEventMetadata,
  StructuredDataMetadata,
  UserAnswerDTO,
} from "@/lib/api/dto";
import type { AskUserRuntime, AskUserUiState } from "@/components/studio/agent-chat/types";
import { seedSchema } from "@/components/studio/agent-chat/hooks/use-skill-schemas";

const DEMO_SCHEMAS: Record<string, Record<string, unknown>> = {
  "demo:user-card": {
    title: "User Summary",
    type: "object",
    required: ["name", "role"],
    properties: {
      name: { type: "string", title: "Name" },
      role: { type: "string", title: "Role" },
      active: { type: "boolean", title: "Active" },
      loginCount: { type: "number", title: "Logins" },
    },
  },
  "demo:orders-table": {
    title: "Recent Orders",
    type: "object",
    properties: {
      id: { type: "string", title: "Order #" },
      customer: { type: "string", title: "Customer" },
      total: { type: "number", title: "Total" },
      paid: { type: "boolean", title: "Paid" },
    },
  },
  "demo:profile-form": {
    title: "Profile Draft",
    type: "object",
    required: ["displayName", "language"],
    properties: {
      displayName: { type: "string", title: "Display name" },
      language: { type: "string", title: "Language", enum: ["en", "zh", "ja"] },
      newsletter: { type: "boolean", title: "Subscribed" },
    },
  },
  "demo:traffic-chart": {
    title: "Weekly Traffic",
    type: "object",
  },
  "demo:release-notes": {
    title: "Release Notes",
    type: "object",
  },
};

function seedAll() {
  for (const [ref, schema] of Object.entries(DEMO_SCHEMAS)) {
    seedSchema(ref, schema);
  }
}

function makeAsk(
  partial: Partial<AskUserMetadata> & Pick<AskUserMetadata, "inputType" | "question" | "askId">,
): AskUserRuntime {
  return {
    state: "pending",
    ...partial,
  } as AskUserRuntime;
}

const STATUS_SAMPLES: StatusEventMetadata[] = [
  { phase: "preflight", label: "Preflight checks", progress: null },
  { phase: "skill_loading", label: "Loading skills", progress: 0.25 },
  { phase: "rag_retrieval", label: "Retrieving 8 / 24 docs", progress: 0.33 },
  { phase: "llm_invoking", label: "Calling model (claude-opus-4-7)", progress: null },
  { phase: "tool_batch_progress", label: "Batch 2 / 3", progress: 0.66 },
  { phase: "mission_step", label: "Step 4 / 5 — Compose storyboard", progress: 0.8 },
  { phase: "context_preparing", label: "Preparing context window", progress: 0.12 },
];

const STRUCTURED_SAMPLES: StructuredDataMetadata[] = [
  {
    schemaRef: "demo:user-card",
    rendererHint: "card",
    data: { name: "Ada Lovelace", role: "Principal Engineer", active: true, loginCount: 42 },
  },
  {
    schemaRef: "demo:orders-table",
    rendererHint: "table",
    data: {
      rows: [
        { id: "A-1001", customer: "Alice", total: 129.9, paid: true },
        { id: "A-1002", customer: "Bob", total: 54.0, paid: false },
        { id: "A-1003", customer: "Cheng", total: 12.5, paid: true },
      ],
    },
  },
  {
    schemaRef: "demo:profile-form",
    rendererHint: "form",
    data: { displayName: "Nova", language: "zh", newsletter: true },
  },
  {
    schemaRef: "demo:traffic-chart",
    rendererHint: "chart",
    data: {
      series: [
        { label: "Mon", value: 120 },
        { label: "Tue", value: 180 },
        { label: "Wed", value: 90 },
        { label: "Thu", value: 240 },
        { label: "Fri", value: 310 },
        { label: "Sat", value: 150 },
        { label: "Sun", value: 80 },
      ],
    },
  },
  {
    schemaRef: "demo:release-notes",
    rendererHint: "markdown",
    data: {
      markdown: [
        "## v3.0.3",
        "",
        "- **New**: HITL ask-user inline cards",
        "- **Fix**: Structured data table alignment",
        "- **Chore**: Upgrade HeroUI to v3.0.3",
        "",
        "> _Shipped 2026-04-20_",
      ].join("\n"),
    },
  },
];

function AskSection() {
  const [cycle, setCycle] = useState(0);

  const asks = useMemo<AskUserRuntime[]>(
    () => [
      makeAsk({
        askId: `ask-single-${cycle}`,
        question: "Which rendering engine should we ship with?",
        inputType: "single_choice",
        deadlineMs: 60_000,
        choices: [
          { id: "webgl", label: "WebGL", description: "Hardware accelerated, best quality" },
          { id: "canvas2d", label: "Canvas 2D", description: "Broad compatibility" },
          { id: "svg", label: "SVG", description: "Vector, scales cleanly" },
        ],
      }),
      makeAsk({
        askId: `ask-multi-${cycle}`,
        question: "Select the languages to include in the release.",
        inputType: "multi_choice",
        constraints: { minSelect: 1, maxSelect: 3 },
        choices: [
          { id: "en", label: "English" },
          { id: "zh", label: "中文" },
          { id: "ja", label: "日本語" },
          { id: "ko", label: "한국어" },
        ],
      }),
      makeAsk({
        askId: `ask-confirm-${cycle}`,
        question: "Deploy the new storyboard skill to production now?",
        inputType: "confirm",
      }),
      makeAsk({
        askId: `ask-text-${cycle}`,
        question: "Describe the tone for the opening scene.",
        inputType: "text",
        constraints: { minLength: 5, maxLength: 140 },
      }),
      makeAsk({
        askId: `ask-number-${cycle}`,
        question: "How many episodes should we render this batch?",
        inputType: "number",
        constraints: { min: 1, max: 20 },
      }),
    ],
    [cycle],
  );

  const [states, setStates] = useState<Record<string, { ui: AskUserUiState; answer?: UserAnswerDTO }>>({});

  useEffect(() => {
    setStates({});
  }, [cycle]);

  const onSubmit = (askId: string) => async (answer: UserAnswerDTO) => {
    await new Promise((r) => setTimeout(r, 250));
    setStates((s) => ({ ...s, [askId]: { ui: "answered", answer } }));
  };
  const onDismiss = (askId: string) => async () => {
    await new Promise((r) => setTimeout(r, 150));
    setStates((s) => ({ ...s, [askId]: { ui: "cancelled" } }));
  };

  return (
    <section className="flex flex-col gap-3">
      <header className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-foreground/80">AskUserCard — 5 input types</h2>
        <Button size="sm" variant="secondary" onPress={() => setCycle((c) => c + 1)}>
          Reset asks
        </Button>
      </header>
      <div className="flex flex-col gap-2">
        {asks.map((base) => {
          const s = states[base.askId];
          const ask: AskUserRuntime = s
            ? { ...base, state: s.ui, answer: s.answer, answeredAt: new Date().toISOString() }
            : base;
          return (
            <AskUserCard
              key={base.askId}
              ask={ask}
              onSubmit={onSubmit(base.askId)}
              onDismiss={onDismiss(base.askId)}
            />
          );
        })}

        <AskUserCard
          ask={{
            askId: "ask-timeout-demo",
            question: "(Demo) This prompt already timed out.",
            inputType: "single_choice",
            choices: [
              { id: "a", label: "Option A" },
              { id: "b", label: "Option B" },
            ],
            state: "timeout",
          }}
          onSubmit={async () => {}}
          onDismiss={async () => {}}
        />
      </div>
    </section>
  );
}

function StatusSection() {
  const [phaseIdx, setPhaseIdx] = useState(0);
  const active = STATUS_SAMPLES[phaseIdx];
  return (
    <section className="flex flex-col gap-3">
      <header className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-foreground/80">StatusIndicator — all phases</h2>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="secondary"
            onPress={() => setPhaseIdx((i) => (i - 1 + STATUS_SAMPLES.length) % STATUS_SAMPLES.length)}
          >
            Prev
          </Button>
          <Button
            size="sm"
            variant="primary"
            onPress={() => setPhaseIdx((i) => (i + 1) % STATUS_SAMPLES.length)}
          >
            Next
          </Button>
        </div>
      </header>
      <Card variant="secondary" className="p-3">
        <StatusIndicator status={active} />
      </Card>
      <div className="flex flex-col gap-1.5">
        <p className="text-[11px] text-muted">Static gallery (all phases at once):</p>
        {STATUS_SAMPLES.map((s) => (
          <StatusIndicator key={s.phase} status={s} />
        ))}
      </div>
    </section>
  );
}

function StructuredSection() {
  useEffect(() => {
    seedAll();
  }, []);
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-sm font-semibold text-foreground/80">StructuredDataCard — 5 renderer hints</h2>
      <div className="flex flex-col gap-2">
        {STRUCTURED_SAMPLES.map((meta) => (
          <StructuredDataCard key={meta.schemaRef} meta={meta} />
        ))}
      </div>
    </section>
  );
}

export default function HitlDemoPage() {
  useEffect(() => {
    seedAll();
  }, []);

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 p-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold">HITL / Structured Data / Status — Demo</h1>
        <p className="text-xs text-muted">
          Mock data for offline preview. No backend calls; schemas are pre-seeded.
        </p>
      </header>

      <Tabs defaultSelectedKey="ask">
        <Tabs.ListContainer>
          <Tabs.List aria-label="demo-sections">
            <Tabs.Tab id="ask">
              Ask User
              <Tabs.Indicator />
            </Tabs.Tab>
            <Tabs.Tab id="status">
              Status
              <Tabs.Indicator />
            </Tabs.Tab>
            <Tabs.Tab id="structured">
              Structured Data
              <Tabs.Indicator />
            </Tabs.Tab>
          </Tabs.List>
        </Tabs.ListContainer>
        <Tabs.Panel id="ask" className="pt-4">
          <AskSection />
        </Tabs.Panel>
        <Tabs.Panel id="status" className="pt-4">
          <StatusSection />
        </Tabs.Panel>
        <Tabs.Panel id="structured" className="pt-4">
          <StructuredSection />
        </Tabs.Panel>
      </Tabs>

      <Separator />
      <p className="text-[11px] text-muted">
        Source: <code>src/components/studio/agent-chat/components/*</code>
      </p>
    </div>
  );
}
