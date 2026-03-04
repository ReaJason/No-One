import { useMemo } from "react";
import FileManager from "@/components/shell/file-manager";
import { deriveFileManagerInitialState } from "@/lib/file-manager-initial-state";
import { useShellManagerContext } from "./shell-manager-context";

export default function ShellFilesRoute() {
  const { shell } = useShellManagerContext();
  const initialState = useMemo(
    () => deriveFileManagerInitialState(shell.basicInfo),
    [shell.basicInfo],
  );

  return (
    <div className="flex h-full min-h-0 flex-col gap-4 p-4">
      <FileManager shellId={shell.id} initialState={initialState} />
    </div>
  );
}
