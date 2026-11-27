import './style.css';
import { ApiError, configuredApiClient } from './api/client';
import type { Config, GoldPrice } from './api/contracts';
import { fetchGoldPrice } from './api/goldPrice';
import { Session } from './state/session';
import { Monitor, compareDecimal } from './state/monitor';
import { notify, trayStatus } from './platform/desktop';
import { chartSvg, loadPoints, localDay, localMinute, prunePoints, saveServerPoints, type PricePoint, type ServerPoint } from './history';

const app = document.querySelector<HTMLDivElement>('#app');
if (!app) throw new Error('Missing app element');
const root = app;
let api: ReturnType<typeof configuredApiClient> | null = null;
let setupError = '';
try { api = configuredApiClient(); }
catch { setupError = '尚未配置 API 地址。请在构建时设置 VITE_API_BASE_URL。'; }
let config: Config | null = null;
let configRequest = 0;
let price: GoldPrice | undefined;
let priceError: string | null = null;
let trayUpdate = 0;
let points: PricePoint[] = [];
let historyError = '';
let historyDay = localDay();
let configError: string | null = null;
let message = '';
let savingConfig = false;
let configDraft: { min: string; max: string } | null = null;
let page: 'restoring' | 'login' | 'register' | 'home' | 'account' = setupError ? 'login' : 'restoring';
const request = <T>(path: string, options: { method?: string; body?: unknown; token?: string } = {}) => {
  if (!api) throw new Error(setupError);
  return api.request<T>(path, options);
};
const session = new Session(request, () => { configRequest++; trayUpdate++; monitor.stop(); config = null; configDraft = null; price = undefined; points = []; page = 'login'; void trayStatus('未登录'); render(); });
const monitor = new Monitor(
  () => { if (!config) throw new Error('尚未获取监控配置'); return fetchGoldPrice(config, path => authorized<GoldPrice>(path)); },
  (nextPrice, error) => { price = nextPrice; priceError = error; void updateTray(nextPrice, error); if (page === 'home') render(); },
  (nextPrice, threshold, direction) => { void notify('金价监控提醒', `当前金价 ${nextPrice.price} ${nextPrice.currency}/${nextPrice.unit}，${direction === 'low' ? '低于' : '高于'}阈值 ${formatPrice(threshold)}`).catch(() => { message = '系统通知不可用，请检查通知权限'; render(); }); },
);
async function updateTray(nextPrice: GoldPrice | undefined, error: string | null): Promise<void> {
  const update = ++trayUpdate;
  if (!nextPrice) { await trayStatus('获取中'); return; }
  let trend: 'up' | 'down' | null = null;
  if (!error && config) {
    try {
      const now = new Date();
      const previous = new Date(now.getTime() - 60_000);
      const rows = await loadPoints(config, previous);
      const point = rows.find(row => row.minute === localMinute(previous));
      if (point) {
        const comparison = compareDecimal(nextPrice.price, point.price);
        trend = comparison > 0 ? 'up' : comparison < 0 ? 'down' : null;
      }
    } catch { /* Keep the price visible if history is unavailable. */ }
  }
  if (update === trayUpdate) await trayStatus(`${formatPrice(nextPrice.price)}${error ? '（非最新）' : ''}`, trend);
}
async function refreshHistory(): Promise<void> {
  if (!config) return;
  const day = localDay();
  try { points = await loadPoints(config); historyDay = day; historyError = ''; }
  catch (error) { historyError = errorText(error); }
  if (page === 'home') render();
}
async function maintainHistory(): Promise<void> {
  try { await prunePoints(); if (historyDay !== localDay()) points = []; await syncToday(); }
  catch (error) { historyError = errorText(error); if (page === 'home') render(); }
  await refreshHistory();
}
async function syncToday(): Promise<void> {
  if (!config) return;
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const end = new Date(start.getTime() + 24 * 60 * 60 * 1000 - 1);
  const beijingDay = (value: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(value);
  const days = [...new Set([beijingDay(start), beijingDay(end)])];
  const rows = await Promise.all(days.map(async date => ({ date, points: await authorized<ServerPoint[]>(`/api/v1/gold-price/day?date=${date}`) })));
  await saveServerPoints(rows, config, now);
}
globalThis.setInterval(() => void maintainHistory(), 60 * 60 * 1000);
void prunePoints().catch(error => { historyError = errorText(error); });
async function authorized<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  try { return await request<T>(path, { ...options, token: session.token ?? undefined }); }
  catch (error) { if (error instanceof ApiError && error.status === 401) void session.expire(); throw error; }
}
function escape(value: unknown): string { return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!); }
function formatPrice(value: string | number): string { return Number(value).toFixed(2); }
function formatDataTime(value: string): string {
  const date = new Date(value);
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
function errorText(error: unknown): string { return error instanceof ApiError && error.response && typeof error.response === 'object' && 'message' in error.response ? String(error.response.message) : error instanceof Error ? error.message : '操作失败'; }
function render(): void {
  if (setupError) { root.innerHTML = `<main class="auth"><span class="eyebrow">GOLDPRICE MONITOR</span><h1>需要配置 API</h1><p role="alert">${escape(setupError)}</p></main>`; return; }
  if (page === 'restoring') { root.innerHTML = '<main class="auth"><span class="eyebrow">GOLDPRICE MONITOR</span><h1>正在恢复登录…</h1></main>'; return; }
  if (page === 'login' || page === 'register') {
    root.innerHTML = `<main class="auth"><span class="eyebrow">GOLDPRICE MONITOR</span><h1>${page === 'login' ? '登录' : '注册'}</h1><p>实时关注金价变化</p><form id="auth-form"><label>手机号<input name="phone" type="tel" required autocomplete="tel" placeholder="13800138000"></label><label>密码<input name="password" type="password" required minlength="8" autocomplete="${page === 'login' ? 'current-password' : 'new-password'}"></label>${page === 'register' ? '<label>确认密码<input name="confirm" type="password" required minlength="8" autocomplete="new-password"></label>' : ''}<button type="submit">${page === 'login' ? '登录' : '注册'}</button></form><button class="link" id="switch">${page === 'login' ? '没有账号？注册' : '已有账号？登录'}</button><div class="message" role="status">${escape(message)}</div></main>`;
    root.querySelector('#switch')?.addEventListener('click', () => { page = page === 'login' ? 'register' : 'login'; message = ''; render(); });
    root.querySelector<HTMLFormElement>('#auth-form')?.addEventListener('submit', event => void submitAuth(event));
    return;
  }
  const home = page === 'home';
  root.innerHTML = `<main class="app-shell"><nav class="sidebar" aria-label="主导航"><div class="brand-mark" aria-hidden="true">◆</div><button class="nav-item ${home ? 'active' : ''}" id="nav-home" aria-current="${home ? 'page' : 'false'}"><span aria-hidden="true">⌂</span>首页</button><button class="nav-item ${!home ? 'active' : ''}" id="nav-account" aria-current="${!home ? 'page' : 'false'}"><span aria-hidden="true">◉</span>账户</button></nav><div class="content"><header class="page-header"><div><span class="eyebrow">GOLDPRICE MONITOR</span><h1>${home ? '首页' : '账户'}</h1></div><button class="secondary" id="logout">登出</button></header>${home ? `<section class="price-card"><span>当前金价</span><strong>${price ? escape(formatPrice(price.price)) : '—'}</strong><small>${config ? `${escape(config.currency)} / ${escape(config.unit)}` : '获取中'}</small><p>${price ? `数据时间 ${escape(formatDataTime(price.dataTime))}` : '等待首个价格'}</p><p class="${priceError ? 'error' : ''}">${escape(priceError ? (price ? `${priceError}（显示上次成功价格，非最新）` : priceError) : (price ? '监控中' : '获取中'))}</p>${message ? `<p class="error" role="alert">${escape(message)}</p>` : ''}</section><section class="chart-card"><h2>今日金价走势</h2><p>${escape(config ? `${config.currency} / ${config.unit}` : '获取中')} · ${escape(localDay())}</p>${historyError ? `<p class="error" role="alert">历史记录：${escape(historyError)}</p>` : chartSvg(historyDay === localDay() ? points : [])}</section>` : `<section class="settings"><div class="section-heading"><div><span class="eyebrow">ACCOUNT SETTINGS</span><h2>监控配置</h2></div><button class="secondary" id="refresh-config" type="button" ${savingConfig ? 'disabled' : ''}>重新获取用户配置</button></div>${config ? `<div class="config-grid"><div><span>用户等级</span><strong>${escape(config.level)}</strong></div><div><span>监控频率</span><strong>每 ${escape(config.monitorIntervalSeconds)} 秒</strong></div></div><form id="config-form"><label>低价提醒<input name="min" inputmode="decimal" placeholder="不设置" value="${escape(configDraft?.min ?? (config.minPrice == null ? '' : formatPrice(config.minPrice)))}"></label><label>高价提醒<input name="max" inputmode="decimal" placeholder="不设置" value="${escape(configDraft?.max ?? (config.maxPrice == null ? '' : formatPrice(config.maxPrice)))}"></label><button type="submit" ${savingConfig ? 'disabled' : ''}>${savingConfig ? '保存中…' : '保存阈值'}</button></form>${configError ? `<p class="error" role="alert">配置刷新失败：${escape(configError)}</p><button id="retry">重试</button>` : ''}` : `<p class="error">${escape(configError ?? '正在获取配置')}</p><button id="retry">重试</button>`}<div class="message" role="status">${escape(message)}</div></section>`}</div></main>`;
  root.querySelector('#nav-home')?.addEventListener('click', () => { if (page !== 'home') { page = 'home'; render(); } });
  root.querySelector('#nav-account')?.addEventListener('click', () => { if (page !== 'account') { page = 'account'; render(); } });
  root.querySelector('#logout')?.addEventListener('click', () => void session.logout());
  root.querySelector('#retry')?.addEventListener('click', () => void loadConfig());
  root.querySelector<HTMLButtonElement>('#refresh-config')?.addEventListener('click', event => {
    const button = event.currentTarget as HTMLButtonElement;
    button.disabled = true;
    button.textContent = '获取中…';
    configDraft = null;
    void loadConfig().then(() => { message = configError ? '重新获取用户配置失败' : '已获取最新用户配置'; render(); });
  });
  const configForm = root.querySelector<HTMLFormElement>('#config-form');
  configForm?.addEventListener('input', () => { const data = new FormData(configForm); configDraft = { min: String(data.get('min') ?? ''), max: String(data.get('max') ?? '') }; });
  configForm?.addEventListener('submit', event => void saveConfig(event));
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
  const requestId = ++configRequest;
  try {
    const next = await authorized<Config>('/api/v1/me/config');
    if (requestId !== configRequest || !session.token) return;
    if (!Number.isSafeInteger(next.monitorIntervalSeconds) || next.monitorIntervalSeconds <= 0) throw new Error('监控频次必须为正整数秒');
    const changed = !config || next.monitorIntervalSeconds !== config.monitorIntervalSeconds || next.unit !== config.unit || next.currency !== config.currency || next.precision !== config.precision || next.minPrice !== config.minPrice || next.maxPrice !== config.maxPrice;
    config = next;
    configError = null;
    void maintainHistory();
    if (changed) monitor.start(next);
  }
  catch (error) { if (requestId !== configRequest) return; configError = errorText(error); }
  render();
}
async function saveConfig(event: Event): Promise<void> {
  event.preventDefault();
  if (!config || savingConfig) return;
  const data = new FormData(event.currentTarget as HTMLFormElement);
  const min = String(data.get('min') ?? '').trim(), max = String(data.get('max') ?? '').trim();
  const valid = (value: string) => value === '' || (/^\d+(?:\.\d{1,2})?$/.test(value) && value.replace('.', '').length <= 18);
  if (!valid(min) || !valid(max) || (min && max && compareDecimal(min, max) >= 0)) { message = '价格须为非负、最多两位小数，且低价小于高价'; render(); return; }
  savingConfig = true;
  message = '正在保存阈值…';
  render();
  try {
    const saved = await authorized<Config>('/api/v1/me/config', { method: 'PATCH', body: { minPrice: min || null, maxPrice: max || null } });
    if (!session.token) return;
    const matches = (actual: string | number | null, expected: string) => expected === '' ? actual == null : actual != null && compareDecimal(actual, expected) === 0;
    if (!matches(saved.minPrice, min) || !matches(saved.maxPrice, max)) throw new Error('服务器返回的阈值与输入不一致，请重试');
    configRequest++;
    config = saved;
    configDraft = null;
    configError = null;
    monitor.start(saved);
    message = '阈值已保存';
  } catch (error) { message = `保存失败：${errorText(error)}`; }
  finally { savingConfig = false; render(); }
}
window.addEventListener('focus', () => void resume());
document.addEventListener('visibilitychange', () => { if (!document.hidden) void resume(); });
async function resume(): Promise<void> { await session.check(); if (session.token && (page === 'home' || page === 'account')) { await loadConfig(); await maintainHistory(); } }
void trayStatus('未登录');
render();
if (!setupError) void session.restore().then(ok => { page = ok ? 'home' : 'login'; if (ok) void loadConfig(); else render(); }).catch(() => { page = 'login'; message = '无法读取系统凭据，请检查钥匙串权限'; render(); });
