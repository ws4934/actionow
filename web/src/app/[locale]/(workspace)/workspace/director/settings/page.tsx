import { redirect } from "next/navigation";

export default function DirectorSettingsRedirect() {
  return redirect("/workspace/management/settings");
}
