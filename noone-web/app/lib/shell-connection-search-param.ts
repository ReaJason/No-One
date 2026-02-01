import type { LoaderFunctionArgs } from "react-router";
import type { ShellConnectionSearchParams } from "@/types/shell-connection";

export async function loadShellConnectionSearchParams(
  request: LoaderFunctionArgs["request"],
): Promise<ShellConnectionSearchParams> {
  const url = new URL(request.url);
  const searchParams = url.searchParams;
  const statusParam = searchParams.get("status") ?? "";
  const projectIdParam = searchParams.get("projectId") ?? "";
  const parsedProjectId = Number(projectIdParam);

  return {
    group: searchParams.get("group") ?? "",
    status: statusParam ? statusParam.split(",")[0] : "",
    projectId: Number.isFinite(parsedProjectId) ? parsedProjectId : undefined,
    page: Number(searchParams.get("page")) || 1,
    perPage: Number(searchParams.get("perPage")) || 10,
    sortBy: searchParams.get("sortBy") || "createTime",
    sortOrder: (searchParams.get("sortOrder") as "asc" | "desc") || "desc",
  };
}
