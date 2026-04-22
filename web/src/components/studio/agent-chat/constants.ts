import {
  FileText,
  Film,
  Users,
  Lightbulb,
  MessageSquare,
  Sparkles,
  MapPin,
  Package,
  Palette,
  Layout,
} from "lucide-react";
import { createElement, type ReactNode } from "react";
import type { EntityCategoryDef } from "./types";

export const POLLING_INTERVAL_MS = 2000;
export const MAX_POLLING_DURATION_MS = 15 * 60 * 1000; // 15 minutes max

export const ENTITY_CATEGORIES: EntityCategoryDef[] = [
  { key: "character", icon: Users, labelKey: "mention.character", nameField: "name", requestField: "characterId" },
  { key: "scene", icon: MapPin, labelKey: "mention.scene", nameField: "name", requestField: "sceneId" },
  { key: "prop", icon: Package, labelKey: "mention.prop", nameField: "name", requestField: "propId" },
  { key: "style", icon: Palette, labelKey: "mention.style", nameField: "name", requestField: "styleId" },
  { key: "episode", icon: Film, labelKey: "mention.episode", nameField: "title", requestField: "episodeId" },
  { key: "storyboard", icon: Layout, labelKey: "mention.storyboard", nameField: "title", requestField: "storyboardId" },
];

export interface QuickInput {
  label: string;
  message: string;
  icon: ReactNode;
}

export function getQuickInputs(t: (key: string) => string): QuickInput[] {
  return [
    { label: t("quickInput.scriptParse"), message: t("quickInput.scriptParseMsg"), icon: createElement(FileText, { className: "size-3.5" }) },
    { label: t("quickInput.episodeParse"), message: t("quickInput.episodeParseMsg"), icon: createElement(Film, { className: "size-3.5" }) },
    { label: t("quickInput.characterAnalysis"), message: t("quickInput.characterAnalysisMsg"), icon: createElement(Users, { className: "size-3.5" }) },
    { label: t("quickInput.sceneOptimize"), message: t("quickInput.sceneOptimizeMsg"), icon: createElement(Lightbulb, { className: "size-3.5" }) },
    { label: t("quickInput.dialoguePolish"), message: t("quickInput.dialoguePolishMsg"), icon: createElement(MessageSquare, { className: "size-3.5" }) },
    { label: t("quickInput.creativeSuggestion"), message: t("quickInput.creativeSuggestionMsg"), icon: createElement(Sparkles, { className: "size-3.5" }) },
  ];
}
