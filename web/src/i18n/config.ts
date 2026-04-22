export const LOCALES = ["en", "zh"] as const;
export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "zh";

export const LOCALE_NAMES: Record<Locale, string> = {
  en: "English",
  zh: "中文",
};
