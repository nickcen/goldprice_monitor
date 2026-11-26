import { invoke, isTauri } from '@tauri-apps/api/core';
import type { Config, GoldPrice } from './api/contracts';

export interface PricePoint { minute: string; price: string }
const key = 'gold-price-history-v1';
export function localDay(date = new Date()): string { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
export function localMinute(date = new Date()): string { return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`; }
type Stored = PricePoint & { day: string; currency: string; unit: string; precision: number };
function read(): Stored[] { try { return JSON.parse(localStorage.getItem(key) ?? '[]') as Stored[]; } catch { return []; } }
export async function savePoint(price: GoldPrice, date = new Date()): Promise<void> {
  const point: Stored = { day: localDay(date), minute: localMinute(date), price: String(price.price), currency: price.currency, unit: price.unit, precision: price.precision };
  if (isTauri()) { await invoke('save_price_point', { ...point }); return; }
  const rows = read().filter(row => !(row.day === point.day && row.minute === point.minute && row.currency === point.currency && row.unit === point.unit && row.precision === point.precision));
  rows.push(point); localStorage.setItem(key, JSON.stringify(rows));
}
export async function loadPoints(config: Config, date = new Date()): Promise<PricePoint[]> {
  const query = { day: localDay(date), currency: config.currency, unit: config.unit, precision: config.precision };
  if (isTauri()) return invoke<PricePoint[]>('load_price_points', query);
  return read().filter(row => row.day === query.day && row.currency === query.currency && row.unit === query.unit && row.precision === query.precision).sort((a, b) => a.minute.localeCompare(b.minute)).map(({ minute, price }) => ({ minute, price }));
}
export async function prunePoints(date = new Date()): Promise<void> {
  const day = localDay(date);
  if (isTauri()) { await invoke('prune_price_points', { day }); return; }
  localStorage.setItem(key, JSON.stringify(read().filter(row => row.day === day)));
}

export function chartSvg(points: PricePoint[]): string {
  if (!points.length) return '<p class="chart-empty">当天暂无价格记录</p>';
  const values = points.map(point => Number(point.price));
  if (values.some(value => !Number.isFinite(value))) return '<p class="chart-empty">价格记录无法绘图</p>';
  const min = Math.min(...values), max = Math.max(...values), spread = max - min || 1;
  const minuteValue = (minute: string) => Number(minute.slice(0, 2)) * 60 + Number(minute.slice(3));
  const coordinates = points.map((point, i) => ({ x: 36 + minuteValue(point.minute) / 1439 * 624, y: 152 - (values[i] - min) / spread * 116 }));
  const polyline = coordinates.map(({ x, y }) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const escape = (value: string) => value.replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]!);
  const dots = coordinates.map(({ x, y }, i) => `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${points.length === 1 ? 5 : 3}"><title>${escape(points[i].minute)} · ${escape(points[i].price)}</title></circle>`).join('');
  return `<svg class="price-chart" viewBox="0 0 700 190" role="img" aria-label="当天价格折线图，从 ${escape(points[0].minute)} 到 ${escape(points[points.length - 1].minute)}"><line x1="36" y1="152" x2="660" y2="152"/><polyline points="${polyline}"/>${dots}<text x="36" y="182">00:00</text><text x="618" y="182">23:59</text></svg><div class="chart-range"><span>最低 ${min}</span><span>最高 ${max}</span></div>`;
}
