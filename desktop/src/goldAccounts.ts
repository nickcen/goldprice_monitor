import { ApiError } from './api/client';

type Account = { id: number; username: string; isOnline: boolean };
type Request = <T>(path: string, options?: { method?: string; body?: unknown }) => Promise<T>;
const escape = (value: unknown): string => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
const errorText = (e: unknown): string => e instanceof ApiError && e.response && typeof e.response === 'object' && 'message' in e.response ? String(e.response.message) : e instanceof Error ? e.message : '操作失败';

export class GoldAccountsPage {
  private rows: Account[] = [];
  private message = '';
  private editing: number | null = null;
  private loginId: number | null = null;
  private loginMode: 'sms' | 'password' = 'sms';
  private captcha: { base64str: string; cachkey: string } | null = null;
  private busy = false;
  constructor(private root: HTMLElement, private request: Request) {}
  async load(): Promise<void> {
    try { this.rows = await this.request<Account[]>('/api/v1/me/gold-accounts'); this.message = ''; }
    catch (e) { this.message = errorText(e); }
    this.render();
  }
  private render(): void {
    const selected = this.rows.find(row => row.id === this.editing);
    const captchaSrc = this.captcha?.base64str.startsWith('data:image/') ? this.captcha.base64str : `data:image/png;base64,${this.captcha?.base64str ?? ''}`;
    this.root.innerHTML = `<section class="settings gold-accounts"><h2>Gold账号</h2><p role="status">${escape(this.message)}</p><form id="gold-form"><label>账号名<input name="username" required maxlength="120" value="${escape(selected?.username ?? '')}"></label><label>密码（明文保存）<input name="password" type="password" maxlength="255" autocomplete="off" placeholder="${selected ? '留空则不修改' : '可选'}"></label><button type="submit">${selected ? '保存修改' : '新增账号'}</button>${selected ? '<button type="button" id="gold-cancel" class="secondary">取消</button>' : ''}</form><div class="gold-list">${this.rows.map(row => `<div class="gold-row"><span>${escape(row.username)} · ${row.isOnline ? '在线' : '未在线'}</span><div><button type="button" class="secondary" data-edit="${row.id}">编辑</button>${row.isOnline ? '' : `<button type="button" data-login="${row.id}">登录</button>`}<button type="button" class="secondary" data-delete="${row.id}">删除</button></div></div>`).join('') || '<p>暂无账号</p>'}</div></section>${this.loginId === null ? '' : `<div class="gold-modal-backdrop"><section class="gold-modal" role="dialog" aria-modal="true" aria-label="Gold账号登录"><h2>Gold账号登录</h2><div><button type="button" id="gold-mode-sms" class="secondary">短信登录</button><button type="button" id="gold-mode-password" class="secondary">账号密码登录</button></div><form id="gold-login">${this.loginMode === 'sms' ? '<label>手机号<input name="mobile" type="tel" required></label>' : `<p>账号：${escape(this.rows.find(row => row.id === this.loginId)?.username)}</p>${this.captcha ? `<img alt="图形验证码" src="${escape(captchaSrc)}">` : ''}<button type="button" id="gold-refresh" class="secondary">刷新验证码</button>`}<label>验证码<input name="code" required></label><div>${this.loginMode === 'sms' ? '<button type="button" id="gold-sms" class="secondary">获取验证码</button>' : ''}<button type="submit">登录</button><button type="button" id="gold-close" class="secondary">取消</button></div></form><p id="gold-login-message" role="status">${escape(this.message)}</p></section></div>`}`;
    this.root.querySelector<HTMLFormElement>('#gold-form')?.addEventListener('submit', e => void this.save(e));
    this.root.querySelector('#gold-cancel')?.addEventListener('click', () => { this.editing = null; this.render(); });
    this.root.querySelectorAll<HTMLButtonElement>('[data-edit]').forEach(b => b.addEventListener('click', () => { this.editing = Number(b.dataset.edit); this.render(); }));
    this.root.querySelectorAll<HTMLButtonElement>('[data-delete]').forEach(b => b.addEventListener('click', () => void this.remove(Number(b.dataset.delete))));
    this.root.querySelectorAll<HTMLButtonElement>('[data-login]').forEach(b => b.addEventListener('click', () => { this.loginId = Number(b.dataset.login); this.loginMode = 'sms'; this.captcha = null; this.message = ''; this.render(); }));
    this.root.querySelector('#gold-close')?.addEventListener('click', () => { this.loginId = null; this.captcha = null; this.render(); });
    this.root.querySelector('#gold-mode-sms')?.addEventListener('click', () => { this.loginMode = 'sms'; this.captcha = null; this.message = ''; this.render(); });
    this.root.querySelector('#gold-mode-password')?.addEventListener('click', () => { this.loginMode = 'password'; this.captcha = null; this.message = ''; this.render(); void this.refreshCaptcha(); });
    this.root.querySelector('#gold-refresh')?.addEventListener('click', () => void this.refreshCaptcha());
    this.root.querySelector('#gold-sms')?.addEventListener('click', () => void this.sendSms());
    this.root.querySelector<HTMLFormElement>('#gold-login')?.addEventListener('submit', e => void this.login(e));
  }
  private async save(e: Event): Promise<void> {
    e.preventDefault(); if (this.busy) return; this.busy = true;
    const data = new FormData(e.currentTarget as HTMLFormElement), username = String(data.get('username') ?? '').trim(), password = String(data.get('password') ?? '');
    try { await this.request(this.editing === null ? '/api/v1/me/gold-accounts' : `/api/v1/me/gold-accounts/${this.editing}`, { method: this.editing === null ? 'POST' : 'PATCH', body: this.editing === null || password ? { username, password } : { username } }); this.editing = null; await this.load(); this.message = '已保存'; }
    catch (err) { this.message = errorText(err); }
    finally { this.busy = false; this.render(); }
  }
  private async remove(id: number): Promise<void> {
    if (this.busy || !window.confirm('确定删除此Gold账号？')) return; this.busy = true;
    try { await this.request(`/api/v1/me/gold-accounts/${id}`, { method: 'DELETE' }); await this.load(); }
    catch (e) { this.message = errorText(e); }
    finally { this.busy = false; this.render(); }
  }
  private loginForm(): { mobile: string; code: string } {
    const data = new FormData(this.root.querySelector<HTMLFormElement>('#gold-login')!);
    return { mobile: String(data.get('mobile') ?? '').trim(), code: String(data.get('code') ?? '').trim() };
  }
  private loginMessage(): void { const node = this.root.querySelector('#gold-login-message'); if (node) node.textContent = this.message; }
  private async refreshCaptcha(): Promise<void> {
    if (this.busy || this.loginId === null) return;
    const id = this.loginId; this.busy = true; this.captcha = null;
    try { const captcha = await this.request<{ base64str: string; cachkey: string }>(`/api/v1/me/gold-accounts/${id}/password-code`); if (this.loginId === id && this.loginMode === 'password') { this.captcha = captcha; this.message = ''; this.render(); } }
    catch (e) { this.message = errorText(e); this.loginMessage(); }
    finally { this.busy = false; }
  }
  private async sendSms(): Promise<void> {
    if (this.busy || this.loginId === null) return;
    const { mobile } = this.loginForm(); if (!/^(?:1[3-9]\d{9}|\+[1-9]\d{7,14})$/.test(mobile)) { this.message = '手机号格式无效'; this.loginMessage(); return; }
    this.busy = true;
    try { await this.request(`/api/v1/me/gold-accounts/${this.loginId}/send-sms`, { method: 'POST', body: { mobile } }); this.message = '验证码已发送'; }
    catch (e) { this.message = errorText(e); }
    finally { this.busy = false; this.loginMessage(); }
  }
  private async login(e: Event): Promise<void> {
    e.preventDefault(); if (this.busy || this.loginId === null) return; this.busy = true;
    try {
      if (this.loginMode === 'password' && !this.captcha) { this.message = '请先获取图形验证码'; this.loginMessage(); return; }
      const path = this.loginMode === 'sms' ? 'login' : 'password-login';
      const body = this.loginMode === 'sms' ? this.loginForm() : { code: this.loginForm().code, cachkey: this.captcha!.cachkey };
      await this.request(`/api/v1/me/gold-accounts/${this.loginId}/${path}`, { method: 'POST', body }); this.loginId = null; this.captcha = null; await this.load(); this.message = '登录成功'; this.render();
    }
    catch (err) { this.message = errorText(err); this.loginMessage(); }
    finally { this.busy = false; }
  }
}
