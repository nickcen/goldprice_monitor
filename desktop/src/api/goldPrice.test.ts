import { expect, it, vi } from 'vitest';
import { fetchGoldPrice } from './goldPrice';
import type { Config } from './contracts';

const config: Config = { level: 0, monitorIntervalSeconds: 60, minPrice: null, maxPrice: null,
  currency: 'CNY', unit: 'gram', precision: 2 };

it('requests the server for the current instant', async () => {
  const request = vi.fn().mockResolvedValue({ price: '768.48', unit: 'gram', currency: 'CNY', precision: 2, dataTime: '2026-11-26T14:23:00Z' });
  const result = await fetchGoldPrice(config, request, new Date('2026-11-26T14:23:42Z'));
  expect(request).toHaveBeenCalledWith('/api/v1/gold-price/at?time=2026-11-26T14%3A23%3A42.000Z');
  expect(result.price).toBe('768.48');
});

it('rejects malformed server prices', async () => {
  await expect(fetchGoldPrice(config, async () => ({ price: '-1', unit: 'gram', currency: 'CNY', precision: 2, dataTime: '2026-11-26T14:23:00Z' }))).rejects.toThrow('金价数据格式无效');
});
