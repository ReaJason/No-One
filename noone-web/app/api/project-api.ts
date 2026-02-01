import {
  createLoader,
  parseAsInteger,
  parseAsString,
  parseAsStringEnum,
} from "nuqs/server";
import type { Project } from "@/types/project";
import { apiClient, type PaginatedResponse } from "./api-client";

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
): Promise<PaginatedResponse<Project>> {
  return await apiClient.getPaginated<Project>(baseUrl, filters);
}

export async function getProjectById(id: string): Promise<Project | null> {
  return (await apiClient.get<Project>(`${baseUrl}/${id}`)).data;
}

export interface CreateProjectRequest {
  name: string;
  code: string;
  status?: Project["status"];
  memberIds?: number[];
}

export async function createProject(
  projectData: CreateProjectRequest,
): Promise<Project> {
  return (await apiClient.post<Project>(baseUrl, projectData)).data;
}

export async function updateProject(
  id: string,
  projectData: Partial<Project> & { memberIds?: number[] },
): Promise<Project | null> {
  return (await apiClient.put<Project>(`${baseUrl}/${id}`, projectData)).data;
}

export async function deleteProject(id: string): Promise<boolean> {
  await apiClient.delete(`${baseUrl}/${id}`);
  return true;
}

export async function getAllProjects(): Promise<Project[]> {
  return (
    await apiClient.getPaginated<Project>(baseUrl, { page: 1, perPage: 1000 })
  ).content;
}
