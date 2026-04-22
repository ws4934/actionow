import type { Metadata } from "next";
import localFont from "next/font/local";
import Script from "next/script";
import "./globals.css";

const plusJakartaSans = localFont({
  src: [
    { path: "../fonts/PlusJakartaSans-Variable.ttf", style: "normal", weight: "200 800" },
    { path: "../fonts/PlusJakartaSans-Italic-Variable.ttf", style: "italic", weight: "200 800" },
  ],
  variable: "--font-sans",
  display: "swap",
});

const jetbrainsMono = localFont({
  src: [
    { path: "../fonts/JetBrainsMono-Variable.ttf", style: "normal", weight: "100 800" },
    { path: "../fonts/JetBrainsMono-Italic-Variable.ttf", style: "italic", weight: "100 800" },
  ],
  variable: "--font-mono",
  display: "swap",
});

const GA_ID = "G-TFFNM9MJB7";

export const metadata: Metadata = {
  metadataBase: new URL("https://actionow.ai"),
  title: {
    default: "ActioNow - AI-Powered Video Creation Platform",
    template: "%s | ActioNow",
  },
  description:
    "ActioNow is an AI-powered video creation platform. Automate scriptwriting, storyboarding, and production with intelligent AI agents.",
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
  authors: [{ name: "ActioNow" }],
  creator: "ActioNow",
  publisher: "ActioNow",
  icons: {
    icon: "/favicon.ico",
    apple: "/logo.png",
  },
  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      "max-video-preview": -1,
      "max-image-preview": "large",
      "max-snippet": -1,
    },
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html suppressHydrationWarning>
      <body
        className={`${plusJakartaSans.variable} ${jetbrainsMono.variable} font-sans antialiased`}
      >
        {children}
        <Script
          src={`https://www.googletagmanager.com/gtag/js?id=${GA_ID}`}
          strategy="afterInteractive"
        />
        <Script id="google-analytics" strategy="afterInteractive">
          {`
            window.dataLayer = window.dataLayer || [];
            function gtag(){dataLayer.push(arguments);}
            gtag('js', new Date());
            gtag('config', '${GA_ID}');
          `}
        </Script>
      </body>
    </html>
  );
}
