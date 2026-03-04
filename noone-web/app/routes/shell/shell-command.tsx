import CommandExecute from "@/components/shell/command-execute";
import { useShellManagerContext } from "./shell-manager-context";

export default function ShellCommandRoute() {
  const { shell } = useShellManagerContext();
  const os = shell.basicInfo?.os;
  const process = shell.basicInfo?.process;

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <CommandExecute shellId={shell.id} osName={os.name} cwdHint={process.cwd} />
    </div>
  );
}
