import './style.css';
import { ApiError, configuredApiClient } from './api/client';
import type { Config, GoldPrice } from './api/contracts';
import { fetchGoldPrice } from './api/goldPrice';
import { Session } from './state/session';
import { Monitor, compareDecimal } from './state/monitor';
import { notify, trayStatus } from './platform/desktop';

const app = document.querySelector<HTMLDivElement>('#app');
if (!app) throw new Error('Missing app element');
const root = app;
let api: ReturnType<typeof configuredApiClient> | null = null;
let setupError = '';
try { api = configuredApiClient(); }
catch { setupError = '尚未配置 API 地址。请在构建时设置 VITE_API_BASE_URL。'; }
let config: Config | null = null;
let price: GoldPrice | undefined;
let priceError: string | null = null;
let configError: string | null = null;
let message = '';
let page: 'login' | 'register' | 'home' = 'login';
const request = <T>(path: string, options: { method?: string; body?: unknown; token?: string } = {}) => {
  if (!api) throw new Error(setupError);
  return api.request<T>(path, options);
};
const session = new Session(request, () => { monitor.stop(); config = null; price = undefined; page = 'login'; void trayStatus('未登录'); render(); });
const monitor = new Monitor(
  () => { if (!config) throw new Error('尚未获取监控配置'); return fetchGoldPrice(config); },
  (nextPrice, error) => { price = nextPrice; priceError = error; void trayStatus(nextPrice ? `${nextPrice.price} ${nextPrice.currency}/${nextPrice.unit}${error ? '（非最新）' : ''}` : '获取中'); render(); },
  (nextPrice, threshold, direction) => { void notify('金价监控提醒', `当前金价 ${nextPrice.price} ${nextPrice.currency}/${nextPrice.unit}，${direction === 'low' ? '低于' : '高于'}阈值 ${threshold}`).catch(() => { message = '系统通知不可用，请检查通知权限'; render(); }); },
);
async function authorized<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  try { return await request<T>(path, { ...options, token: session.token ?? undefined }); }
  catch (error) { if (error instanceof ApiError && error.status === 401) void session.expire(); throw error; }
}
function escape(value: unknown): string { return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!); }
function errorText(error: unknown): string { return error instanceof ApiError && error.response && typeof error.response === 'object' && 'message' in error.response ? String(error.response.message) : error instanceof Error ? error.message : '操作失败'; }
function render(): void {
  if (setupError) { root.innerHTML = `<main class="auth"><span class="eyebrow">GOLDPRICE MONITOR</span><h1>需要配置 API</h1><p role="alert">${escape(setupError)}</p></main>`; return; }
  if (page !== 'home') {
    root.innerHTML = `<main class="auth"><span class="eyebrow">GOLDPRICE MONITOR</span><h1>${page === 'login' ? '登录' : '注册'}</h1><p>实时关注金价变化</p><form id="auth-form"><label>手机号<input name="phone" type="tel" required autocomplete="tel" placeholder="13800138000"></label><label>密码<input name="password" type="password" required minlength="8" autocomplete="${page === 'login' ? 'current-password' : 'new-password'}"></label>${page === 'register' ? '<label>确认密码<input name="confirm" type="password" required minlength="8" autocomplete="new-password"></label>' : ''}<button type="submit">${page === 'login' ? '登录' : '注册'}</button></form><button class="link" id="switch">${page === 'login' ? '没有账号？注册' : '已有账号？登录'}</button><div class="message" role="status">${escape(message)}</div></main>`;
    root.querySelector('#switch')?.addEventListener('click', () => { page = page === 'login' ? 'register' : 'login'; message = ''; render(); });
    root.querySelector<HTMLFormElement>('#auth-form')?.addEventListener('submit', event => void submitAuth(event));
    return;
  }
  root.innerHTML = `<main class="dashboard"><header><div><span class="eyebrow">GOLDPRICE MONITOR</span><h1>金价监控</h1></div><button class="secondary" id="logout">登出</button></header><section class="price-card"><span>当前金价</span><strong>${price ? escape(price.price) : '—'}</strong><small>${config ? `${escape(config.currency)} / ${escape(config.unit)}` : '获取中'}</small><p>${price ? `数据时间 ${escape(new Date(price.dataTime).toLocaleString())}` : '等待首个价格'}</p><p class="${priceError ? 'error' : ''}">${escape(priceError ?? (price ? '监控中' : '获取中'))}</p></section><section class="settings"><h2>监控设置</h2>${config ? `<p>等级 ${escape(config.level)} · 每 ${escape(config.monitorIntervalSeconds)} 秒检查 · 精度 ${escape(config.precision)} 位小数</p><p class="url">数据接口：${escape(config.goldPriceUrl)}</p><form id="config-form"><label>低价提醒<input name="min" inputmode="decimal" placeholder="不设置" value="${escape(config.minPrice)}"></label><label>高价提醒<input name="max" inputmode="decimal" placeholder="不设置" value="${escape(config.maxPrice)}"></label><button type="submit">保存阈值</button></form>` : `<p class="error">${escape(configError ?? '正在获取配置')}</p><button id="retry">重试</button>`}<div class="message" role="status">${escape(message)}</div></section></main>`;
  root.querySelector('#logout')?.addEventListener('click', () => void session.logout());
  root.querySelector('#retry')?.addEventListener('click', () => void loadConfig());
  root.querySelector<HTMLFormElement>('#config-form')?.addEventListener('submit', event => void saveConfig(event));
}
async function submitAuth(event: Event): Promise<void> {
  event.preventDefault();
  const form = event.currentTarget as HTMLFormElement;
  const data = new FormData(form);
  const phone = String(data.get('phone') ?? '').trim(); const password = String(data.get('password') ?? '');
  if (!/^(?:1[3-9]\d{9}|\+[1-9]\d{7,14})$/.test(phone)) { message = '手机号格式无效'; render(); return; }
  if (password.length < 8 || password.length > 72) { message = '密码长度应为 8 到 72 位'; render(); return; }
  if (page === 'register' && password !== data.get('confirm')) { message = '两次密码不一致'; render(); return; }
  try {
    if (page === 'register') { await request('/api/v1/auth/register', { method: 'POST', body: { phone, password } }); page = 'login'; message = '注册成功，请登录'; }
    else { await session.login(phone, password); page = 'home'; message = ''; await loadConfig(); }
  } catch (error) { message = errorText(error); }
  render();
}
async function loadConfig(): Promise<void> {
  try { config = await authorized<Config>('/api/v1/me/config'); configError = null; monitor.start(config); }
  catch (error) { config = null; monitor.stop(); configError = errorText(error); }
  render();
}
async function saveConfig(event: Event): Promise<void> {
  event.preventDefault();
  if (!config) return;
  const data = new FormData(event.currentTarget as HTMLFormElement);
  const min = String(data.get('min') ?? '').trim(), max = String(data.get('max') ?? '').trim();
  const valid = (value: string) => value === '' || (/^\d+(?:\.\d{1,4})?$/.test(value) && value.replace('.', '').length <= 18);
  if (!valid(min) || !valid(max) || (min && max && compareDecimal(min, max) >= 0)) { message = '价格须为非负、最多四位小数，且低价小于高价'; render(); return; }
  try { await authorized('/api/v1/me/config', { method: 'PATCH', body: { minPrice: min || null, maxPrice: max || null } }); message = '已保存'; await loadConfig(); }
  catch (error) { message = errorText(error); render(); }
}
window.addEventListener('focus', () => void resume());
document.addEventListener('visibilitychange', () => { if (!document.hidden) void resume(); });
async function resume(): Promise<void> { await session.check(); if (session.token && page === 'home') await loadConfig(); }
void trayStatus('未登录');
render();
if (!setupError) void session.restore().then(ok => { if (ok) { page = 'home'; void loadConfig(); } else render(); }).catch(() => { message = '无法读取系统凭据，请检查钥匙串权限'; render(); });
