import { useOutletContext } from "react-router";
import type { ShellConnection } from "@/types/shell-connection";

export interface ShellManagerContext {
  shell: ShellConnection;
}

export function useShellManagerContext() {
  return useOutletContext<ShellManagerContext>();
}
