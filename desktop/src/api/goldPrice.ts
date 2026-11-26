import type { Config, GoldPrice } from './contracts';

export async function fetchGoldPrice(config: Config, request: (path: string) => Promise<GoldPrice>, now = new Date()): Promise<GoldPrice> {
  const price = await request(`/api/v1/gold-price/at?time=${encodeURIComponent(now.toISOString())}`);
  if (!price || !/^\d+(?:\.\d{1,2})?$/.test(String(price.price)) ||
      typeof price.dataTime !== 'string' || Number.isNaN(Date.parse(price.dataTime)) ||
      price.unit !== config.unit || price.currency !== config.currency || price.precision !== config.precision) {
    throw new Error('金价数据格式无效');
  }
  return price;
}
