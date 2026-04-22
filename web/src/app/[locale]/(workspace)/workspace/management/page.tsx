import { redirect } from "next/navigation";

export default function ManagementPage() {
  return redirect("management/members");
}
