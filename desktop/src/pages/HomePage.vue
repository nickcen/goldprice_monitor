<script setup lang="ts">
import { computed } from 'vue';
import { ElButton, ElCard } from 'element-plus';
import type { Config, GoldPrice } from '../api/contracts';
import { chartSvg, localDay, type PricePoint } from '../history';
import { formatTime } from '../dateTime';
const props = defineProps<{ config: Config | null; price?: GoldPrice; priceError: string | null; message: string; points: PricePoint[]; historyDay: string; historyError: string }>();
const emit = defineEmits<{ logout: [] }>();
const chart = computed(() => chartSvg(props.historyDay === localDay() ? props.points : []));
</script>
<template>
  <ElCard class="price-card" shadow="never">
    <template #header>
      <div class="price-card-header">
        <div class="price-summary">
          <strong>{{ props.price ? Number(props.price.price).toFixed(2) : '—' }}</strong>
          <p>{{ props.price ? `数据时间 ${formatTime(props.price.dataTime)}` : '等待首个价格' }}</p>
        </div>
        <ElButton class="secondary" @click="emit('logout')">登出</ElButton>
      </div>
    </template>
    <p v-if="props.historyError" class="error" role="alert">历史记录：{{ props.historyError }}</p>
    <div v-else v-html="chart" />
  </ElCard>
</template>
