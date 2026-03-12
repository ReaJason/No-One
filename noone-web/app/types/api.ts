export interface ApiResponse<T = any> {
  data: T;
  message?: string;
  success: boolean;
  code?: number;
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
