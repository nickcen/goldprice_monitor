import { beforeEach, expect, it, vi } from 'vitest';
import { chartSvg, loadPoints, prunePoints, savePoint, saveServerPoints } from './history';
import type { Config, GoldPrice } from './api/contracts';

const data = new Map<string, string>();
beforeEach(() => {
  data.clear();
  vi.stubGlobal('localStorage', { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => data.set(key, value) });
});
const config = { currency: 'CNY', unit: 'gram', precision: 4 } as Config;
const price = (value: string): GoldPrice => ({ price: value, currency: 'CNY', unit: 'gram', precision: 4, dataTime: '' });

it('keeps the last successful price per local minute and reads in time order', async () => {
  await savePoint(price('501'), new Date(2026, 10, 26, 10, 5, 1));
  await savePoint(price('502'), new Date(2026, 10, 26, 10, 5, 50));
  await savePoint(price('500'), new Date(2026, 10, 26, 9, 59));
  expect(await loadPoints(config, new Date(2026, 10, 26))).toEqual([{ minute: '09:59', price: '500' }, { minute: '10:05', price: '502' }]);
});

it('filters configuration and removes other days', async () => {
  await savePoint(price('500'), new Date(2026, 10, 25, 23, 59));
  await savePoint(price('501'), new Date(2026, 10, 26, 0, 0));
  expect(await loadPoints({ ...config, unit: 'ounce' }, new Date(2026, 10, 26))).toEqual([]);
  await prunePoints(new Date(2026, 10, 26));
  expect(JSON.parse(data.get('gold-price-history-v1') ?? '[]')).toHaveLength(1);
});

it('replaces local day with server Beijing minutes and removes stale points', async () => {
  const instant = new Date(2026, 10, 26, 10, 0);
  const beijing = new Date(instant.getTime() + 8 * 60 * 60 * 1000);
  const beijingDay = beijing.toISOString().slice(0, 10);
  const minute = beijing.getUTCHours() * 60 + beijing.getUTCMinutes();
  await saveServerPoints([{ date: beijingDay, points: [{ minute, price: 500 }, { minute: 0, price: 400 }] }], config, instant);
  await saveServerPoints([{ date: beijingDay, points: [{ minute, price: 501 }] }], config, instant);
  expect(await loadPoints(config, instant)).toEqual([{ minute: '10:00', price: '501' }]);
});

it('renders empty, single point and multi point chart states', () => {
  expect(chartSvg([])).toContain('暂无价格记录');
  expect(chartSvg([{ minute: '10:00', price: '500' }])).not.toContain('<circle');
  expect(chartSvg([{ minute: '10:00', price: '500' }, { minute: '11:00', price: '510' }])).toContain('<polyline');
  expect(chartSvg([{ minute: '10:00', price: '500' }, { minute: '11:00', price: '510' }])).not.toContain('<circle');
});

it('spreads recorded times across the chart and labels the actual range', () => {
  const svg = chartSvg([{ minute: '09:30', price: '767' }, { minute: '16:40', price: '768' }, { minute: '23:50', price: '767.5' }]);
  expect(svg).toContain('points="76.0,152.0 368.0,36.0 660.0,94.0"');
  expect(svg).toContain('>768.00</text>');
  expect(svg).toContain('>767.50</text>');
  expect(svg).toContain('>767.00</text>');
  expect(svg).toContain('>09:30</text>');
  expect(svg).toContain('>23:50</text>');
});
