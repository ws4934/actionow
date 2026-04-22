"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { Sun, Moon, Monitor, Check } from "lucide-react";
import { Dropdown, Label, Button, Skeleton } from "@heroui/react";
import { useTheme } from "@/components/providers/theme-provider";

type ThemeOption = "light" | "dark" | "system";

const themeOptions: { id: ThemeOption; icon: typeof Sun }[] = [
  { id: "light", icon: Sun },
  { id: "dark", icon: Moon },
  { id: "system", icon: Monitor },
];

export function ThemeSwitcher() {
  const { theme, setTheme, resolvedTheme } = useTheme();
  const t = useTranslations("theme");
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) {
    return <Skeleton className="size-8 rounded-md" />;
  }

  const currentTheme = theme || "system";
  const CurrentIcon = resolvedTheme === "dark" ? Moon : Sun;

  return (
    <Dropdown>
      <Button
        isIconOnly
        variant="ghost"
        size="sm"
        aria-label={t(currentTheme as ThemeOption)}
      >
        <CurrentIcon className="size-4" />
      </Button>
      <Dropdown.Popover placement="bottom end" className="min-w-0">
        <Dropdown.Menu
          selectedKeys={new Set([currentTheme])}
          selectionMode="single"
          onAction={(key) => setTheme(key as ThemeOption)}
        >
          {themeOptions.map((option) => {
            const Icon = option.icon;
            return (
              <Dropdown.Item key={option.id} id={option.id} textValue={t(option.id)}>
                <Icon className="size-4 text-muted" />
                <Label>{t(option.id)}</Label>
                {currentTheme === option.id && (
                  <Check className="ml-auto size-4 text-accent" />
                )}
              </Dropdown.Item>
            );
          })}
        </Dropdown.Menu>
      </Dropdown.Popover>
    </Dropdown>
  );
}
