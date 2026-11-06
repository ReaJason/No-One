import type { User } from "@/types/admin";

type SessionData = {
  accessToken?: string;
  refreshToken?: string;
  user?: User;
  pending2faActionToken?: string;
  pending2faReturnTo?: string;
};

type SessionFlashData = {
  error: string;
  success: string;
};

type SessionValues = SessionData & SessionFlashData;

const STORAGE_KEY = "noone.session";

export class BrowserSession {
  constructor(private values: Partial<SessionValues> = {}) {}

  get<K extends keyof SessionValues>(key: K): SessionValues[K] | undefined {
    return this.values[key];
  }

  set<K extends keyof SessionValues>(key: K, value: SessionValues[K]): void {
    this.values[key] = value;
  }

  unset(key: keyof SessionValues): void {
    delete this.values[key];
  }

  toJSON(): Partial<SessionValues> {
    return this.values;
  }
}

export function getSession(_cookie?: string | null): BrowserSession {
  if (typeof window === "undefined") {
    return new BrowserSession();
  }

  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return new BrowserSession(stored ? JSON.parse(stored) : {});
  } catch {
    return new BrowserSession();
  }
}

export function commitSession(session: BrowserSession): void {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }
}

export function destroySession(_session: BrowserSession): void {
  if (typeof window !== "undefined") {
    window.localStorage.removeItem(STORAGE_KEY);
  }
}
