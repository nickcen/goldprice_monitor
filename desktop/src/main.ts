import './style.css';
import { showAlert } from './ui';
import { createApp, h, reactive } from 'vue';
import 'element-plus/dist/index.css';
import App from './App.vue';
import { ApiError, configuredApiClient } from './api/client';
import type { Config, GoldPrice } from './api/contracts';
import { fetchGoldPrice } from './api/goldPrice';
import { Session } from './state/session';
import { Monitor, compareDecimal } from './state/monitor';
import { notify, trayStatus } from './platform/desktop';
import { loadPoints, localDay, localMinute, prunePoints, saveServerPoints, type PricePoint, type ServerPoint } from './history';

const root = document.querySelector<HTMLDivElement>('#app');
if (!root) throw new Error('Missing app element');
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
let page: 'restoring' | 'login' | 'register' | 'home' | 'account' | 'gold' | 'grabcoin' = setupError ? 'login' : 'restoring';
const request = <T>(path: string, options: { method?: string; body?: unknown; token?: string } = {}) => {
  if (!api) throw new Error(setupError);
  return api.request<T>(path, options);
};
const session = new Session(request, () => { configRequest++; trayUpdate++; monitor.stop(); config = null; configDraft = null; price = undefined; points = []; page = 'login'; void trayStatus('未登录'); render(); });
const monitor = new Monitor(
  () => { if (!config) throw new Error('尚未获取监控配置'); return fetchGoldPrice(config, path => authorized<GoldPrice>(path)); },
  (nextPrice, error) => { price = nextPrice; priceError = error; void updateTray(nextPrice, error); if (page === 'home') render(); },
  (nextPrice, threshold, direction) => {
    const body = `当前金价 ${nextPrice.price} ${nextPrice.currency}/${nextPrice.unit}，${direction === 'low' ? '低于' : '高于'}阈值 ${formatPrice(threshold)}`;
    showPriceAlert(body);
    void notify('金价监控提醒', body).catch(error => { message = `系统通知不可用：${errorText(error)}`; render(); });
  },
);
function showPriceAlert(body: string): void {
  if (!document.hidden) showAlert(`金价监控提醒：${body}`);
}
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
function formatPrice(value: string | number): string { return Number(value).toFixed(2); }
function errorText(error: unknown): string { return error instanceof ApiError && error.response && typeof error.response === 'object' && 'message' in error.response ? String(error.response.message) : error instanceof Error ? error.message : '操作失败'; }
const view = reactive({ page, setupError, config, configError, price, priceError, message, points, historyDay, historyError, savingConfig, configDraft });
function render(): void { Object.assign(view, { page, config, configError, price, priceError, message, points, historyDay, historyError, savingConfig, configDraft }); }
function switchAuth(): void { page = page === 'login' ? 'register' : 'login'; message = ''; render(); }
function navigate(next: 'home' | 'account' | 'gold' | 'grabcoin'): void { page = next; render(); }
function refreshConfig(): void { configDraft = null; void loadConfig().then(() => { message = configError ? '重新获取用户配置失败' : '已获取最新用户配置'; render(); }); }
const actions = {
  switchAuth,
  submitAuth: (phone: string, password: string, confirm: string) => void submitAuth(phone, password, confirm),
  navigate,
  logout: () => void session.logout(),
  retry: () => void loadConfig(),
  refresh: refreshConfig,
  save: (min: string, max: string) => void saveConfig(min, max),
  draft: (min: string, max: string) => { configDraft = { min, max }; },
  request: <T>(path: string, options: { method?: string; body?: unknown } = {}) => authorized<T>(path, options),
};
createApp({ render: () => h(App, { state: view, actions }) }).mount(root);
async function submitAuth(rawPhone: string, password: string, confirm: string): Promise<void> {
  const phone = rawPhone.trim();
  if (!/^(?:1[3-9]\d{9}|\+[1-9]\d{7,14})$/.test(phone)) { message = '手机号格式无效'; render(); return; }
  if (password.length < 8 || password.length > 72) { message = '密码长度应为 8 到 72 位'; render(); return; }
  if (page === 'register' && password !== confirm) { message = '两次密码不一致'; render(); return; }
  try {
    if (page === 'register') { await request('/api/v1/auth/register', { method: 'POST', body: { phone, password } }); page = 'login'; message = '注册成功，请登录'; }
    else { await session.login(phone, password); page = 'home'; message = ''; await loadConfig(); }
  } catch (error) { message = errorText(error); }
  render();
}
async function loadConfig(updateAccountView = true): Promise<void> {
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
  // A focus-triggered refresh can finish between pointerdown and click. Replacing
  // the form then discards the pending submit event and the user's input.
  if (updateAccountView || (page !== 'account' && page !== 'gold' && page !== 'grabcoin')) render();
}
async function saveConfig(rawMin: string, rawMax: string): Promise<void> {
  if (!config || savingConfig) return;
  const min = rawMin.trim(), max = rawMax.trim();
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
async function resume(): Promise<void> { await session.check(); if (session.token && (page === 'home' || page === 'account' || page === 'gold' || page === 'grabcoin')) { await loadConfig(false); await maintainHistory(); } }
void trayStatus('未登录');
render();
if (!setupError) void session.restore().then(ok => { page = ok ? 'home' : 'login'; if (ok) void loadConfig(); else render(); }).catch(() => { page = 'login'; message = '无法读取系统凭据，请检查钥匙串权限'; render(); });
