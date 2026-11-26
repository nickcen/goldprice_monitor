import { describe, expect, it, vi } from 'vitest';
import { compareDecimal, Monitor } from './monitor';
import type { Config, GoldPrice } from '../api/contracts';

const config: Config = { level: 0, monitorIntervalSeconds: 1, minPrice: '100.0000', maxPrice: '200.0000', goldPriceUrl: '/api/v1/gold-price/latest', currency: 'CNY', unit: 'gram', precision: 4 };
const quote = (price: string): GoldPrice => ({ price, currency: 'CNY', unit: 'gram', precision: 4, dataTime: '2026-11-26T00:00:00Z' });
const flush = async () => { await Promise.resolve(); await Promise.resolve(); };

describe('monitor', () => {
  it('compares decimal strings without floating point loss', () => {
    expect(compareDecimal('100.0000', '100')).toBe(0);
    expect(compareDecimal('9007199254740993.0001', '9007199254740993.0000')).toBe(1);
  });
  it('alerts once per crossing and retries after a failure', async () => {
    vi.useFakeTimers();
    const alerts: string[] = [];
    const states: Array<string | undefined> = [];
    let calls = 0;
    const monitor = new Monitor(async () => {
      calls++;
      if (calls === 2) throw new Error('offline');
      return quote(calls === 1 || calls === 3 ? '99' : calls === 4 ? '150' : '99');
    }, (price) => states.push(String(price?.price)), (_, __, zone) => alerts.push(zone));
    monitor.start(config); await flush();
    for (let i = 0; i < 4; i++) { await vi.advanceTimersByTimeAsync(1000); await flush(); }
    expect(alerts).toEqual(['low', 'low']);
    expect(states[1]).toBe('99');
    monitor.stop(); vi.useRealTimers();
  });
});
