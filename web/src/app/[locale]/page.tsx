import { LandingPage } from "@/components/landing/landing-page";

const jsonLd = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "ActioNow",
  applicationCategory: "MultimediaApplication",
  operatingSystem: "Web",
  url: "https://actionow.ai",
  description:
    "AI-powered video creation platform. Automate scriptwriting, storyboarding, and production with intelligent AI agents.",
  offers: {
    "@type": "Offer",
    price: "0",
    priceCurrency: "USD",
  },
  creator: {
    "@type": "Organization",
    name: "ActioNow",
    url: "https://actionow.ai",
  },
};

export default function LocalePage() {
  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <LandingPage />
    </>
  );
}
