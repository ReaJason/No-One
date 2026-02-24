console.log(import.meta.env);
export const apiConfig = {
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  useMockData: false,
  timeout: 10000,
  retries: 3,
  retryDelay: 1000,
  defaultHeaders: {
    "Content-Type": "application/json",
  },
} as const;
