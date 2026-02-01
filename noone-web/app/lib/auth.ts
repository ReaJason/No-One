import { createCookieSessionStorage } from "react-router";
import type { User } from "@/types/admin";
import { apiClient } from "../api/api-client";

export const sessionStorage = createCookieSessionStorage({
  cookie: {
    name: "__session",
    httpOnly: true,
    maxAge: 60 * 60 * 24 * 7, // 7 days
    path: "/",
    sameSite: "lax",
    secrets: ["default-secret-key-change-in-production"],
    secure: true,
  },
});

export const SESSION_KEYS = {
  AUTH_TOKEN: "auth_token",
  USER_INFO: "user_info",
} as const;

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: User;
  expiresIn: number;
}

export class AuthService {
  private baseUrl = "/auth";
  async getAuthFromRequest(
    request: Request,
  ): Promise<{ token: string | null; user: User | null }> {
    const session = await sessionStorage.getSession(
      request.headers.get("Cookie"),
    );
    const token = session.get(SESSION_KEYS.AUTH_TOKEN) || null;
    const user = session.get(SESSION_KEYS.USER_INFO) || null;
    return { token, user };
  }

  async createAuthResponse(
    token: string,
    user: User,
    _expiresIn?: number,
    request?: Request,
  ): Promise<Response> {
    const session = request
      ? await sessionStorage.getSession(request.headers.get("Cookie"))
      : await sessionStorage.getSession();

    session.set(SESSION_KEYS.AUTH_TOKEN, token);
    session.set(SESSION_KEYS.USER_INFO, user);

    const cookie = await sessionStorage.commitSession(session);

    return new Response(null, {
      status: 200,
      headers: {
        "Set-Cookie": cookie,
      },
    });
  }

  async createLogoutResponse(request?: Request): Promise<Response> {
    const session = request
      ? await sessionStorage.getSession(request.headers.get("Cookie"))
      : await sessionStorage.getSession();

    session.unset(SESSION_KEYS.AUTH_TOKEN);
    session.unset(SESSION_KEYS.USER_INFO);

    const cookie = await sessionStorage.destroySession(session);

    return new Response(null, {
      status: 200,
      headers: {
        "Set-Cookie": cookie,
      },
    });
  }

  async login(credentials: LoginRequest): Promise<LoginResponse> {
    const response = await apiClient.post<LoginResponse>(
      `${this.baseUrl}/login`,
      credentials,
    );

    console.log(
      `[Auth] Login API called for user: ${response.data.user?.username}`,
    );
    return response.data;
  }

  async logout(): Promise<void> {
    await apiClient.post(`${this.baseUrl}/logout`);
    console.log("[Auth] Logout API called");
  }

  async refreshToken(refreshData: {
    refreshToken: string;
  }): Promise<LoginResponse> {
    const response = await apiClient.post<LoginResponse>(
      `${this.baseUrl}/refresh`,
      refreshData,
    );

    console.log(
      `[Auth] Token refresh API called for user: ${response.data.user?.username}`,
    );
    return response.data;
  }

  async getCurrentUser(): Promise<User> {
    const response = await apiClient.get<User>(`${this.baseUrl}/me`);
    return response.data;
  }

  async changePassword(
    oldPassword: string,
    newPassword: string,
  ): Promise<void> {
    await apiClient.post(`${this.baseUrl}/change-password`, {
      oldPassword,
      newPassword,
    });
  }

  async resetPassword(email: string): Promise<void> {
    await apiClient.post(`${this.baseUrl}/reset-password`, { email });
  }

  async verifyResetPassword(token: string, newPassword: string): Promise<void> {
    await apiClient.post(`${this.baseUrl}/verify-reset-password`, {
      token,
      newPassword,
    });
  }
}

export const auth = new AuthService();
export const authUtils = {
  getAuthFromRequest: (request: Request) => auth.getAuthFromRequest(request),
  createAuthResponse: (
    token: string,
    user: User,
    expiresIn?: number,
    request?: Request,
  ) => auth.createAuthResponse(token, user, expiresIn, request),
  createLogoutResponse: (request?: Request) =>
    auth.createLogoutResponse(request),
  login: (credentials: LoginRequest) => auth.login(credentials),
  logout: () => auth.logout(),
  refreshToken: (refreshData: { refreshToken: string }) =>
    auth.refreshToken(refreshData),
  getCurrentUser: () => auth.getCurrentUser(),
  changePassword: (oldPassword: string, newPassword: string) =>
    auth.changePassword(oldPassword, newPassword),
  resetPassword: (email: string) => auth.resetPassword(email),
  verifyResetPassword: (token: string, newPassword: string) =>
    auth.verifyResetPassword(token, newPassword),
};
