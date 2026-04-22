import { redirect } from "next/navigation";

export default function DirectorMembersRedirect() {
  return redirect("/workspace/management/members");
}
