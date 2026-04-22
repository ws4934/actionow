import { redirect } from "next/navigation";

export default function DirectorWalletRedirect() {
  return redirect("/workspace/management/wallet");
}
