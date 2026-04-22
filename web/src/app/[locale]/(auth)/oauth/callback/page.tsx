import { OAuthCallbackClient } from "@/components/auth/oauth-callback-client";

interface OAuthCallbackPageProps {
  searchParams: Promise<{
    code?: string;
    state?: string;
    provider?: string;
    error?: string;
  }>;
}

export default async function OAuthCallbackPage({ searchParams }: OAuthCallbackPageProps) {
  const { code, state, provider, error } = await searchParams;

  return (
    <OAuthCallbackClient
      code={code}
      state={state}
      provider={provider}
      error={error}
    />
  );
}
