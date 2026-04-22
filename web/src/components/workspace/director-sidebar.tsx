"use client";

import { useState } from "react";
import { useTranslations, useLocale } from "next-intl";
import { usePathname } from "next/navigation";
import Link from "next/link";
import { Button } from "@heroui/react";
import {
  User,
  Settings,
  Users,
  Wallet,
  Bot,
  PanelLeftClose,
  PanelLeft
} from "lucide-react";
import { LOCALES } from "@/i18n/config";

interface DirectorSidebarProps {
  isCollapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
}

export function DirectorSidebar({
  isCollapsed: controlledIsCollapsed,
  onCollapsedChange
}: DirectorSidebarProps) {
  const t = useTranslations("workspace.director.sidebar");
  const tProfile = useTranslations("workspace.director.profile");
  const tStudio = useTranslations("workspace.director.studioManagement");
  const locale = useLocale();
  const pathname = usePathname();

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

  const sidebarSections = [
    {
      title: t("profile"),
      items: [
        {
          key: "profile",
          path: "/workspace/director/profile",
          href: `/${locale}/workspace/director/profile`,
          icon: User,
          label: tProfile("title"),
        },
      ],
    },
    {
      title: t("studioManagement"),
      items: [
        {
          key: "members",
          path: "/workspace/director/members",
          href: `/${locale}/workspace/director/members`,
          icon: Users,
          label: tStudio("members"),
        },
        {
          key: "wallet",
          path: "/workspace/director/wallet",
          href: `/${locale}/workspace/director/wallet`,
          icon: Wallet,
          label: tStudio("wallet"),
        },
        {
          key: "settings",
          path: "/workspace/director/settings",
          href: `/${locale}/workspace/director/settings`,
          icon: Settings,
          label: tStudio("settings"),
        },
        {
          key: "aiAdmin",
          path: "/admin/models/ai-models",
          href: `/${locale}/admin/models/ai-models`,
          icon: Bot,
          label: tStudio("aiAdmin"),
        },
      ],
    },
  ];

  const isActive = (path: string) =>
    normalizedPathname === path || normalizedPathname.startsWith(`${path}/`);

  return (
    <aside
      className={`sticky top-14 flex h-[calc(100vh-3.5rem)] flex-col border-r border-border bg-background transition-all duration-300 ${
        isCollapsed ? "w-14" : "w-52"
      }`}
    >
      {/* Collapse Toggle */}
      <div className={`flex items-center border-b border-border px-3 py-2 ${isCollapsed ? "justify-center" : "justify-between"}`}>
        {!isCollapsed && (
          <span className="text-xs font-medium text-muted">导演室</span>
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
        {sidebarSections.map((section, index) => (
          <div key={section.title} className={index > 0 ? "mt-4" : ""}>
            {!isCollapsed && (
              <h3 className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-muted">
                {section.title}
              </h3>
            )}
            <nav className="flex flex-col gap-1">
              {section.items.map((item) => {
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
                    <Icon className={`size-5 shrink-0 ${active ? "" : ""}`} />
                    {!isCollapsed && <span>{item.label}</span>}
                  </Link>
                );
              })}
            </nav>
          </div>
        ))}
      </div>
    </aside>
  );
}
