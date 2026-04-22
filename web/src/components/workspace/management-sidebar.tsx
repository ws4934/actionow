"use client";

import { useState, useMemo } from "react";
import { useTranslations, useLocale } from "next-intl";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { Button } from "@heroui/react";
import {
  Settings,
  Users,
  Wallet,
  Bot,
  Zap,
  PanelLeftClose,
  PanelLeft,
} from "lucide-react";
import { LOCALES } from "@/i18n/config";
import { useWorkspace } from "@/components/providers/workspace-provider";

interface ManagementSidebarProps {
  isCollapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
}

export function ManagementSidebar({
  isCollapsed: controlledIsCollapsed,
  onCollapsedChange,
}: ManagementSidebarProps) {
  const t = useTranslations("workspace.management.sidebar");
  const locale = useLocale();
  const pathname = usePathname();
  const { currentWorkspace } = useWorkspace();

  const [internalCollapsed, setInternalCollapsed] = useState(false);
  const isCollapsed = controlledIsCollapsed ?? internalCollapsed;

  const toggleCollapse = () => {
    const newValue = !isCollapsed;
    setInternalCollapsed(newValue);
    onCollapsedChange?.(newValue);
  };

  // Remove locale prefix from pathname for comparison
  const getPathWithoutLocale = (path: string) => {
    const segments = path.split("/").filter(Boolean);
    if (segments.length > 0 && LOCALES.includes(segments[0] as typeof LOCALES[number])) {
      return "/" + segments.slice(1).join("/");
    }
    return path;
  };

  const normalizedPathname = getPathWithoutLocale(pathname);

  const myRole = currentWorkspace?.myRole;
  const isSystem = currentWorkspace?.isSystem ?? false;
  const isAdmin = myRole === "CREATOR" || myRole === "ADMIN";
  const isSystemAdmin = isSystem && isAdmin;

  const sidebarItems = useMemo(() => {
    const items: Array<{
      key: string;
      path: string;
      href: string;
      icon: typeof Users;
      label: string;
    }> = [];

    // 成员管理: ADMIN+ only
    if (isAdmin) {
      items.push({
        key: "members",
        path: "/workspace/management/members",
        href: `/${locale}/workspace/management/members`,
        icon: Users,
        label: t("members"),
      });
    }

    // 钱包: all roles
    items.push({
      key: "wallet",
      path: "/workspace/management/wallet",
      href: `/${locale}/workspace/management/wallet`,
      icon: Wallet,
      label: t("wallet"),
    });

    // 设置: ADMIN+ only
    if (isAdmin) {
      items.push({
        key: "settings",
        path: "/workspace/management/settings",
        href: `/${locale}/workspace/management/settings`,
        icon: Settings,
        label: t("settings"),
      });
    }

    // 技能管理: ADMIN+ only
    if (isAdmin) {
      items.push({
        key: "skills",
        path: "/workspace/management/skills",
        href: `/${locale}/workspace/management/skills`,
        icon: Zap,
        label: t("skills"),
      });
    }

    // AI 管理后台: system tenant CREATOR/ADMIN only
    if (isSystemAdmin) {
      items.push({
        key: "aiAdmin",
        path: "/admin/models/ai-models",
        href: `/${locale}/admin/models/ai-models`,
        icon: Bot,
        label: t("aiAdmin"),
      });
    }

    return items;
  }, [isAdmin, isSystemAdmin, locale, t]);

  const isActive = (path: string) =>
    normalizedPathname === path || normalizedPathname.startsWith(`${path}/`);

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
          aria-label={isCollapsed ? "展开侧边栏" : "收起侧边栏"}
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
          {sidebarItems.map((item) => {
            const Icon = item.icon;
            const active = isActive(item.path);
            return (
              <Link
                key={item.key}
                href={item.href}
                title={isCollapsed ? item.label : undefined}
                className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all ${
                  active
                    ? "bg-accent text-accent-foreground shadow-sm"
                    : "text-muted hover:bg-surface hover:text-foreground"
                } ${isCollapsed ? "justify-center" : ""}`}
              >
                <Icon className="size-5 shrink-0" />
                {!isCollapsed && <span>{item.label}</span>}
              </Link>
            );
          })}
        </nav>
      </div>
    </div>
  );
}
