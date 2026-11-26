export interface AuthToken { token: string; expiresAt: string; role: string; userId: number }
export interface Config { level: number; monitorIntervalSeconds: number; minPrice: string | number | null; maxPrice: string | number | null; goldPriceUrl: string; unit: string; currency: string; precision: number }
export interface GoldPrice { price: string | number; unit: string; currency: string; precision: number; dataTime: string }
