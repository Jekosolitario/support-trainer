const LOCAL_API_BASE_URL = 'http://localhost:8080/api/v1';

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();

export const environment = {
  apiBaseUrl: configuredApiBaseUrl || LOCAL_API_BASE_URL,
} as const;
