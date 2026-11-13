import axios from "axios";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "/api/v1",
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const csrf = document.cookie
    .split("; ")
    .find((row) => row.startsWith("prism_csrf="))
    ?.split("=")[1];
  if (csrf) {
    config.headers["X-CSRF-Token"] = decodeURIComponent(csrf);
  }
  return config;
});

let refresh: Promise<void> | null = null;

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const url = String(original?.url ?? "");
    if (error.response?.status === 401 && original && !original._retry && !url.includes("/auth/")) {
      original._retry = true;
      refresh ??= api.post("/auth/refresh").then(() => undefined).finally(() => {
        refresh = null;
      });
      try {
        await refresh;
        return api(original);
      } catch {
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  },
);

export function errorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.error?.message;
    if (typeof message === "string" && message.length > 0) return message;
  }
  return fallback;
}
