import { describe, expect, it, vi } from 'vitest';
import { Session } from './session';
import type { AuthToken } from '../api/contracts';

vi.mock('../platform/desktop', () => ({ readToken: async () => null, saveToken: async () => {}, clearToken: async () => {} }));
const auth = (token: string): AuthToken => ({ token, role: 'USER', userId: 1, expiresAt: new Date(Date.now() + 7_200_000).toISOString() });

describe('session refresh', () => {
  it('coalesces simultaneous refreshes and keeps the new token', async () => {
    let resolveRefresh!: (value: AuthToken) => void;
    const pending = new Promise<AuthToken>(resolve => { resolveRefresh = resolve; });
    const request = vi.fn(async (path: string) => path.endsWith('/refresh') ? pending : auth('old'));
    const session = new Session(request as unknown as ConstructorParameters<typeof Session>[0], vi.fn());
    await session.login('13800000000', 'password123');
    session.refreshedAt = Date.now() - 3_600_001;
    const first = session.check(), second = session.check();
    resolveRefresh(auth('new'));
    await Promise.all([first, second]);
    expect(request).toHaveBeenCalledTimes(2);
    expect(session.token).toBe('new');
    await session.logout();
  });
  it('does not restore a token after logout during refresh', async () => {
    let resolveRefresh!: (value: AuthToken) => void;
    const pending = new Promise<AuthToken>(resolve => { resolveRefresh = resolve; });
    const request = vi.fn(async (path: string) => path.endsWith('/refresh') ? pending : auth('old'));
    const session = new Session(request as unknown as ConstructorParameters<typeof Session>[0], vi.fn());
    await session.login('13800000000', 'password123');
    session.refreshedAt = Date.now() - 3_600_001;
    const refreshing = session.check();
    await session.logout();
    resolveRefresh(auth('new'));
    await refreshing;
    expect(session.token).toBeNull();
  });
});
