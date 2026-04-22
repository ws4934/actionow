"use client";

import { useLocale } from "next-intl";
import { useRouter, usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Dropdown, Label, Button, Skeleton } from "@heroui/react";
import { Languages, Check } from "lucide-react";
import { LOCALES, type Locale } from "@/i18n/config";

const LANGUAGE_OPTIONS = [
  { id: "zh", label: "中文", short: "中文" },
  { id: "en", label: "English", short: "EN" },
] as const;

export function LanguageSwitcher() {
  const locale = useLocale() as Locale;
  const router = useRouter();
  const pathname = usePathname();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) {
    return <Skeleton className="h-8 w-16 rounded-md" />;
  }

  const currentLanguage = LANGUAGE_OPTIONS.find((l) => l.id === locale) || LANGUAGE_OPTIONS[0];

  const handleChange = (newLocale: Locale) => {
    if (newLocale === locale) return;

    const segments = pathname.split("/").filter(Boolean);
    const hasLocalePrefix = LOCALES.includes(segments[0] as Locale);

    let newPath: string;
    if (hasLocalePrefix) {
      segments[0] = newLocale;
      newPath = `/${segments.join("/")}`;
    } else {
      newPath = `/${newLocale}${pathname}`;
    }

    router.push(newPath);
  };

  return (
    <Dropdown>
      <Button
        isIconOnly
        variant="ghost"
        size="sm"
        aria-label={currentLanguage.label}
      >
        <Languages className="size-4" />
      </Button>
      <Dropdown.Popover placement="bottom end" className="min-w-0">
        <Dropdown.Menu
          selectedKeys={new Set([locale])}
          selectionMode="single"
          onAction={(key) => handleChange(key as Locale)}
        >
          {LANGUAGE_OPTIONS.map((lang) => (
            <Dropdown.Item key={lang.id} id={lang.id} textValue={lang.label}>
              <Label>{lang.label}</Label>
              {locale === lang.id && (
                <Check className="ml-auto size-3.5 text-accent" />
              )}
            </Dropdown.Item>
          ))}
        </Dropdown.Menu>
      </Dropdown.Popover>
    </Dropdown>
  );
}
