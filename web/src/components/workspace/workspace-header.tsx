"use client";

import { useTranslations, useLocale } from "next-intl";
import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import Image from "next/image";
import { useEffect, useState, useMemo, useCallback } from "react";
import { User, Zap, Menu, ListTodo, Loader2 } from "lucide-react";
import { Badge, Button, Separator, Tabs, Tooltip, Popover, ScrollShadow, toast} from "@heroui/react";
import { WorkspaceSwitcher } from "./workspace-switcher";
import { ThemeSwitcher } from "@/components/ui/theme-switcher";
import { LanguageSwitcher } from "@/components/ui/language-switcher";
import { useTheme } from "@/components/providers/theme-provider";
import { LOCALES } from "@/i18n/config";
import { getActiveScript, type ActiveScript } from "@/lib/stores/script-store";
import { useTaskStore, taskSelectors } from "@/lib/stores/task-store";
import { useWalletStore } from "@/lib/stores/wallet-store";
import { useWebSocketContext } from "@/lib/websocket/provider";
import { taskService } from "@/lib/api/services/task.service";
import { TaskCard } from "./task-center/task-card";
import { walletService } from "@/lib/api/services/wallet.service";
import { NotificationCenter } from "@/components/collab/notification-center/notification-center";
import type { WorkspaceDTO } from "@/lib/api/dto";
import { getErrorFromException } from "@/lib/api";

interface WorkspaceHeaderProps {
  workspaces: WorkspaceDTO[];
  currentWorkspaceId: string | null;
  onWorkspaceChange: (workspaceId: string) => void;
  onCreateWorkspace?: () => void;
}

