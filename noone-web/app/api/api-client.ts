import {ofetch} from "ofetch";
import {apiConfig} from "@/config/api";

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
  timeout?: number;
  retries?: number;
  retryDelay?: number;
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
      headers: this.config.headers,
      onRequest: this.onRequest.bind(this),
      onRequestError: this.onRequestError.bind(this),
      onResponse: this.onResponse.bind(this),
      onResponseError: this.onResponseError.bind(this),
    });
  }

  // 请求拦截器
  private async onRequest({ request, options }: any) {
    // 添加认证 token
    const token = this.getAuthToken();
    if (token) {
      options.headers = {
        ...options.headers,
        Authorization: `Bearer ${token}`,
      };
    }

    // 添加请求 ID 用于追踪
    options.headers = {
      ...options.headers,
      "X-Request-ID": this.generateRequestId(),
    };

    console.log(`[API] ${options.method || "GET"} ${request}`, {
      headers: options.headers,
      params: options.params,
      body: options.body,
    });
  }

  // 请求错误拦截器
  private onRequestError({ request, error }: any) {
    console.error(`[API] Request error for ${request}:`, error);
    throw new Error(`Request failed: ${error.message}`);
  }

  // 响应拦截器
  private onResponse({ request, response }: any) {
    console.log(`[API] ${response.status} ${request}`, {
      status: response.status,
      statusText: response.statusText,
      data: response._data,
    });
  }

  // 响应错误拦截器
  private onResponseError({ request, response }: any) {
    console.error(`[API] Response error for ${request}:`, {
      status: response.status,
      statusText: response.statusText,
      data: response._data,
    });

    const error: ApiError = {
      message: response._data?.error || response.statusText || "Unknown error",
      code: response.status,
      details: response._data,
    };

    // 处理特定的 HTTP 状态码
    switch (response.status) {
      case 401:
        this.handleUnauthorized();
        break;
      case 403:
        error.message = "Access denied";
        break;
      case 404:
        error.message = "Resource not found";
        break;
      case 422:
        error.message = "Validation error";
        break;
      case 500:
        error.message = "Internal server error";
        break;
    }

    throw error;
  }

  // 获取认证 token
  private getAuthToken(): string | null {
    if (typeof window !== "undefined") {
      return localStorage.getItem("auth_token");
    }
    // 在 SSR 模式下，从 cookies 或其他服务端存储获取 token
    return this.getTokenFromCookies();
  }

  // 从 cookies 获取 token（SSR 模式）
  private getTokenFromCookies(): string | null {
    if (typeof document !== "undefined") {
      const cookies = document.cookie.split(";");
      const tokenCookie = cookies.find((cookie) =>
        cookie.trim().startsWith("auth_token="),
      );
      if (tokenCookie) {
        return tokenCookie.split("=")[1];
      }
    }
    return null;
  }

  // 生成请求 ID
  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  // 处理未授权错误
  private handleUnauthorized() {
    if (typeof window !== "undefined") {
      localStorage.removeItem("auth_token");
      // 清除 cookie
      // @ts-expect-error - Necessary for cookie management in SSR
      document.cookie =
        "auth_token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
      // 重定向到登录页面
      window.location.href = "/auth/login";
    }
  }

  // 通用请求方法
  async request<T = any>(
    url: string,
    config: RequestConfig = {},
  ): Promise<ApiResponse<T>> {
    try {
      const response = await this.ofetchInstance(url, {
        method: config.method || "GET",
        headers: config.headers,
        params: config.params,
        body: config.body,
        timeout: config.timeout,
        retry: config.retries,
        retryDelay: config.retryDelay,
      });

      return {
        data: response,
        success: true,
        message: "Success",
      };
    } catch (error: any) {
      console.error(`[API] Request failed for ${url}:`, error);

      // 处理403错误，重定向到登录页面
      if (error?.status === 403 || error?.statusCode === 403) {
        console.warn("[API] 403 Forbidden detected, redirecting to login");
        // 使用setTimeout避免在错误处理中直接导航
        setTimeout(() => {
          window.location.href = "/auth/login";
        }, 100);
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

  setAuthToken(token: string) {
    if (typeof window !== "undefined") {
      localStorage.setItem("auth_token", token);
      // 同时设置 cookie 以支持 SSR
      // @ts-expect-error - Necessary for cookie management in SSR
      document.cookie = `auth_token=${token}; path=/; max-age=${7 * 24 * 60 * 60}; SameSite=Strict`;
    }
  }

  clearAuthToken() {
    if (typeof window !== "undefined") {
      localStorage.removeItem("auth_token");
      // 清除 cookie
      // @ts-expect-error - Necessary for cookie management in SSR
      document.cookie =
        "auth_token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
    }
  }

  updateConfig(newConfig: Partial<ApiClientConfig>) {
    this.config = { ...this.config, ...newConfig };
    this.ofetchInstance = ofetch.create({
      baseURL: this.config.baseURL,
      timeout: this.config.timeout,
      retry: this.config.retries,
      retryDelay: this.config.retryDelay,
      headers: this.config.headers,
      onRequest: this.onRequest.bind(this),
      onRequestError: this.onRequestError.bind(this),
      onResponse: this.onResponse.bind(this),
      onResponseError: this.onResponseError.bind(this),
    });
  }
}

export const apiClient = new ApiClient();
