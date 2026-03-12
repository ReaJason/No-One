import type { User } from "@/types/admin";

export interface ProjectMember extends Pick<User, "id" | "username"> {}

export interface Project {
  id: string;
  name: string;
  code: string;
  members: ProjectMember[];
  ownerId?: number | null;
  status: "draft" | "active" | "archived";
  createdAt: string;
  updatedAt: string;
}