export function WorkspaceHeader({
  workspaces,
  currentWorkspaceId,
  onWorkspaceChange,
  onCreateWorkspace,
}: WorkspaceHeaderProps) {
  const t = useTranslations("workspace.nav");
  const tTask = useTranslations("workspace.taskCenter");
  const locale = useLocale();
  const pathname = usePathname();
  const router = useRouter();
  const { resolvedTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  const [activeScriptId, setActiveScriptId] = useState<string | null>(null);
  const [activeScriptTitle, setActiveScriptTitle] = useState<string | null>(null);
  const wallet = useWalletStore((s) => s.wallet);
  const quota = useWalletStore((s) => s.quota);
  const setWalletAndQuota = useWalletStore((s) => s.setWalletAndQuota);
  const { reconnectCount } = useWebSocketContext();

  const runningCount = useTaskStore((s) => s.runningCount);
  const activeTasksList = useTaskStore(taskSelectors.activeTasksList);
  const hasActiveTasks = useTaskStore(taskSelectors.hasActiveTasks);
  const activeCount = useTaskStore(taskSelectors.activeCount);
  const cancelTaskInStore = useTaskStore((s) => s.cancelTask);

  const handleCancelTask = useCallback(
    async (taskId: string) => {
      cancelTaskInStore(taskId);
      try {
        await taskService.cancelTask(taskId);
      } catch {
        // Task may already be completed or cancelled
      }
    },
    [cancelTaskInStore]
  );

  // Initialize and listen for active script changes
  useEffect(() => {
    setMounted(true);

    // Get initial value for current workspace
    const script = getActiveScript(currentWorkspaceId);
    setActiveScriptId(script?.id ?? null);
    setActiveScriptTitle(script?.title ?? null);

    // Listen for changes
    const handleScriptChange = (e: CustomEvent<ActiveScript | null>) => {
      // Only update if the script is for the current workspace
      if (!e.detail || e.detail.workspaceId === currentWorkspaceId) {
        setActiveScriptId(e.detail?.id ?? null);
        setActiveScriptTitle(e.detail?.title ?? null);
      }
    };

    window.addEventListener("activeScriptChanged", handleScriptChange as EventListener);
    return () => {
      window.removeEventListener("activeScriptChanged", handleScriptChange as EventListener);
    };
  }, [currentWorkspaceId]);

  // Update active script when workspace changes
  useEffect(() => {
    if (currentWorkspaceId) {
      const script = getActiveScript(currentWorkspaceId);
      setActiveScriptId(script?.id ?? null);
      setActiveScriptTitle(script?.title ?? null);
    } else {
      setActiveScriptId(null);
      setActiveScriptTitle(null);
    }
  }, [currentWorkspaceId]);

  // Fetch wallet and quota when workspace changes — writes to Zustand store.
  // WS WALLET_BALANCE_CHANGED events update the same store automatically,
  // so the header re-renders in real time.
  useEffect(() => {
    if (!currentWorkspaceId) return;

    const fetchWalletData = async () => {
      try {
        const [walletData, quotaData] = await Promise.all([
          walletService.getWallet(),
          walletService.getMyQuota(),
        ]);
        setWalletAndQuota(walletData, quotaData);
      } catch (error) {
        console.error("Failed to fetch wallet data:", error);
        toast.danger(getErrorFromException(error, locale));
      }
    };

    fetchWalletData();
  }, [currentWorkspaceId, setWalletAndQuota, reconnectCount]);

  const logoSrc = mounted
    ? resolvedTheme === "dark"
      ? "/full-logo-dark.png"
      : "/full-logo.png"
    : "/full-logo.png";

  // Remove locale prefix from pathname for comparison
  const getPathWithoutLocale = useCallback((path: string) => {
    const segments = path.split("/").filter(Boolean);
    if (segments.length > 0 && LOCALES.includes(segments[0] as typeof LOCALES[number])) {
      return "/" + segments.slice(1).join("/");
    }
    return path;
  }, []);

  const normalizedPathname = getPathWithoutLocale(pathname);

  // Check if we're on a script detail page
  const isInScriptStudio = useMemo(() => {
    return /^\/workspace\/projects\/[^/]+$/.test(normalizedPathname);
  }, [normalizedPathname]);

  // Navigation items - 4 tabs (home hidden, tasks moved to right icon)
  const navItems = useMemo(() => {
    const studioHref = activeScriptId
      ? `/${locale}/workspace/projects/${activeScriptId}`
      : "";

    return [
      { key: "inspiration", href: `/${locale}/workspace/inspiration`, path: "/workspace/inspiration", label: t("inspiration"), disabled: false },
      { key: "projects", href: `/${locale}/workspace/projects`, path: "/workspace/projects", label: t("projects"), disabled: false },
      { key: "studio", href: studioHref, path: "/workspace/projects/", label: t("studio"), disabled: !activeScriptId },
      { key: "assets", href: `/${locale}/workspace/studio`, path: "/workspace/studio", label: t("assets"), disabled: false },
      { key: "management", href: `/${locale}/workspace/management/members`, path: "/workspace/management", label: t("management"), disabled: false },
    ];
  }, [activeScriptId, locale, t]);

  const isOnTasksPage = normalizedPathname.startsWith("/workspace/tasks");

  const getActiveKey = useCallback(() => {
    // No center tab highlighted when on tasks page
    if (isOnTasksPage) {
      return "__none__";
    }

    // When in script studio, highlight "studio"
    if (isInScriptStudio) {
      return "studio";
    }

    for (const item of navItems) {
      if (item.key === "studio") continue; // Skip studio for general matching
      if (normalizedPathname.startsWith(item.path)) {
        return item.key;
      }
    }
    return "projects";
  }, [isInScriptStudio, navItems, normalizedPathname]);

  const handleNavChange = (key: React.Key) => {
    const item = navItems.find((i) => i.key === key);
    if (item && !item.disabled && item.href) {
      router.push(item.href);
    }
  };

  return (
    <header className="sticky top-0 z-50 flex h-14 shrink-0 items-center justify-between border-b border-border bg-background/80 px-4 backdrop-blur-sm">
      {/* Left: Logo + Workspace Switcher */}
      <div className="flex items-center gap-4">
        <Link href={`/${locale}/workspace/projects`} className="relative h-8 w-24 shrink-0">
          {mounted ? (
            <Image
              src={logoSrc}
              alt="ActioNow"
              fill
              sizes="96px"
              className="object-contain object-left"
              priority
            />
          ) : (
            <div className="h-8 w-24 animate-pulse rounded bg-surface" />
          )}
        </Link>
        <Separator orientation="vertical" className="h-6 self-center" />
        <WorkspaceSwitcher
          workspaces={workspaces}
          currentWorkspaceId={currentWorkspaceId}
          onWorkspaceChange={onWorkspaceChange}
          onCreateWorkspace={onCreateWorkspace}
        />
      </div>

      {/* Center: Navigation - show on md+ screens */}
      <nav className="absolute left-1/2 hidden -translate-x-1/2 md:block">
        <Tabs
          selectedKey={getActiveKey()}
          onSelectionChange={handleNavChange}
        >
          <Tabs.ListContainer className="bg-transparent">
            <Tabs.List
              aria-label="Navigation"
              className="*:whitespace-nowrap *:px-1.5 *:py-2 *:text-sm *:font-medium *:data-[selected=true]:text-accent-foreground lg:*:px-3"
            >
              {navItems.map((item) => (
                <Tabs.Tab
                  key={item.key}
                  id={item.key}
                  isDisabled={item.disabled}
                  className={item.disabled ? "cursor-not-allowed opacity-50" : ""}
                >
                  {item.disabled ? (
                    <Tooltip delay={0}>
                      <Tooltip.Trigger>
                        <span>{item.label}</span>
                      </Tooltip.Trigger>
                      <Tooltip.Content>
                        <span className="text-xs">{t("studioDisabledTooltip")}</span>
                      </Tooltip.Content>
                    </Tooltip>
                  ) : (
                    <>
                      {item.key === "studio" && activeScriptTitle ? (
                        <span className="flex items-center gap-1">
                          {item.label}
                          <span className="hidden max-w-20 truncate text-xs xl:inline">
                            · {activeScriptTitle}
                          </span>
                        </span>
                      ) : (
                        item.label
                      )}
                    </>
                  )}
                  <Tabs.Indicator className="bg-accent" />
                </Tabs.Tab>
              ))}
            </Tabs.List>
          </Tabs.ListContainer>
        </Tabs>
      </nav>

      {/* Right: Credits + Task Center + Theme/Language + User */}
      <div className="flex items-center gap-2">
        {/* Mobile Navigation Menu - show on small screens */}
        <div className="md:hidden">
          <Popover>
            <Popover.Trigger>
              <Button isIconOnly variant="ghost" size="sm" aria-label={t("menuAriaLabel")}>
                <Menu className="size-4" />
              </Button>
            </Popover.Trigger>
            <Popover.Content className="w-40 p-1">
              <div className="flex flex-col">
                {navItems.map((item) => (
                  <Button
                    key={item.key}
                    variant="ghost"
                    size="sm"
                    isDisabled={item.disabled}
                    onPress={() => item.href && router.push(item.href)}
                    className="justify-start"
                  >
                    {item.label}
                  </Button>
                ))}
                <Button
                  variant="ghost"
                  size="sm"
                  onPress={() => router.push(`/${locale}/workspace/tasks`)}
                  className="justify-start"
                >
                  {t("tasks")}
                </Button>
              </div>
            </Popover.Content>
          </Popover>
        </div>

        {/* Credits Display - clickable to wallet page */}
        {mounted && wallet && quota && (
          <>
            <Tooltip delay={0}>
              <Tooltip.Trigger>
                <Link
                  href={`/${locale}/workspace/management/wallet`}
                  tabIndex={0}
                  className="flex items-center gap-1.5 rounded-md bg-surface px-2.5 py-1.5 text-sm transition-colors hover:bg-surface-2"
                >
                  <Zap className="size-4 text-warning" />
                  <span className="font-medium tabular-nums text-foreground">
                    {Math.min(quota.remainingAmount, wallet.available).toLocaleString()}
                  </span>
                  <span className="text-foreground-2">/</span>
                  <span className="tabular-nums text-foreground-2">
                    {wallet.available.toLocaleString()}
                  </span>
                </Link>
              </Tooltip.Trigger>
              <Tooltip.Content>
                <div className="space-y-1 text-xs">
                  <div>{t("walletAvailable", { amount: Math.min(quota.remainingAmount, wallet.available).toLocaleString() })}</div>
                  <div>{t("walletQuota", { amount: quota.remainingAmount.toLocaleString() })}</div>
                  <div>{t("walletBalance", { amount: wallet.available.toLocaleString() })}</div>
                  <div className="pt-1 text-foreground-2">{t("walletViewDetail")}</div>
                </div>
              </Tooltip.Content>
            </Tooltip>
            <Separator orientation="vertical" className="h-5 self-center" />
          </>
        )}

        {/* Task Center Icon */}
        <Popover>
          <Badge.Anchor>
            <Popover.Trigger>
              <Button
                isIconOnly
                variant="ghost"
                size="sm"
                aria-label={t("tasks")}
                className={isOnTasksPage ? "bg-surface-2 text-accent" : ""}
              >
                {runningCount > 0 ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <ListTodo className="size-4" />
                )}
              </Button>
            </Popover.Trigger>
            {runningCount > 0 && (
              <Badge color="danger" size="sm">
                {runningCount > 9 ? "9+" : runningCount}
              </Badge>
            )}
          </Badge.Anchor>
          <Popover.Content placement="bottom end" className="w-80 p-0">
            <Popover.Dialog className="p-0">
              <div className="border-b border-border px-4 py-2.5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-foreground">{tTask("tracker.title")}</span>
                    {activeCount > 0 && (
                      <span className="inline-flex size-5 items-center justify-center rounded-full bg-surface-2 text-[10px] font-medium text-foreground-2">
                        {activeCount}
                      </span>
                    )}
                  </div>
                  <Link
                    href={`/${locale}/workspace/tasks`}
                    className="text-xs text-accent hover:underline"
                  >
                    {tTask("tracker.viewAll")}
                  </Link>
                </div>
              </div>
              {hasActiveTasks ? (
                <ScrollShadow className="max-h-80 overflow-y-auto p-2">
                  <div className="space-y-1">
                    {activeTasksList.map((task) => (
                      <TaskCard
                        key={task.id}
                        task={task}
                        variant="compact"
                        onCancel={handleCancelTask}
                        onViewDetail={(taskId) => router.push(`/${locale}/workspace/tasks?taskId=${taskId}`)}
                      />
                    ))}
                  </div>
                </ScrollShadow>
              ) : (
                <div className="flex flex-col items-center justify-center gap-2 px-4 py-10 text-center">
                  <ListTodo className="size-8 text-muted/30" />
                  <p className="text-xs text-muted">{tTask("emptyState")}</p>
                </div>
              )}
            </Popover.Dialog>
          </Popover.Content>
        </Popover>

        {/* Notification Center */}
        <NotificationCenter />

        <LanguageSwitcher />
        <ThemeSwitcher />
        <Button
          isIconOnly
          variant="ghost"
          size="sm"
          onPress={() => router.push(`/${locale}/workspace/director/profile`)}
          aria-label={t("userCenterAriaLabel")}
        >
          <User className="size-4" />
        </Button>
      </div>
    </header>
  );
}
