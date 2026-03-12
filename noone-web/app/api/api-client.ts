import { ofetch } from "ofetch";
import { apiConfig } from "@/config/api";

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

export interface ApiClientConfig {
  baseURL: string;
  timeout?: number;
  retries?: number;
  retryDelay?: number;
  headers?: Record<string, string>;
}

export interface RequestConfig {
  method?: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  headers?: Record<string, string>;
  params?: Record<string, any>;
  body?: any;
  signal?: AbortSignal;
  timeout?: number;
  retries?: number;
  retryDelay?: number;
  auth?: RequestAuthConfig;
}

export interface RequestAuthConfig {
  accessTokenOverride?: string;
  serverCookieHeader?: string | null;
  serverRequest?: Request;
  skipAuthHeader?: boolean;
}

export function buildRequestConfig(request?: Request): Pick<RequestConfig, "headers"> {
  return {};
}

export function mergeRequestConfig(
  request: Request | undefined,
  config: RequestConfig = {},
): RequestConfig {
  const requestConfig = buildRequestConfig(request);
  return {
    ...requestConfig,
    ...config,
    headers: {
      ...requestConfig.headers,
      ...config.headers,
    },
    auth: config.auth,
  };
}

export async function buildServerRequestConfig(
  request?: Request,
): Promise<Pick<RequestConfig, "headers">> {
  return {};
}

export async function mergeServerRequestConfig(
  request: Request | undefined,
  config: RequestConfig = {},
): Promise<RequestConfig> {
  if (!request) {
    return mergeRequestConfig(undefined, config);
  }

  const cookieHeader = request.headers.get("Cookie");
  const requestConfig = await buildServerRequestConfig(request);
  return {
    ...requestConfig,
    ...config,
    headers: {
      ...requestConfig.headers,
      ...config.headers,
    },
    auth: {
      ...config.auth,
      serverCookieHeader: cookieHeader,
      serverRequest: request,
    },
  };
}

export function isAbortError(error: unknown): boolean {
  if (!error || typeof error !== "object") {
    return false;
  }
  const candidate = error as {
    name?: string;
    code?: string;
    message?: string;
    cause?: { name?: string; code?: string; message?: string };
  };
  if (candidate.name === "AbortError" || candidate.code === "ABORT_ERR") {
    return true;
  }
  if (candidate.cause?.name === "AbortError" || candidate.cause?.code === "ABORT_ERR") {
    return true;
  }
  const message = (candidate.message || candidate.cause?.message || "").toLowerCase();
  return (
    message.includes("aborted") ||
    message.includes("aborterror") ||
    message.includes("cancelled") ||
    message.includes("canceled")
  );
}

const DEFAULT_CONFIG: ApiClientConfig = {
  baseURL: apiConfig.baseURL,
  timeout: apiConfig.timeout,
  retries: apiConfig.retries,
  retryDelay: apiConfig.retryDelay,
  headers: apiConfig.defaultHeaders,
};

export class ApiClient {
  private config: ApiClientConfig;
  private ofetchInstance: any;

  constructor(config: Partial<ApiClientConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.ofetchInstance = ofetch.create({
      baseURL: this.config.baseURL,
      timeout: this.config.timeout,
      retry: this.config.retries,
      retryDelay: this.config.retryDelay,
      credentials: "include",
      headers: this.config.headers,
      onRequest: this.onRequest.bind(this),
      onRequestError: this.onRequestError.bind(this),
      onResponse: this.onResponse.bind(this),
      onResponseError: this.onResponseError.bind(this),
    });
  }

  // 请求拦截器
  private async onRequest({ request, options }: any) {
    const auth = options.auth as RequestAuthConfig | undefined;
    const headers = new Headers(options.headers);

    // 添加认证 token
    if (!auth?.skipAuthHeader) {
      let token: string | null = null;
      if (auth?.accessTokenOverride) {
        token = auth.accessTokenOverride;
      }
      if (token) {
        headers.set("Authorization", `Bearer ${token}`);
      }
    }

    // 添加请求 ID 用于追踪
    headers.set("X-Request-ID", this.generateRequestId());

    options.headers = headers;
  }

  // 请求错误拦截器
  private onRequestError({ request, error }: any) {
    console.error(`[API] Request error for ${request}:`, error);
    throw new ApiClientError(`Request failed: ${error.message}`, {
      cause: error,
    });
  }

  // 响应拦截器
  private onResponse({ request, response }: any) {}

