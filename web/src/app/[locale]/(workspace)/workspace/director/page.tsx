import { redirect } from "next/navigation";

export default function DirectorPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  // Redirect to profile page by default
  return redirect(`profile`);
}
