"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useSearchParams, useRouter, usePathname } from "next/navigation";
import { Button } from "@heroui/react";
import {
  Users,
  MapPin,
  Package,
  FileBox,
  Palette,
  PanelLeftClose,
  PanelLeft,
  type LucideIcon,
} from "lucide-react";

export type EntityType = "characters" | "scenes" | "props" | "assets" | "styles";

interface NavItem {
  key: EntityType;
  icon: LucideIcon;
}

const NAV_ITEMS: NavItem[] = [
  { key: "characters", icon: Users },
  { key: "scenes", icon: MapPin },
  { key: "props", icon: Package },
  { key: "assets", icon: FileBox },
  { key: "styles", icon: Palette },
];

interface MaterialSidebarProps {
  isCollapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
}

export function MaterialSidebar({
  isCollapsed: controlledIsCollapsed,
  onCollapsedChange,
}: MaterialSidebarProps) {
  const t = useTranslations("workspace.materialRoom");
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const [internalCollapsed, setInternalCollapsed] = useState(false);
  const isCollapsed = controlledIsCollapsed ?? internalCollapsed;

  const currentType: EntityType = (searchParams.get("type") as EntityType) || "characters";

  const toggleCollapse = () => {
    const newValue = !isCollapsed;
    setInternalCollapsed(newValue);
    onCollapsedChange?.(newValue);
  };

  const handleNavigation = (item: NavItem) => {
    const studioBase = pathname.replace(/\/studio.*$/, "/studio");
    const params = new URLSearchParams(searchParams.toString());
    params.set("type", item.key);
    router.replace(`${studioBase}?${params.toString()}`);
  };

  return (
    <div
      className={`flex h-full flex-col transition-all duration-300 ${
        isCollapsed ? "w-14" : "w-52"
      }`}
    >
      {/* Collapse Toggle */}
      <div className={`flex items-center border-b border-border px-3 py-2 ${isCollapsed ? "justify-center" : "justify-between"}`}>
        {!isCollapsed && (
          <span className="text-xs font-medium text-muted">{t("title")}</span>
        )}
        <Button
          isIconOnly
          variant="ghost"
          size="sm"
          onPress={toggleCollapse}
          className="size-7"
        >
          {isCollapsed ? (
            <PanelLeft className="size-4" />
          ) : (
            <PanelLeftClose className="size-4" />
          )}
        </Button>
      </div>

      {/* Sidebar Content */}
      <div className="flex-1 overflow-y-auto p-2">
        <nav className="flex flex-col gap-1">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            const active = currentType === item.key;
            return (
              <Button
                key={item.key}
                variant="ghost"
                onPress={() => handleNavigation(item)}
                className={`w-full justify-start gap-3 rounded-lg px-3 py-2.5 text-sm font-medium ${
                  active
                    ? "bg-accent text-accent-foreground shadow-sm"
                    : "text-muted hover:bg-surface hover:text-foreground"
                } ${isCollapsed ? "justify-center px-0" : ""}`}
              >
                <Icon className="size-5 shrink-0" />
                {!isCollapsed && <span>{t(`sidebar.${item.key}`)}</span>}
              </Button>
            );
          })}
        </nav>
      </div>
    </div>
  );
}
