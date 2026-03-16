// app/context.server.ts
import { createContext } from "react-router";

export interface AuthUser {
  userId: string;
  username: string;
  roles: string[];
}

// 当前登录用户信息，由 authMiddleware 写入，loader/action 只读
export const userContext = createContext<AuthUser | null>(null);

/**
 * Token 刷新后待写回浏览器的 Set-Cookie 字符串。
 *
 * 写入方：api.server.ts 中的 apiFetch（发生 token 刷新时）
 * 读取方：auth.server.ts 中的 authMiddleware（在 next() 之后统一 append 到响应）
 *
 * 这是实现 "loader/action 零感知 token 刷新" 的核心桥梁：
 *   apiFetch 只负责把新 cookie 存进 context，
 *   middleware 只负责把 context 里的 cookie 写进 Response，
 *   两者通过 context 解耦，loader/action 完全不参与。
 */
export const pendingCookieContext = createContext<string | null>(null);
