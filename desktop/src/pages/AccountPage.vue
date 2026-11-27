<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElButton, ElInput } from 'element-plus';
import type { Config } from '../api/contracts';
const props = defineProps<{ config: Config | null; configError: string | null; message: string; saving: boolean; draft: { min: string; max: string } | null }>();
const emit = defineEmits<{ refresh: []; retry: []; save: [min: string, max: string]; draft: [min: string, max: string] }>();
const min = ref(''), max = ref('');
watch(() => [props.config, props.draft] as const, () => { min.value = props.draft?.min ?? (props.config?.minPrice == null ? '' : Number(props.config.minPrice).toFixed(2)); max.value = props.draft?.max ?? (props.config?.maxPrice == null ? '' : Number(props.config.maxPrice).toFixed(2)); }, { immediate: true });
function update() { emit('draft', min.value, max.value); }
</script>
<template>
  <section class="settings"><div class="section-heading"><div><span class="eyebrow">ACCOUNT SETTINGS</span><h2>监控配置</h2></div><ElButton class="secondary" :disabled="props.saving" @click="emit('refresh')">重新获取用户配置</ElButton></div>
    <template v-if="props.config"><div class="config-grid"><div><span>用户等级</span><strong>{{ props.config.level }}</strong></div><div><span>监控频率</span><strong>每 {{ props.config.monitorIntervalSeconds }} 秒</strong></div></div><form @submit.prevent="emit('save', min, max)"><label>低价提醒<ElInput v-model="min" inputmode="decimal" placeholder="不设置" @input="update" /></label><label>高价提醒<ElInput v-model="max" inputmode="decimal" placeholder="不设置" @input="update" /></label><ElButton native-type="submit" type="primary" :disabled="props.saving">{{ props.saving ? '保存中…' : '保存阈值' }}</ElButton></form><p v-if="props.configError" class="error" role="alert">配置刷新失败：{{ props.configError }}</p><ElButton v-if="props.configError" @click="emit('retry')">重试</ElButton></template>
    <template v-else><p class="error">{{ props.configError ?? '正在获取配置' }}</p><ElButton @click="emit('retry')">重试</ElButton></template><div class="message" role="status">{{ props.message }}</div>
  </section>
</template>
