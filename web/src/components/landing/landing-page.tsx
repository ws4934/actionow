import { useTranslations, useLocale } from "next-intl";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import NextImage from "next/image";
import { BackgroundRippleEffect } from "@/components/ui/background-ripple-effect";
import { Navbar, BrandLogo } from "./landing-navbar";

const BRAND_IMAGE_BASE = process.env.NEXT_PUBLIC_BRAND_IMAGE_BASE || "https://asset.alienworm.top/brand";
const AGENT_CARDS = [
  { key: "strategyLead", number: "01", image: `${BRAND_IMAGE_BASE}/StrategyLead.jpg` },
  { key: "scriptwriter", number: "02", image: `${BRAND_IMAGE_BASE}/Scriptwriter.jpg` },
  { key: "storyboardDirector", number: "03", image: `${BRAND_IMAGE_BASE}/StoryboardDirector.jpg` },
  { key: "voiceoverArtist", number: "04", image: `${BRAND_IMAGE_BASE}/VoiceoverArtist.jpg` },
  { key: "videoEditor", number: "05", image: `${BRAND_IMAGE_BASE}/VideoEditor.jpg` },
  { key: "growthMarketer", number: "06", image: `${BRAND_IMAGE_BASE}/GrowthMarketer.jpg` },
] as const;

/* ─── Hero: glass text effect ─── */
function HeroSection() {
  const t = useTranslations("landing.hero");

  return (
    <section className="w-full px-6 pt-28 pb-12 md:px-10 md:pt-32 md:pb-20">
      <h1 className="relative z-10 text-[13vw] font-black uppercase italic leading-[0.85] tracking-tighter md:whitespace-nowrap md:text-[10.5vw]">
        <span className="glass-text block">{t("titleLine1")}</span>
        <span className="hero-brand block">
          {t("brandPrefix")}<span className="hero-brand-n">{t("brandHighlight")}</span>{t("brandSuffix")}.AI
        </span>
        <span className="glass-text block">{t("titleLine2")}</span>
      </h1>
    </section>
  );
}

/* ─── Marquee ─── */
function MarqueeBanner() {
  const t = useTranslations("landing.marquee");
  const segment = `${t("item1")} \u2022 ${t("item2")} \u2022 ${t("item3")} \u2022 `;
  const repeated = segment.repeat(6);

  return (
    <div className="w-full overflow-hidden whitespace-nowrap bg-accent py-3">
      <div className="marquee-track">
        <span className="text-sm font-black uppercase tracking-[0.2em] text-accent-foreground">
          {repeated}
        </span>
        <span className="text-sm font-black uppercase tracking-[0.2em] text-accent-foreground">
          {repeated}
        </span>
      </div>
    </div>
  );
}

/* ─── Agents ─── */
function AgentsSection() {
  const t = useTranslations("landing");
  const locale = useLocale();

  return (
    <section
      id="agents"
      className="w-full flex-grow px-6 py-16 md:px-10 md:py-24"
    >
      <div className="mx-auto flex max-w-7xl flex-col items-start gap-12 lg:flex-row lg:gap-16">
        {/* Left column */}
        <div className="lg:sticky lg:top-24 lg:w-1/3">
          <h2 className="mb-4 text-3xl font-bold md:text-4xl">
            {t("content.title")}
          </h2>
          <p className="mb-10 text-base leading-relaxed text-muted md:text-lg">
            {t("content.subtitle")}
          </p>
          <div className="flex flex-col gap-3">
            <Link
              href={`/${locale}/register`}
              className="pointer-events-auto flex w-full items-center justify-center gap-3 rounded-lg bg-accent py-4 text-sm font-black uppercase tracking-wider text-accent-foreground transition-transform hover:scale-[1.02]"
            >
              {t("content.startCreating")}
              <ArrowRight className="size-4" />
            </Link>
            <a
              href="#agents"
              className="pointer-events-auto block w-full rounded-lg border border-foreground/10 bg-surface/40 py-4 text-center text-sm font-bold uppercase tracking-wider text-foreground backdrop-blur-sm transition-colors hover:bg-surface/60"
            >
              {t("content.learnMore")}
            </a>
          </div>
        </div>

        {/* Right column - agent grid */}
        <div className="grid w-full grid-cols-2 gap-3 md:grid-cols-3 lg:w-2/3">
          {AGENT_CARDS.map(({ key, number, image }) => (
            <div
              key={key}
              className="pointer-events-auto group relative aspect-square overflow-hidden rounded-lg border border-foreground/[0.1] bg-surface/50 transition-all hover:border-accent/30"
            >
              <NextImage
                src={image}
                alt={t(`agentCards.${key}`)}
                fill
                className="object-cover transition-transform duration-500 group-hover:scale-105"
                sizes="(min-width: 1024px) 20vw, (min-width: 768px) 28vw, 44vw"
              />

              <div className="absolute inset-0 bg-linear-to-b from-black/10 via-black/5 to-black/55" />

              <span className="absolute top-3 left-3 z-10 rounded-full border border-foreground/20 bg-background/70 px-2 py-1 text-[10px] font-black uppercase tracking-widest text-accent md:text-xs">
                {number}
              </span>

              <div className="absolute right-3 bottom-3 left-3 z-10 rounded-md border border-foreground/15 bg-background/80 p-3 md:p-4">
                <h3 className="mb-1 text-sm font-bold md:text-base">
                  {t(`agentCards.${key}`)}
                </h3>
                <p className="text-[10px] leading-relaxed text-muted md:text-xs">
                  {t(`agentCards.${key}Desc`)}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ─── Footer ─── */
function Footer() {
  const t = useTranslations("landing");
  const locale = useLocale();

  return (
    <footer className="pointer-events-auto w-full border-t border-foreground/[0.06] bg-background px-6 py-10 md:px-10">
      <div className="mx-auto flex max-w-7xl flex-col items-center gap-8 md:flex-row md:justify-between">
        {/* Brand */}
        <div className="flex items-center gap-3">
          <Link href={`/${locale}`}>
            <BrandLogo />
          </Link>
        </div>

        {/* Nav */}
        <nav className="flex gap-6 text-[11px] font-semibold uppercase tracking-widest text-muted">
          <a href="#agents" className="transition-colors hover:text-foreground">
            {t("nav.features")}
          </a>
        </nav>

        <div className="flex flex-col items-center gap-3 text-center md:items-end md:text-right">
          <div className="space-y-1">
            <p className="text-[10px] font-semibold uppercase tracking-[0.3em] text-muted/70">
              {t("footer.contactLabel")}
            </p>
            <a
              href="mailto:actionow.ai@gmail.com"
              className="text-sm font-semibold text-foreground transition-colors hover:text-accent"
            >
              actionow.ai@gmail.com
            </a>
          </div>
          <p className="text-[10px] tracking-widest text-muted/60">
            {t("footer.rights")}
          </p>
        </div>
      </div>
    </footer>
  );
}

/* ─── Page ─── */
export function LandingPage() {
  return (
    <div className="scrollbar-hide relative min-h-screen overflow-x-hidden bg-background text-foreground">
      {/* Full-page interactive grid background */}
      <BackgroundRippleEffect />

      {/* Content layer — pointer-events-none lets grid receive interactions;
          interactive children re-enable pointer-events */}
      <div className="pointer-events-none relative z-10 flex min-h-screen flex-col">
        <Navbar />

        <main className="flex flex-grow flex-col">
          <HeroSection />
          <div className="pointer-events-auto">
            <MarqueeBanner />
          </div>
          <AgentsSection />
        </main>

        <Footer />
      </div>
    </div>
  );
}
