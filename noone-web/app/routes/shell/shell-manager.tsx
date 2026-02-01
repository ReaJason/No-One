import { type LoaderFunctionArgs, useLoaderData } from "react-router";
import ShellManager from "@/components/shell/shell-manager";
import { getShellConnectionById } from "@/lib/shell-connection-api";
import type { ShellConnection } from "@/types/shell-connection";

export async function loader({ params }: LoaderFunctionArgs) {
  const shellId = params.shellId as string | undefined;
  if (!shellId) {
    throw new Response("Invalid shell ID", { status: 400 });
  }
  const shell = await getShellConnectionById(shellId);
  if (!shell) {
    throw new Response("Shell connection not found", { status: 404 });
  }
  return { shell } as {
    shell: ShellConnection;
  };
}

export default function ShellManagerPage() {
  const { shell } = useLoaderData() as {
    shell: ShellConnection;
  };
  return <ShellManager shell={shell} onClose={() => {}} />;
}
