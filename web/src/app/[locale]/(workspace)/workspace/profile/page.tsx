import { redirect } from "next/navigation";

interface ProfileRedirectPageProps {
  params: Promise<{ locale: string }>;
}

export default async function ProfileRedirectPage({ params }: ProfileRedirectPageProps) {
  const { locale } = await params;
  redirect(`/${locale}/workspace/director/profile`);
}
