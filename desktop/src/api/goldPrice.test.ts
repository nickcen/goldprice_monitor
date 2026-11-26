import { afterEach, expect, it, vi } from 'vitest';
import { fetchGoldPrice } from './goldPrice';
import type { Config } from './contracts';

const config: Config = { level: 0, monitorIntervalSeconds: 60, minPrice: null, maxPrice: null,
  goldPriceUrl: 'https://coinapi.gcsgcs.com/api/Accumulate/GetRealTimeGoldPrice',
  currency: 'CNY', unit: 'gram', precision: 4 };

afterEach(() => vi.unstubAllGlobals());

it('requests the configured source and parses its price', async () => {
  const fetch = vi.fn().mockResolvedValue({ ok: true, text: async () => '{"status":true,"code":1,"data":768.48}' });
  vi.stubGlobal('fetch', fetch);
  const result = await fetchGoldPrice(config);
  expect(fetch).toHaveBeenCalledWith(new URL(config.goldPriceUrl));
  expect(result.price).toBe('768.48');
  expect(result.currency).toBe('CNY');
  expect(Date.parse(result.dataTime)).not.toBeNaN();
});

it('rejects a failed source response', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => '{"status":false,"code":0,"data":0}' }));
  await expect(fetchGoldPrice(config)).rejects.toThrow('金价数据源返回失败');
});
