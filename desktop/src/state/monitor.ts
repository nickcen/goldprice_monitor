import type { Config, GoldPrice } from '../api/contracts';

export function compareDecimal(a: string | number, b: string | number): number {
  const parse = (value: string | number) => {
    const match = String(value).match(/^([+-]?)(\d+)(?:\.(\d+))?$/);
    if (!match) throw new Error('无效价格');
    return { negative: match[1] === '-', whole: match[2], fraction: match[3] ?? '' };
  };
  const left = parse(a), right = parse(b);
  const scale = Math.max(left.fraction.length, right.fraction.length);
  const number = (v: typeof left) => (v.negative ? -1n : 1n) * BigInt(v.whole + v.fraction.padEnd(scale, '0'));
  const l = number(left), r = number(right);
  return l < r ? -1 : l > r ? 1 : 0;
}

export class Monitor {
  private timer?: number;
  private running = false;
  private generation = 0;
  private lastZone: 'low' | 'high' | 'normal' = 'normal';
  private thresholds = '';
  private lastPrice?: GoldPrice;
  constructor(private fetchPrice: () => Promise<GoldPrice>, private onState: (price: GoldPrice | undefined, error: string | null) => void, private onAlert: (price: GoldPrice, threshold: string | number, direction: 'low' | 'high') => void) {}
  start(config: Config): void {
    const thresholds = `${config.minPrice ?? ''}|${config.maxPrice ?? ''}`;
    const previousZone = this.lastZone;
    const previousPrice = this.lastPrice;
    const sameThresholds = thresholds === this.thresholds;
    this.stop();
    this.thresholds = thresholds;
    if (sameThresholds) this.lastZone = previousZone;
    if (previousPrice && previousPrice.unit === config.unit && previousPrice.currency === config.currency && previousPrice.precision === config.precision) this.lastPrice = previousPrice;
    const generation = this.generation;
    const interval = Math.max(1, config.monitorIntervalSeconds) * 1000;
    const poll = async () => {
      if (generation !== this.generation) return;
      if (this.running) { this.timer = globalThis.setTimeout(poll, interval); return; }
      this.running = true;
      try {
        const price = await this.fetchPrice();
        if (generation !== this.generation) return;
        if (price.unit !== config.unit || price.currency !== config.currency || price.precision !== config.precision) throw new Error('金价单位与监控配置不一致');
        this.lastPrice = price;
        this.onState(price, null);
        const zone = config.minPrice != null && compareDecimal(price.price, config.minPrice) < 0 ? 'low' : config.maxPrice != null && compareDecimal(price.price, config.maxPrice) > 0 ? 'high' : 'normal';
        if (zone !== this.lastZone && zone !== 'normal') this.onAlert(price, zone === 'low' ? config.minPrice! : config.maxPrice!, zone);
        this.lastZone = zone;
      } catch (error) {
        if (generation === this.generation) this.onState(this.lastPrice, error instanceof Error ? error.message : '获取金价失败');
      } finally {
        this.running = false;
        if (generation === this.generation) this.timer = globalThis.setTimeout(poll, interval);
      }
    };
    void poll();
  }
  stop(): void { this.generation++; if (this.timer) clearTimeout(this.timer); this.timer = undefined; this.lastZone = 'normal'; this.lastPrice = undefined; }
}
