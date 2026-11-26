import { clearToken, readToken, saveToken } from '../platform/desktop';
import type { AuthToken } from '../api/contracts';

export class Session {
  token: string | null = null;
  expiresAt = 0;
  refreshedAt = 0;
  private timer?: number;
  private refreshing?: Promise<void>;
  constructor(private request: <T>(path: string, options?: { method?: string; body?: unknown; token?: string }) => Promise<T>, private onExpired: () => void) {}
  async restore(): Promise<boolean> {
    this.token = await readToken();
    if (!this.token) return false;
    try {
      const result = await this.request<{ expiresAt: string }>('/api/v1/auth/session', { token: this.token });
      this.expiresAt = Date.parse(result.expiresAt); this.refreshedAt = Date.now(); this.schedule(); return true;
    } catch { await this.clear(); return false; }
  }
  async login(phone: string, password: string): Promise<void> {
    const result = await this.request<AuthToken>('/api/v1/auth/login', { method: 'POST', body: { phone, password } });
    await this.accept(result);
  }
  private async accept(result: AuthToken): Promise<void> {
    await saveToken(result.token);
    this.token = result.token; this.expiresAt = Date.parse(result.expiresAt); this.refreshedAt = Date.now(); this.schedule();
  }
  private schedule(): void { if (this.timer) clearInterval(this.timer); this.timer = globalThis.setInterval(() => void this.check(), 60_000); }
  async check(): Promise<void> {
    if (!this.token) return;
    if (Date.now() - this.refreshedAt < 3_600_000 && Date.now() < this.expiresAt - 60_000) return;
    if (this.refreshing) return this.refreshing;
    const oldToken = this.token;
    this.refreshing = (async () => {
      try {
        const refreshed = await this.request<AuthToken>('/api/v1/auth/refresh', { method: 'POST', token: oldToken });
        if (this.token === oldToken) await this.accept(refreshed);
      } catch { if (this.token === oldToken) await this.expire(); }
    })();
    try { await this.refreshing; } finally { this.refreshing = undefined; }
  }
  async logout(): Promise<void> {
    const token = this.token;
    await this.clear(); this.onExpired();
    if (token) try { await this.request('/api/v1/auth/logout', { method: 'POST', token }); } catch { /* local logout always succeeds */ }
  }
  async expire(): Promise<void> { await this.clear(); this.onExpired(); }
  private async clear(): Promise<void> { if (this.timer) clearInterval(this.timer); this.timer = undefined; this.token = null; this.expiresAt = 0; this.refreshedAt = 0; await clearToken(); }
}
