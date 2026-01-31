export interface Permission {
  id: number;
  name: string;
  code: string;
  category?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Role {
  id: string;
  name: string;
  permissions: Permission[];
  createdAt: string;
  updatedAt: string;
}

export interface User {
  id: number;
  username: string;
  password?: string;
  roles?: Role[];
  roleIds?: string[];
  enabled: boolean | null;
  createdAt: string;
  lastLogin?: string;
}
