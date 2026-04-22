import { getRequestConfig } from "next-intl/server";
import { LOCALES, type Locale } from "./config";

export default getRequestConfig(async ({ requestLocale }) => {
  let locale = await requestLocale;

  if (!locale || !LOCALES.includes(locale as Locale)) {
    locale = "zh";
  }

  return {
    locale,
    messages: (await import(`./messages/${locale}.json`)).default,
  };
});
