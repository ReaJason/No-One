import type { AuthFetch } from "@/api/api.server";
import type { PaginatedResponse, ServerPaginatedResponse } from "@/types/api";
import type { Project, ProjectStatus } from "@/types/project";

import { createLoader, parseAsInteger, parseAsString, parseAsStringEnum } from "nuqs/server";

import { mapPaginatedResponse } from "@/api/server-api-utils";

const baseUrl = "/projects";
const PROJECT_STATUSES = ["DRAFT", "ACTIVE", "ARCHIVED"] as const;

export interface ProjectSearchParams {
  name?: string | null;
  status?: ProjectStatus | null;
  page?: number;
  perPage?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

export const loadProjectSearchParams = createLoader({
  name: parseAsString,
  status: parseAsStringEnum([...PROJECT_STATUSES]),
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

export interface ProjectMutationRequest {
  name: string;
  code: string;
  status?: Project["status"];
  bizName?: string;
  description?: string;
  memberIds?: number[];
  startedAt?: string | null;
  endedAt?: string | null;
  remark?: string;
}

export async function createProject(
  projectData: ProjectMutationRequest,
  authFetch: AuthFetch,
): Promise<Project> {
  return await authFetch<Project>(baseUrl, {
    method: "POST",
    body: projectData,
  });
}

export async function updateProject(
  id: string,
  projectData: ProjectMutationRequest,
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
