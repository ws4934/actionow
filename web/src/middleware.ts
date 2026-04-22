import createMiddleware from "next-intl/middleware";
import { LOCALES, DEFAULT_LOCALE } from "./i18n/config";

export default createMiddleware({
  locales: LOCALES,
  defaultLocale: DEFAULT_LOCALE,
  localePrefix: "as-needed",
});

export const config = {
  matcher: ["/((?!api|_next|_vercel|.*\\..*).*)"],
};
