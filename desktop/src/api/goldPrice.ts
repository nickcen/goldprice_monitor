import { invoke, isTauri } from '@tauri-apps/api/core';
import type { Config, GoldPrice } from './contracts';

export async function fetchGoldPrice(config: Config): Promise<GoldPrice> {
  const url = new URL(config.goldPriceUrl);
  if (url.protocol !== 'http:' && url.protocol !== 'https:') throw new Error('金价数据接口必须使用 HTTP 或 HTTPS');
  const text = isTauri()
    ? await invoke<string>('fetch_gold_price', { url: url.href })
    : await (async () => {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`金价数据接口返回 ${response.status}`);
        return response.text();
      })();
  let body: unknown;
  try { body = JSON.parse(text); }
  catch { throw new Error('金价数据接口返回无效 JSON'); }
  if (!body || typeof body !== 'object') throw new Error('金价数据格式无效');
  const data = body as Record<string, unknown>;
  const coinApi = 'status' in data && 'data' in data;
  if (coinApi && (data.status !== true || data.code !== 1)) throw new Error('金价数据源返回失败');
  const rawPrice = coinApi ? data.data : data.price;
  if ((typeof rawPrice !== 'string' && typeof rawPrice !== 'number') ||
      !/^\d+(?:\.\d{1,4})?$/.test(String(rawPrice))) throw new Error('金价数据格式无效');
  const dataTime = coinApi ? new Date().toISOString() : data.dataTime;
  if (typeof dataTime !== 'string' || Number.isNaN(Date.parse(dataTime))) throw new Error('金价数据时间无效');
  return { price: String(rawPrice), unit: config.unit, currency: config.currency,
    precision: config.precision, dataTime };
}
