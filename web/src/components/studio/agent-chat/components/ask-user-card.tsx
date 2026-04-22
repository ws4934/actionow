"use client";

import { useState, useEffect, useMemo, useCallback } from "react";
import { useTranslations } from "next-intl";
import {
  Button,
  RadioGroup,
  Radio,
  CheckboxGroup,
  Checkbox,
  TextField,
  NumberField,
  TextArea,
  Input,
  Label,
  Description,
  Spinner,
  Chip,
  FieldError,
} from "@heroui/react";
import { Check, X, HelpCircle } from "lucide-react";
import type { AskUserRuntime, AskUserUiState } from "../types";
import type { UserAnswerDTO } from "@/lib/api/dto";
import { SegmentCardShell, type SegmentPosition } from "./segment-card-shell";

interface AskUserCardProps {
  ask: AskUserRuntime;
  onSubmit: (answer: UserAnswerDTO) => void | Promise<void>;
  onDismiss: (reason?: string) => void | Promise<void>;
  position?: SegmentPosition;
}

function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

export function AskUserCard({ ask, onSubmit, onDismiss, position }: AskUserCardProps) {
  const t = useTranslations("workspace.agent.ask");

  const { state, inputType, choices, constraints, deadlineMs } = ask;
  const isPending = state === "pending";

  const [remainingMs, setRemainingMs] = useState<number | null>(
    deadlineMs && isPending ? deadlineMs : null,
  );
  useEffect(() => {
    if (!isPending || !deadlineMs) return;
    const startedAt = Date.now();
    const id = setInterval(() => {
      const elapsed = Date.now() - startedAt;
      const left = Math.max(0, deadlineMs - elapsed);
      setRemainingMs(left);
      if (left <= 0) clearInterval(id);
    }, 1000);
    return () => clearInterval(id);
  }, [deadlineMs, isPending]);

  const isTimedOut = remainingMs !== null && remainingMs <= 0;
  const canInteract = isPending && !isTimedOut;

  const [singleValue, setSingleValue] = useState<string>(ask.answer?.answer ?? "");
  const [multiValues, setMultiValues] = useState<string[]>(ask.answer?.multiAnswer ?? []);
  const [textValue, setTextValue] = useState<string>(ask.answer?.answer ?? "");
  const [numberValue, setNumberValue] = useState<number | null>(
    ask.answer?.answer !== undefined && ask.answer.answer !== "" ? Number(ask.answer.answer) : null,
  );
  const [submitting, setSubmitting] = useState(false);

  const validationError = useMemo<string | null>(() => {
    if (!canInteract) return null;
    if (inputType === "multi_choice") {
      const n = multiValues.length;
      const minSel = constraints?.minSelect;
      const maxSel = constraints?.maxSelect;
      if (minSel !== undefined && n < minSel) return t("minSelect", { n: minSel });
      if (maxSel !== undefined && n > maxSel) return t("maxSelect", { n: maxSel });
    } else if (inputType === "text") {
      const v = textValue.trim();
      const minLen = constraints?.minLength;
      const maxLen = constraints?.maxLength;
      if (minLen !== undefined && v.length < minLen) return t("textTooShort", { n: minLen });
      if (maxLen !== undefined && v.length > maxLen) return t("textTooLong", { n: maxLen });
    } else if (inputType === "number") {
      if (numberValue === null || Number.isNaN(numberValue)) return t("numberRequired");
      const min = constraints?.min;
      const max = constraints?.max;
      if (min !== undefined && numberValue < min) return t("numberOutOfRange", { min, max: max ?? "∞" });
      if (max !== undefined && numberValue > max) return t("numberOutOfRange", { min: min ?? "-∞", max });
    } else if (inputType === "single_choice") {
      if (!singleValue) return t("choiceRequired");
    }
    return null;
  }, [inputType, constraints, multiValues, textValue, numberValue, singleValue, canInteract, t]);

  const submitPayload = useCallback((): UserAnswerDTO | null => {
    switch (inputType) {
      case "single_choice":
        return singleValue ? { answer: singleValue } : null;
      case "multi_choice":
        return { multiAnswer: multiValues };
      case "confirm":
        return null;
      case "text":
        return { answer: textValue };
      case "number":
        return numberValue !== null ? { answer: String(numberValue) } : null;
    }
  }, [inputType, singleValue, multiValues, textValue, numberValue]);

  const handleSubmit = useCallback(async () => {
    if (!canInteract || validationError) return;
    const payload = submitPayload();
    if (!payload) return;
    setSubmitting(true);
    try {
      await onSubmit(payload);
    } finally {
      setSubmitting(false);
    }
  }, [canInteract, validationError, submitPayload, onSubmit]);

  const handleConfirmChoice = useCallback(async (value: "yes" | "no") => {
    if (!canInteract) return;
    setSubmitting(true);
    try {
      await onSubmit({ answer: value });
    } finally {
      setSubmitting(false);
    }
  }, [canInteract, onSubmit]);

  const handleDismiss = useCallback(async () => {
    if (!isPending) return;
    setSubmitting(true);
    try {
      await onDismiss("user_dismiss");
    } finally {
      setSubmitting(false);
    }
  }, [isPending, onDismiss]);

  const badge = (() => {
    if (state === "answered") return <Chip size="sm" variant="soft" color="success">{t("answered")}</Chip>;
    if (state === "cancelled") return <Chip size="sm" variant="soft" color="default">{t("cancelled")}</Chip>;
    if (state === "timeout" || isTimedOut) return <Chip size="sm" variant="soft" color="warning">{t("timedOut")}</Chip>;
    if (remainingMs !== null) {
      return (
        <Chip size="sm" variant="soft" color="accent" className="tabular-nums">
          {t("countdownRemaining", { time: formatCountdown(remainingMs) })}
        </Chip>
      );
    }
    return null;
  })();

  return (
    <SegmentCardShell
      icon={<HelpCircle className="size-4 text-amber-400" />}
      title={<span className="text-sm font-medium">{ask.question}</span>}
      meta={badge}
      bgClass="bg-accent/10"
      position={position}
    >
      <div className="flex flex-col gap-2">
        {inputType === "single_choice" && (
          <RadioGroup
            aria-label={ask.question}
            value={singleValue}
            onChange={setSingleValue}
            isDisabled={!canInteract}
            className="flex flex-col gap-1"
          >
            {(choices ?? []).map((c) => (
              <Radio key={c.id} value={c.id} className="rounded-md px-2 py-1 data-[selected=true]:bg-accent/10">
                <Radio.Control>
                  <Radio.Indicator />
                </Radio.Control>
                <Radio.Content>
                  <Label className="cursor-pointer text-sm">{c.label}</Label>
                  {c.description && <Description className="text-[11px]">{c.description}</Description>}
                </Radio.Content>
              </Radio>
            ))}
          </RadioGroup>
        )}

        {inputType === "multi_choice" && (
          <CheckboxGroup
            aria-label={ask.question}
            value={multiValues}
            onChange={(v) => setMultiValues(v as string[])}
            isDisabled={!canInteract}
            className="flex flex-col gap-1"
          >
            {(choices ?? []).map((c) => (
              <Checkbox key={c.id} value={c.id} className="rounded-md px-2 py-1 data-[selected=true]:bg-accent/10">
                <Checkbox.Control>
                  <Checkbox.Indicator />
                </Checkbox.Control>
                <Checkbox.Content>
                  <Label className="cursor-pointer text-sm">{c.label}</Label>
                  {c.description && <Description className="text-[11px]">{c.description}</Description>}
                </Checkbox.Content>
              </Checkbox>
            ))}
          </CheckboxGroup>
        )}

        {inputType === "text" && (
          <TextField
            className="w-full"
            value={textValue}
            onChange={setTextValue}
            isDisabled={!canInteract}
          >
            <TextArea
              rows={3}
              placeholder={t("textPlaceholder")}
              value={textValue}
              onChange={(e) => setTextValue(e.target.value)}
              maxLength={constraints?.maxLength}
            />
          </TextField>
        )}

        {inputType === "number" && (
          <NumberField
            value={numberValue ?? undefined}
            onChange={(v) => setNumberValue(v ?? null)}
            minValue={constraints?.min}
            maxValue={constraints?.max}
            isDisabled={!canInteract}
          >
            <Input />
          </NumberField>
        )}

        {inputType === "confirm" && (
          <div className="flex gap-2">
            <Button
              variant="primary"
              size="sm"
              isDisabled={!canInteract || submitting}
              onPress={() => handleConfirmChoice("yes")}
            >
              {submitting ? <Spinner color="current" size="sm" /> : <Check className="size-3.5" />}
              {t("confirmYes")}
            </Button>
            <Button
              variant="secondary"
              size="sm"
              isDisabled={!canInteract || submitting}
              onPress={() => handleConfirmChoice("no")}
            >
              <X className="size-3.5" />
              {t("confirmNo")}
            </Button>
          </div>
        )}

        {!isPending && (
          <div className="text-xs text-foreground-2">
            {renderAnsweredReadout(ask, state, isTimedOut, t)}
          </div>
        )}

        {validationError && (
          <FieldError className="mt-2 text-[11px] text-danger">{validationError}</FieldError>
        )}

        {isPending && inputType !== "confirm" && (
          <div className="mt-1 flex justify-end gap-2">
            <Button
              variant="secondary"
              size="sm"
              onPress={handleDismiss}
              isDisabled={submitting}
            >
              {t("dismiss")}
            </Button>
            <Button
              variant="primary"
              size="sm"
              onPress={handleSubmit}
              isDisabled={!canInteract || !!validationError || submitting}
            >
              {submitting ? <Spinner color="current" size="sm" /> : null}
              {t("submit")}
            </Button>
          </div>
        )}
      </div>
    </SegmentCardShell>
  );
}

function renderAnsweredReadout(
  ask: AskUserRuntime,
  state: AskUserUiState,
  isTimedOut: boolean,
  t: (key: string, vars?: Record<string, string | number | Date>) => string,
) {
  if (state === "cancelled") return t("cancelled");
  if (state === "timeout" || isTimedOut) return t("timedOut");
  if (state !== "answered" || !ask.answer) return null;

  const { inputType, choices } = ask;
  const a = ask.answer;
  if (inputType === "single_choice" && a.answer) {
    const hit = choices?.find((c) => c.id === a.answer);
    return t("yourAnswer", { value: hit?.label ?? a.answer });
  }
  if (inputType === "multi_choice" && a.multiAnswer) {
    const labels = a.multiAnswer.map((id) => choices?.find((c) => c.id === id)?.label ?? id);
    return t("yourAnswer", { value: labels.join(", ") });
  }
  if (inputType === "confirm") {
    return t("yourAnswer", { value: a.answer === "yes" ? t("confirmYes") : t("confirmNo") });
  }
  return t("yourAnswer", { value: a.answer ?? "" });
}
