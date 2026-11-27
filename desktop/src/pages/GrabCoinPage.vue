<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElButton, ElCard, ElDialog, ElInputNumber, ElTable, ElTableColumn } from 'element-plus';
import { ApiError } from '../api/client';

type Account = { id: number; username: string; isOnline: boolean; availableCount?: number; listedCount?: number; error?: string };
type Order = { accountId: number; username: string; orderNo: string; goldPrice: number; totalWeight: number | null; createTime: string };
const props = defineProps<{ request: <T>(path: string, options?: { method?: string; body?: unknown }) => Promise<T> }>();
const accounts = ref<Account[]>([]);
const orders = ref<Order[]>([]);
const groupedOrders = computed(() => {
  const groups = new Map<number, { goldPrice: number; count: number; usernames: Set<string>; totalWeight: number | null }>();
  for (const order of orders.value) {
    const group = groups.get(order.goldPrice) ?? { goldPrice: order.goldPrice, count: 0, usernames: new Set<string>(), totalWeight: 0 };
    group.count++;
    group.usernames.add(order.username);
    if (order.totalWeight === null) group.totalWeight = null;
    else if (group.totalWeight !== null) group.totalWeight += order.totalWeight;
    groups.set(order.goldPrice, group);
  }
  return [...groups.values()].sort((a, b) => a.goldPrice - b.goldPrice)
    .map(group => ({ ...group, usernames: [...group.usernames].join('、') }));
});
const loading = ref(false);
const error = ref('');
const selling = ref(false);
const sellAccount = ref<Account | null>(null);
const sellWeight = ref<number | undefined>(undefined);
const sellError = ref('');
function openSell(account: Account) {
  sellAccount.value = account;
  sellWeight.value = undefined;
  sellError.value = '';
}
async function submitSell() {
  if (!sellAccount.value || !sellWeight.value || sellWeight.value <= 0) {
    sellError.value = '请输入大于 0 的克数';
    return;
  }
  selling.value = true;
  sellError.value = '';
  try {
    const result = await props.request<{ success: boolean; completedCount: number; actualWeight: number; message?: string }>(
      `/api/v1/me/gold-accounts/${sellAccount.value.id}/grabcoin/sell`,
      { method: 'POST', body: { weight: sellWeight.value } },
    );
    if (!result.success) {
      sellError.value = `已完成 ${result.completedCount} 次、${result.actualWeight}g；${result.message || '其余挂卖失败'}`;
      await load();
      return;
    }
    sellAccount.value = null;
    await load();
  } catch (e) {
    sellError.value = e instanceof ApiError && e.response && typeof e.response === 'object' && 'message' in e.response
      ? String(e.response.message) : e instanceof Error ? e.message : '挂卖失败';
    await load();
  } finally { selling.value = false; }
}
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const result = await props.request<{ accounts: Account[]; orders: Order[] }>('/api/v1/me/gold-accounts/grabcoin');
    accounts.value = result.accounts;
    orders.value = result.orders;
  } catch (e) {
    error.value = e instanceof ApiError && e.response && typeof e.response === 'object' && 'message' in e.response
      ? String(e.response.message) : e instanceof Error ? e.message : '获取抢金币数据失败';
  } finally { loading.value = false; }
}
onMounted(() => void load());
</script>

<template>
  <section class="grabcoin-page">
    <div class="page-header"><div><span class="eyebrow">GOLDPRICE MONITOR</span><h1>抢金币</h1></div>
      <ElButton :loading="loading" @click="load">刷新</ElButton>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <ElCard class="grabcoin-card">
      <template #header><h2>今日关联账户挂单数</h2></template>
      <ElTable v-loading="loading" :data="accounts" row-key="id" empty-text="暂无关联账户" style="width:100%">
        <ElTableColumn prop="username" label="账号" min-width="140" />
        <ElTableColumn label="今日可挂单数" min-width="110"><template #default="{ row }: { row: Account }">{{ row.availableCount ?? '—' }}</template></ElTableColumn>
        <ElTableColumn label="今日已挂单数" min-width="110"><template #default="{ row }: { row: Account }">{{ row.listedCount ?? '—' }}</template></ElTableColumn>
        <ElTableColumn label="状态" min-width="160"><template #default="{ row }: { row: Account }">{{ row.error || (row.isOnline ? '正常' : '未登录') }}</template></ElTableColumn>
        <ElTableColumn label="操作" min-width="110"><template #default="{ row }: { row: Account }"><ElButton v-if="row.isOnline && !row.error && (row.availableCount ?? 0) > 0" type="primary" @click="openSell(row)">挂卖</ElButton></template></ElTableColumn>
      </ElTable>
    </ElCard>
    <ElDialog :model-value="sellAccount !== null" :title="`挂卖 · ${sellAccount?.username ?? ''}`" width="420px" :close-on-click-modal="!selling" :close-on-press-escape="!selling" :show-close="!selling" @close="sellAccount = null">
      <label for="sell-weight">挂卖克数</label>
      <div style="margin-top: 10px"><ElInputNumber id="sell-weight" v-model="sellWeight" :min="0.0001" :precision="4" :step="1" controls-position="right" /></div>
      <p v-if="sellError" class="error" role="alert">{{ sellError }}</p>
      <template #footer><ElButton :disabled="selling" @click="sellAccount = null">取消</ElButton><ElButton type="primary" :loading="selling" :disabled="selling" @click="submitSell">确认挂卖</ElButton></template>
    </ElDialog>
    <ElCard class="grabcoin-card">
      <template #header><h2>今日所有账号挂单记录</h2></template>
      <ElTable v-loading="loading" :data="groupedOrders" row-key="goldPrice" empty-text="暂无挂单记录" style="width:100%">
        <ElTableColumn prop="goldPrice" label="挂单价" min-width="110" />
        <ElTableColumn prop="count" label="数量" min-width="90" />
        <ElTableColumn prop="totalWeight" label="合计克重" min-width="110" />
        <ElTableColumn prop="usernames" label="关联账号" min-width="180" />
      </ElTable>
    </ElCard>
  </section>
</template>
