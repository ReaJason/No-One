import type {User} from "@/types/admin";

export interface Project {
  id: string;
  name: string;
  code: string;
  members: User[];
  status: "draft" | "active" | "archived";
  createdAt: string;
  updatedAt: string;
}
