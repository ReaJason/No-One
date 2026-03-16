import type { User } from "@/types/admin";

export interface ApiError {
  message: string;
  code?: number;
  details?: any;
}

export class ApiClientError extends Error implements ApiError {
  code?: number;
  details?: any;

  constructor(
    message: string,
    options: {
      code?: number;
      details?: any;
      cause?: unknown;
    } = {},
  ) {
    super(message, { cause: options.cause });
    this.name = "ApiClientError";
    this.code = options.code;
    this.details = options.details;
  }
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  user: User;
}

export interface Page {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface ServerPaginatedResponse<T = any> {
  content: T[];
  page: Page;
}

export interface PaginatedResponse<T = any> {
  content: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}
