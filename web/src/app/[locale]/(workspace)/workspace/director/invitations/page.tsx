import { redirect } from "next/navigation";

export default function InvitationsPage({
  params,
}: {
  params: { locale: string };
}) {
  redirect(`/${params.locale}/workspace/director/members`);
}
