<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElButton, ElCard, ElDescriptions, ElDescriptionsItem, ElDialog, ElForm, ElFormItem, ElInput } from 'element-plus';
import type { Config } from '../api/contracts';

const props = defineProps<{ config: Config | null; configError: string | null; message: string; saving: boolean; draft: { min: string; max: string } | null }>();
const emit = defineEmits<{ refresh: []; retry: []; save: [min: string, max: string]; draft: [min: string, max: string] }>();
const dialogVisible = ref(false);
const min = ref('');
const max = ref('');

function openConfig() {
  min.value = props.draft?.min ?? (props.config?.minPrice == null ? '' : Number(props.config.minPrice).toFixed(2));
  max.value = props.draft?.max ?? (props.config?.maxPrice == null ? '' : Number(props.config.maxPrice).toFixed(2));
  dialogVisible.value = true;
}
function update() { emit('draft', min.value, max.value); }
function save() { emit('save', min.value, max.value); }
watch(() => props.message, value => { if (value === '阈值已保存') dialogVisible.value = false; });
</script>

<template>
  <section class="settings">
    <ElCard class="settings-card" shadow="never">
      <template #header>
        <div class="section-heading">
          <div><span class="eyebrow">ACCOUNT SETTINGS</span><h2>监控配置</h2></div>
          <div class="settings-actions">
            <ElButton class="secondary" :disabled="props.saving" @click="emit('refresh')">重新加载</ElButton>
            <ElButton type="primary" :disabled="!props.config || props.saving" @click="openConfig">配置</ElButton>
          </div>
        </div>
      </template>
      <template v-if="props.config">
        <ElDescriptions class="config-descriptions" :column="2" border>
          <ElDescriptionsItem label="用户等级">{{ props.config.level }}</ElDescriptionsItem>
          <ElDescriptionsItem label="监控频率">每 {{ props.config.monitorIntervalSeconds }} 秒</ElDescriptionsItem>
          <ElDescriptionsItem label="低价提醒">{{ props.config.minPrice == null ? '未设置' : Number(props.config.minPrice).toFixed(2) }}</ElDescriptionsItem>
          <ElDescriptionsItem label="高价提醒">{{ props.config.maxPrice == null ? '未设置' : Number(props.config.maxPrice).toFixed(2) }}</ElDescriptionsItem>
        </ElDescriptions>
        <p v-if="props.configError" class="error" role="alert">配置刷新失败：{{ props.configError }}</p>
        <ElButton v-if="props.configError" @click="emit('retry')">重试</ElButton>
      </template>
      <template v-else>
        <p class="error">{{ props.configError ?? '正在获取配置' }}</p>
        <ElButton @click="emit('retry')">重试</ElButton>
      </template>
      <div class="message" role="status">{{ props.message }}</div>
    </ElCard>

    <ElDialog v-model="dialogVisible" title="配置提醒阈值" width="min(480px, 90vw)" :close-on-click-modal="!props.saving" :show-close="!props.saving">
      <ElForm label-position="top" @submit.prevent="save">
        <ElFormItem label="低价提醒"><ElInput v-model="min" inputmode="decimal" placeholder="不设置" @input="update" /></ElFormItem>
        <ElFormItem label="高价提醒"><ElInput v-model="max" inputmode="decimal" placeholder="不设置" @input="update" /></ElFormItem>
        <button type="submit" hidden aria-hidden="true"></button>
      </ElForm>
      <p v-if="props.message.startsWith('保存失败') || props.message.startsWith('价格须')" class="error" role="alert">{{ props.message }}</p>
      <template #footer>
        <ElButton :disabled="props.saving" @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="props.saving" @click="save">保存阈值</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
