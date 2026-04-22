"use client";

import { useState, useMemo } from "react";
import { useTranslations } from "next-intl";
import { Button, Popover, ScrollShadow, Spinner } from "@heroui/react";
import { ChevronDown, Plus, Trash2, Search } from "lucide-react";
import type { InspirationSessionDTO } from "@/lib/api/dto/inspiration.dto";

interface InspirationSessionSelectorProps {
  sessions: InspirationSessionDTO[];
  currentSession: InspirationSessionDTO | null;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (session: InspirationSessionDTO) => void;
  onNew: () => void;
  onDelete: (sessionId: string) => void;
  isLoading: boolean;
  sessionListRef: React.RefObject<HTMLDivElement | null>;
  onScroll: (e: React.UIEvent<HTMLDivElement>) => void;
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  if (isToday) {
    return date.toLocaleTimeString(undefined, {
      hour: "2-digit",
      minute: "2-digit",
    });
  }
  return date.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });
}

export function InspirationSessionSelector({
  sessions,
  currentSession,
  isOpen,
  onOpenChange,
  onSelect,
  onNew,
  onDelete,
  isLoading,
  sessionListRef,
  onScroll,
}: InspirationSessionSelectorProps) {
  const t = useTranslations("workspace.inspiration");
  const [searchQuery, setSearchQuery] = useState("");

  const filteredSessions = useMemo(() => {
    if (!searchQuery.trim()) return sessions;
    const q = searchQuery.toLowerCase();
    return sessions.filter(
      (s) =>
        s.title.toLowerCase().includes(q) ||
        s.id.toLowerCase().includes(q)
    );
  }, [sessions, searchQuery]);

  const displayTitle = currentSession?.title || t("session.defaultTitle");

  return (
    <Popover isOpen={isOpen} onOpenChange={onOpenChange}>
      <Popover.Trigger>
        <Button
          variant="ghost"
          size="sm"
          className="max-w-60 gap-1 text-sm font-medium"
        >
          <span className="truncate">{displayTitle}</span>
          <ChevronDown className="size-3.5 shrink-0" />
        </Button>
      </Popover.Trigger>
      <Popover.Content placement="bottom start" className="w-72 p-0">
        <Popover.Dialog className="p-0">
          {/* Search */}
          <div className="border-b border-border px-3 py-2">
            <div className="flex items-center gap-2 rounded-md bg-surface-2 px-2 py-1.5">
              <Search className="size-3.5 text-muted" />
              <input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder={t("session.searchPlaceholder")}
                className="flex-1 bg-transparent text-xs outline-none placeholder:text-muted"
              />
            </div>
          </div>

          {/* Session list */}
          <ScrollShadow
            ref={sessionListRef}
            className="max-h-64 overflow-y-auto"
            onScroll={onScroll}
          >
            {filteredSessions.length === 0 ? (
              <div className="px-3 py-6 text-center text-xs text-muted">
                {isLoading ? <Spinner size="sm" /> : t("session.empty")}
              </div>
            ) : (
              <div className="p-1">
                {filteredSessions.map((session) => (
                  <div
                    key={session.id}
                    className={`group flex cursor-pointer items-center gap-2 rounded-md px-2.5 py-2 transition-colors hover:bg-surface-2 ${
                      session.id === currentSession?.id
                        ? "bg-accent/10 text-accent"
                        : ""
                    }`}
                    onClick={() => onSelect(session)}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-xs font-medium">
                        {session.title || t("session.defaultTitle")}
                      </div>
                      <div className="flex items-center gap-1.5 text-[10px] text-muted">
                        <span>{session.recordCount} records</span>
                        <span>·</span>
                        <span>{formatDate(session.lastActiveAt)}</span>
                      </div>
                    </div>
                    <Button
                      isIconOnly
                      variant="ghost"
                      size="sm"
                      className="size-6 opacity-0 group-hover:opacity-100"
                      onPress={(e) => {
                        onDelete(session.id);
                      }}
                    >
                      <Trash2 className="size-3" />
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </ScrollShadow>

          {/* New session button */}
          <div className="border-t border-border p-2">
            <Button
              variant="ghost"
              size="sm"
              onPress={onNew}
              className="w-full justify-start gap-2"
            >
              <Plus className="size-3.5" />
              {t("session.new")}
            </Button>
          </div>
        </Popover.Dialog>
      </Popover.Content>
    </Popover>
  );
}
