export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly response: unknown,
  ) {
    super(`API request failed (${status})`);
    this.name = 'ApiError';
  }
}

export type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown;
  token?: string;
};

export function createApiClient(baseUrl: string) {
  const base = new URL(baseUrl);
  if (!['http:', 'https:'].includes(base.protocol)) {
    throw new Error('API base URL must use HTTP or HTTPS');
  }

  return {
    async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
      if (!path.startsWith('/') || path.startsWith('//')) {
        throw new Error('API path must start with a single /');
      }
      const { body, token, headers: inputHeaders, ...init } = options;
      const headers = new Headers(inputHeaders);
      headers.set('Accept', 'application/json');
      if (body !== undefined) headers.set('Content-Type', 'application/json');
      if (token) headers.set('Authorization', `Bearer ${token}`);

      const response = await fetch(new URL(path.slice(1), `${base.href.replace(/\/?$/, '/')}`), {
        ...init,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await response.text();
      let data: unknown;
      try {
        data = text ? JSON.parse(text) : undefined;
      } catch {
        throw new ApiError(response.status, 'Invalid JSON response');
      }
      if (!response.ok) throw new ApiError(response.status, data);
      return data as T;
    },
  };
}

export function configuredApiClient() {
  const baseUrl = import.meta.env.VITE_API_BASE_URL;
  if (!baseUrl) throw new Error('Set VITE_API_BASE_URL before making API requests');
  return createApiClient(baseUrl);
}