  // 响应错误拦截器
  private onResponseError({ request, response }: any) {
    console.error(`[API] Response error for ${request}:`, {
      status: response.status,
      statusText: response.statusText,
      data: response._data,
    });

    const error = new ApiClientError(
      response._data?.message || response._data?.error || response.statusText || "Unknown error",
      {
        code: response.status,
        details: response._data,
      },
    );

    // 处理特定的 HTTP 状态码
    switch (response.status) {
      case 401:
        break;
      case 403:
        if (!response._data?.message && !response._data?.error) {
          error.message = "Access denied";
        }
        break;
      case 404:
        if (!response._data?.message && !response._data?.error) {
          error.message = "Resource not found";
        }
        break;
      case 422:
        if (!response._data?.message && !response._data?.error) {
          error.message = "Validation error";
        }
        break;
      case 500:
        if (!response._data?.message && !response._data?.error) {
          error.message = "Internal server error";
        }
        break;
    }

    throw error;
  }

  // 生成请求 ID
  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  // 通用请求方法
  async request<T = any>(url: string, config: RequestConfig = {}): Promise<ApiResponse<T>> {
    try {
      const response = await this.ofetchInstance(url, {
        method: config.method || "GET",
        headers: config.headers,
        params: config.params,
        body: config.body,
        signal: config.signal,
        timeout: config.timeout,
        retry: config.retries,
        retryDelay: config.retryDelay,
        auth: config.auth,
      });

      return {
        data: response,
        success: true,
        message: "Success",
      };
    } catch (error: any) {
      if (isAbortError(error)) {
        throw error;
      }

      const details = error?.details;
      if (details && typeof details === "object" && "error" in details) {
        return {
          data: details,
          success: false,
          message: details?.error,
        };
      }

      throw error;
    }
  }

  // GET 请求
  async get<T = any>(
    url: string,
    params?: Record<string, any>,
    config: Omit<RequestConfig, "method" | "params"> = {},
  ): Promise<ApiResponse<T>> {
    return this.request<T>(url, {
      ...config,
      method: "GET",
      params,
    });
  }

  // POST 请求
  async post<T = any>(
    url: string,
    body?: any,
    config: Omit<RequestConfig, "method" | "body"> = {},
  ): Promise<ApiResponse<T>> {
    return this.request<T>(url, {
      ...config,
      method: "POST",
      body,
    });
  }

  async put<T = any>(
    url: string,
    body?: any,
    config: Omit<RequestConfig, "method" | "body"> = {},
  ): Promise<ApiResponse<T>> {
    return this.request<T>(url, {
      ...config,
      method: "PUT",
      body,
    });
  }

  async patch<T = any>(
    url: string,
    body?: any,
    config: Omit<RequestConfig, "method" | "body"> = {},
  ): Promise<ApiResponse<T>> {
    return this.request<T>(url, {
      ...config,
      method: "PATCH",
      body,
    });
  }

  async delete<T = any>(
    url: string,
    config: Omit<RequestConfig, "method"> = {},
  ): Promise<ApiResponse<T>> {
    return this.request<T>(url, {
      ...config,
      method: "DELETE",
    });
  }

  async getPaginated<T = any>(
    url: string,
    params: {
      page?: number;
      perPage?: number;
      sortBy?: string;
      sortOrder?: "asc" | "desc";
      [key: string]: any;
    } = {},
    config: Omit<RequestConfig, "method" | "params"> = {},
  ): Promise<PaginatedResponse<T>> {
    const response = await this.get<ServerPaginatedResponse<T>>(
      url,
      { ...params, page: (params?.page ?? 1) - 1, pageSize: params?.perPage },
      config,
    );
    return {
      content: response.data.content,
      total: response.data.page.totalElements,
      page: response.data.page.number + 1,
      pageSize: response.data.page.size,
      totalPages: response.data.page.totalPages,
    };
  }

  async upload<T = any>(
    url: string,
    file: File,
    config: Omit<RequestConfig, "method" | "body"> = {},
  ): Promise<ApiResponse<T>> {
    const formData = new FormData();
    formData.append("file", file);

    return this.request<T>(url, {
      ...config,
      method: "POST",
      body: formData,
      headers: {
        ...config.headers,
      },
    });
  }

  async download(
    url: string,
    filename?: string,
    config: Omit<RequestConfig, "method"> = {},
  ): Promise<void> {
    try {
      const response = await this.ofetchInstance(url, {
        ...config,
        method: "GET",
        signal: config.signal,
        responseType: "blob",
      });

      const blob = new Blob([response]);
      const downloadUrl = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = downloadUrl;
      link.download = filename || "download";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(downloadUrl);
    } catch (error) {
      console.error(`[API] Download failed for ${url}:`, error);
      throw error;
    }
  }

  updateConfig(newConfig: Partial<ApiClientConfig>) {
    this.config = { ...this.config, ...newConfig };
    this.ofetchInstance = ofetch.create({
      baseURL: this.config.baseURL,
      timeout: this.config.timeout,
      retry: this.config.retries,
      retryDelay: this.config.retryDelay,
      credentials: "include",
      headers: this.config.headers,
      onRequest: this.onRequest.bind(this),
      onRequestError: this.onRequestError.bind(this),
      onResponse: this.onResponse.bind(this),
      onResponseError: this.onResponseError.bind(this),
    });
  }
}

export const apiClient = new ApiClient();
