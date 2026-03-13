import type { User } from "@/types/admin";

export interface ProjectMember extends Pick<User, "id" | "username"> {}

export type ProjectStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";

export interface Project {
  id: string;
  name: string;
  code: string;
  bizName?: string | null;
  description?: string | null;
  members: ProjectMember[];
  status: ProjectStatus;
  startedAt?: string | null;
  endedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  archivedAt?: string | null;
  remark?: string | null;
  deleted?: boolean;
}
