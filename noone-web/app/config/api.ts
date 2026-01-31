export const apiConfig = {
  baseURL: "http://localhost:8888/api",
  useMockData: false,
  timeout: 10000,
  retries: 3,
  retryDelay: 1000,
  defaultHeaders: {
    "Content-Type": "application/json",
  },
} as const;

export type ApiConfig = typeof apiConfig;
