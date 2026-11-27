<script setup lang="ts">
import { computed } from 'vue';
import type { Config, GoldPrice } from '../api/contracts';
import { chartSvg, localDay, type PricePoint } from '../history';
import { formatDateTime } from '../dateTime';
const props = defineProps<{ config: Config | null; price?: GoldPrice; priceError: string | null; message: string; points: PricePoint[]; historyDay: string; historyError: string }>();
const chart = computed(() => chartSvg(props.historyDay === localDay() ? props.points : []));
</script>
<template>
  <section class="price-card"><span>当前金价</span><strong>{{ props.price ? Number(props.price.price).toFixed(2) : '—' }}</strong><small>{{ props.config ? `${props.config.currency} / ${props.config.unit}` : '获取中' }}</small><p>{{ props.price ? `数据时间 ${formatDateTime(props.price.dataTime)}` : '等待首个价格' }}</p><p :class="{ error: props.priceError }">{{ props.priceError ? (props.price ? `${props.priceError}（显示上次成功价格，非最新）` : props.priceError) : (props.price ? '监控中' : '获取中') }}</p><p v-if="props.message" class="error" role="alert">{{ props.message }}</p></section>
  <section class="chart-card"><h2>今日金价走势</h2><p>{{ props.config ? `${props.config.currency} / ${props.config.unit}` : '获取中' }} · {{ localDay() }}</p><p v-if="props.historyError" class="error" role="alert">历史记录：{{ props.historyError }}</p><div v-else v-html="chart" /></section>
</template>
