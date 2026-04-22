"use client";

import { useEffect, useState, useRef } from "react";
import Image from "next/image";
import { Card } from "@heroui/react";
import { useTheme } from "@/components/providers/theme-provider";

interface AuthCardProps {
  children: React.ReactNode;
}

export function AuthCard({ children }: AuthCardProps) {
  const { resolvedTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);
  const [cardHeight, setCardHeight] = useState<number | undefined>(undefined);

  useEffect(() => {
    setMounted(true);
  }, []);

  // Measure inner content height and drive card height explicitly,
  // so CSS transition can animate between values on any content change
  // (page navigation, tab switch, step switch).
  useEffect(() => {
    const el = contentRef.current;
    if (!el) return;

    const update = () => setCardHeight(el.scrollHeight);
    update();

    const observer = new ResizeObserver(update);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const logoSrc = mounted
    ? resolvedTheme === "dark"
      ? "/full-logo-dark.png"
      : "/full-logo.png"
    : "/full-logo.png";

  return (
    <div className="flex flex-col items-center gap-6">
      {/* Logo */}
      <div className="relative h-12 w-48">
        {mounted ? (
          <Image
            src={logoSrc}
            alt="ActioNow"
            fill
            sizes="192px"
            className="object-contain"
            priority
          />
        ) : (
          <div className="h-12 w-48 animate-pulse rounded bg-surface" />
        )}
      </div>

      {/* Auth Card – height animated on inner wrapper so Card border doesn't eat into content space */}
      <Card
        className="w-[480px] max-w-[calc(100vw-2rem)] glass bg-surface/80 shadow-overlay dark:bg-surface/90"
      >
        <div
          style={{ height: cardHeight }}
          className="overflow-hidden transition-[height] duration-300 ease-in-out"
        >
          <div ref={contentRef} className="px-8 py-6">
            {children}
          </div>
        </div>
      </Card>
    </div>
  );
}
