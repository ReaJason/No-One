import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";
import type { AuthFetch } from "@/api.server";
import { mapPaginatedResponse } from "@/api/server-api-utils";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type { Project } from "@/types/project";

const baseUrl = "/projects";

export interface ProjectSearchParams {
  name?: string | null;
  page?: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export const loadProjectSearchParams = createLoader({
  name: parseAsString,
  page: parseAsInteger.withDefault(1),
  perPage: parseAsInteger.withDefault(10),
  sortBy: parseAsString.withDefault("createdAt"),
  sortOrder: parseAsStringEnum(["asc", "desc"]).withDefault("desc"),
});

export async function getProjects(
  filters: ProjectSearchParams,
  authFetch: AuthFetch,
): Promise<PaginatedResponse<Project>> {
  const response = await authFetch<ServerPaginatedResponse<Project>>(baseUrl, {
    query: { ...filters, page: (filters.page ?? 1) - 1, pageSize: filters.perPage },
  });
  return mapPaginatedResponse(response);
}

export async function getProjectById(id: string, authFetch: AuthFetch): Promise<Project | null> {
  return await authFetch<Project>(`${baseUrl}/${id}`);
}

export interface CreateProjectRequest {
  name: string;
  code: string;
  status?: Project["status"];
  memberIds?: number[];
}

export async function createProject(
  projectData: CreateProjectRequest,
  authFetch: AuthFetch,
): Promise<Project> {
  return await authFetch<Project>(baseUrl, {
    method: "POST",
    body: projectData,
  });
}

export async function updateProject(
  id: string,
  projectData: Partial<Project> & { memberIds?: number[] },
  authFetch: AuthFetch,
): Promise<Project | null> {
  return await authFetch<Project>(`${baseUrl}/${id}`, {
    method: "PUT",
    body: projectData,
  });
}

export async function deleteProject(id: string, authFetch: AuthFetch): Promise<boolean> {
  await authFetch(`${baseUrl}/${id}`, { method: "DELETE" });
  return true;
}

export async function getAllProjects(authFetch: AuthFetch): Promise<Project[]> {
  const response = await authFetch<ServerPaginatedResponse<Project>>(baseUrl, {
    query: { page: 0, pageSize: 1000 },
  });
  return response.content;
}
