import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import { ThemeProvider } from "@/components/providers/theme-provider";
import { ToastProvider } from "@/components/providers/toast-provider";
import { LocaleHtmlLang } from "@/components/providers/locale-html-lang";
import { LOCALES, type Locale } from "@/i18n/config";

const SITE_URL = "https://actionow.ai";

const localeMetadata: Record<Locale, { title: string; description: string; keywords: string[]; ogLocale: string }> = {
  en: {
    title: "ActioNow - AI-Powered Video Creation Platform",
    description:
      "ActioNow is your AI Director for video creation. Automate scriptwriting, storyboarding, and production with intelligent AI agents. Turn ideas into professional videos effortlessly.",
    keywords: [
      "AI video creation",
      "AI director",
      "video production",
      "scriptwriting",
      "storyboarding",
      "AI agents",
      "video automation",
      "ActioNow",
    ],
    ogLocale: "en_US",
  },
  zh: {
    title: "ActioNow - AI 智能视频创作平台",
    description:
      "ActioNow 是您的 AI 导演，助力视频创作全流程。智能编剧、分镜设计、拍摄制作，AI Agent 协同工作，轻松将创意转化为专业视频。",
    keywords: [
      "AI视频创作",
      "AI导演",
      "智能视频制作",
      "AI编剧",
      "分镜设计",
      "AI智能体",
      "视频自动化",
      "ActioNow",
    ],
    ogLocale: "zh_CN",
  },
};

export function generateStaticParams() {
  return LOCALES.map((locale) => ({ locale }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  const l = (LOCALES.includes(locale as Locale) ? locale : "zh") as Locale;
  const meta = localeMetadata[l];

  return {
    title: {
      default: meta.title,
      template: "%s | ActioNow",
    },
    description: meta.description,
    keywords: meta.keywords,
    alternates: {
      canonical: `/${l}`,
      languages: {
        en: "/en",
        "zh-CN": "/zh",
      },
    },
    openGraph: {
      type: "website",
      siteName: "ActioNow",
      title: meta.title,
      description: meta.description,
      url: `${SITE_URL}/${l}`,
      locale: meta.ogLocale,
      alternateLocale: l === "zh" ? "en_US" : "zh_CN",
      images: [
        {
          url: "/og-image.png",
          width: 1200,
          height: 630,
          alt: meta.title,
        },
      ],
    },
    twitter: {
      card: "summary_large_image",
      title: meta.title,
      description: meta.description,
      images: ["/og-image.png"],
    },
  };
}

interface LocaleLayoutProps {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}

export default async function LocaleLayout({
  children,
  params,
}: LocaleLayoutProps) {
  const { locale } = await params;

  if (!LOCALES.includes(locale as Locale)) {
    notFound();
  }

  setRequestLocale(locale);

  const messages = await getMessages();

  return (
    <ThemeProvider>
      <NextIntlClientProvider messages={messages}>
        <LocaleHtmlLang />
        <ToastProvider>
          {children}
        </ToastProvider>
      </NextIntlClientProvider>
    </ThemeProvider>
  );
}
