"use client";

import { useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { BackgroundRippleEffect } from "@/components/ui/background-ripple-effect";

interface MonitorBackgroundProps {
  children: React.ReactNode;
}

export function MonitorBackground({ children }: MonitorBackgroundProps) {
  const t = useTranslations("monitor");
  const timecodeRef = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    const startTime = Date.now();
    const interval = setInterval(() => {
      const el = timecodeRef.current;
      if (!el) return;
      const elapsed = Date.now() - startTime;
      const h = Math.floor(elapsed / 3600000).toString().padStart(2, "0");
      const m = Math.floor((elapsed % 3600000) / 60000).toString().padStart(2, "0");
      const s = Math.floor((elapsed % 60000) / 1000).toString().padStart(2, "0");
      el.textContent = `${h}:${m}:${s}`;
    }, 1000);

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="relative min-h-screen w-full overflow-y-auto bg-background">
      {/* Interactive Ripple Grid */}
      <BackgroundRippleEffect />

      {/* Vignette */}
      <div className="monitor-vignette" />

      {/* Main Content — pointer-events-none lets clicks pass through to the ripple grid;
           the inner wrapper re-enables pointer-events on the actual card */}
      <div className="pointer-events-none relative z-10 flex min-h-screen items-center justify-center p-4">
        <div className="pointer-events-auto">
          {children}
        </div>
      </div>

      {/* Bottom REC Indicator */}
      <div className="pointer-events-none absolute bottom-4 left-5 z-20 flex items-center gap-2 font-mono text-xs opacity-40">
        <span className="monitor-rec-dot" />
        <span>{t("rec")}</span>
        <span ref={timecodeRef}>00:00:00</span>
      </div>
    </div>
  );
}
