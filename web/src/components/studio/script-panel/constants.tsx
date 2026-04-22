/**
 * Script Panel Constants
 */

import {
  FileText,
  Film,
  Clapperboard,
  Users,
  MapPin,
  Package,
  Image as ImageIcon,
  Sparkles,
} from "lucide-react";
import type { TabConfig, StyleConfig, StatusOption, ScriptStatus } from "./types";

// Tab configuration
export const TABS: TabConfig[] = [
  { key: "details", label: "详情", icon: <FileText className="size-3.5" /> },
  { key: "episodes", label: "剧集", icon: <Film className="size-3.5" /> },
  { key: "storyboards", label: "分镜", icon: <Clapperboard className="size-3.5" /> },
  { key: "characters", label: "角色", icon: <Users className="size-3.5" /> },
  { key: "scenes", label: "场景", icon: <MapPin className="size-3.5" /> },
  { key: "props", label: "道具", icon: <Package className="size-3.5" /> },
  { key: "assets", label: "素材", icon: <ImageIcon className="size-3.5" /> },
];

// Tab i18n key mapping (for translated labels)
export const TAB_I18N_KEYS: Record<string, string> = {
  details: "tabs.details",
  episodes: "tabs.episodes",
  storyboards: "tabs.storyboards",
  characters: "tabs.characters",
  scenes: "tabs.scenes",
  props: "tabs.props",
  assets: "tabs.assets",
};

// Style options
export const STYLES: StyleConfig[] = [
  { key: "default", label: "默认风格", icon: <Sparkles className="size-3.5" /> },
  { key: "anime", label: "动漫风格", icon: <Sparkles className="size-3.5" /> },
  { key: "realistic", label: "写实风格", icon: <Sparkles className="size-3.5" /> },
  { key: "cartoon", label: "卡通风格", icon: <Sparkles className="size-3.5" /> },
  { key: "watercolor", label: "水彩风格", icon: <Sparkles className="size-3.5" /> },
];

// Status options
export const STATUS_OPTIONS: StatusOption[] = [
  { key: "DRAFT", label: "草稿" },
  { key: "IN_PROGRESS", label: "进行中" },
  { key: "COMPLETED", label: "已完成" },
  { key: "ARCHIVED", label: "已归档" },
];

// Get tab label by key
export function getTabLabel(tabKey: string): string {
  const tab = TABS.find((t) => t.key === tabKey);
  return tab?.label || tabKey;
}
