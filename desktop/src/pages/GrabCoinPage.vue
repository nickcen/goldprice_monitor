<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElButton, ElCard, ElDialog, ElForm, ElFormItem, ElInputNumber, ElMessage, ElRadio, ElRadioGroup, ElTable, ElTableColumn } from 'element-plus';
import { ApiError } from '../api/client';

type Account = { id: number; username: string; isOnline: boolean; availableCount?: number; listedCount?: number; error?: string };
type Order = { accountId: number; orderId: number; username: string; orderNo: string; goldPrice: number; totalWeight: number | null; createTime: string };
const props = defineProps<{ request: <T>(path: string, options?: { method?: string; body?: unknown }) => Promise<T> }>();
const accounts = ref<Account[]>([]);
const orders = ref<Order[]>([]);
const groupedOrders = computed(() => {
  const groups = new Map<number, { goldPrice: number; count: number; totalWeight: number | null }>();
  for (const order of orders.value) {
    const group = groups.get(order.goldPrice) ?? { goldPrice: order.goldPrice, count: 0, totalWeight: 0 };
    group.count++;
    if (order.totalWeight === null) group.totalWeight = null;
    else if (group.totalWeight !== null) group.totalWeight += order.totalWeight;
    groups.set(order.goldPrice, group);
  }
  return [...groups.values()].sort((a, b) => a.goldPrice - b.goldPrice);
});
const loading = ref(false);
const error = ref('');
const selling = ref(false);
const sellAccount = ref<Account | null>(null);
const sellMode = ref<'all' | 'weight'>('all');
const sellWeight = ref<number | undefined>(undefined);
const sellError = ref('');
const actionError = ref('');
const actingOrder = ref<number | null>(null);
const priceToSell = ref<number | null>(null);
function openSell(account: Account) {
  sellAccount.value = account;
  sellMode.value = 'all';
  sellWeight.value = undefined;
  sellError.value = '';
}
async function submitSell() {
  const account = sellAccount.value;
  if (!account) return;
  if (sellMode.value === 'weight' && (!sellWeight.value || sellWeight.value <= 0)) {
    sellError.value = '请输入大于 0 的克数';
    return;
  }
  selling.value = true;
  sellError.value = '';
  try {
    if (sellMode.value === 'all') {
      const result = await props.request<{ count?: number; totalWeight?: number }>(`/api/v1/me/gold-accounts/${account.id}/grabcoin/sell-all`, { method: 'POST' });
      sellAccount.value = null;
      ElMessage.success(`已挂卖 ${result.count ?? 0} 笔，共 ${result.totalWeight ?? 0}g`);
      await load();
      return;
    }
    const result = await props.request<{ success: boolean; completedCount: number; actualWeight: number; message?: string }>(
      `/api/v1/me/gold-accounts/${account.id}/grabcoin/sell`,
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
    sellError.value = message(e, '挂卖失败');
    await load();
  } finally { selling.value = false; }
}
function message(e: unknown, fallback: string) {
  return e instanceof ApiError && e.response && typeof e.response === 'object' && 'message' in e.response
    ? String(e.response.message) : e instanceof Error ? e.message : fallback;
}
async function submitOrderSell() {
  const price = priceToSell.value;
  if (price === null) return;
  actingOrder.value = price;
  actionError.value = '';
  try {
    const result = await props.request<{ success: boolean; completedCount: number; totalCount: number; message?: string }>(
      '/api/v1/me/gold-accounts/grabcoin/orders/sell', { method: 'POST', body: { goldPrice: price } });
    if (!result.success) {
      actionError.value = `已卖出 ${result.completedCount}/${result.totalCount} 笔；${result.message || '卖出失败'}`;
      await load();
      return;
    }
    priceToSell.value = null;
    ElMessage.success(`已卖出 ${result.completedCount} 笔权益订单`);
    await load();
  } catch (e) { actionError.value = message(e, '卖出失败'); }
  finally { actingOrder.value = null; }
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
    <div class="page-header">
      <div><span class="eyebrow">GOLDPRICE MONITOR</span>
        <h1>抢金币</h1>
      </div>
      <ElButton :loading="loading" @click="load">刷新</ElButton>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
    <ElCard class="grabcoin-card">
      <template #header>
        <h2>今日关联账户挂单数</h2>
      </template>
      <ElTable v-loading="loading" :data="accounts" row-key="id" empty-text="暂无关联账户" style="width:100%">
        <ElTableColumn prop="username" label="账号" min-width="130" />
        <ElTableColumn label="已挂/可挂" min-width="105"><template #default="{ row }: { row: Account }">{{ row.listedCount
          ?? '—' }}/{{ row.availableCount ?? '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" min-width="90"><template #default="{ row }: { row: Account }">{{ row.error ||
          (row.isOnline ? '正常' : '未登录') }}</template></ElTableColumn>
        <ElTableColumn label="操作" min-width="100"><template #default="{ row }: { row: Account }">
            <ElButton v-if="row.isOnline && !row.error && (row.availableCount ?? 0) > 0" type="primary"
              @click="openSell(row)">卖出</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
    <ElDialog :model-value="sellAccount !== null" title="卖出" width="420px" :close-on-click-modal="!selling"
      :close-on-press-escape="!selling" :show-close="!selling" @close="sellAccount = null">
      <ElForm label-position="top" @submit.prevent="submitSell">
        <ElFormItem label="卖出方式">
          <ElRadioGroup v-model="sellMode" :disabled="selling">
            <ElRadio value="all">全部卖出</ElRadio>
            <ElRadio value="weight">按克数</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem v-if="sellMode === 'weight'" label="挂卖克数">
          <ElInputNumber v-model="sellWeight" :min="0.0001" :precision="4" :step="1"
            controls-position="right" :disabled="selling" />
        </ElFormItem>
      </ElForm>
      <p v-if="sellError" class="error" role="alert">{{ sellError }}</p>
      <template #footer>
        <ElButton :disabled="selling" @click="sellAccount = null">取消</ElButton>
        <ElButton type="primary" :loading="selling" :disabled="selling" @click="submitSell">确认卖出</ElButton>
      </template>
    </ElDialog>
    <ElCard class="grabcoin-card">
      <template #header>
        <h2>今日待卖出权益订单</h2>
      </template>
      <ElTable v-loading="loading" :data="groupedOrders" row-key="goldPrice" empty-text="暂无挂单记录" style="width:100%">
        <ElTableColumn prop="goldPrice" label="挂单价" min-width="110" />
        <ElTableColumn prop="count" label="数量" min-width="90" />
        <ElTableColumn prop="totalWeight" label="合计克重" min-width="110" />
        <ElTableColumn label="操作" min-width="100"><template
            #default="{ row }: { row: { goldPrice: number; count: number } }">
            <ElButton type="primary" :disabled="!row.count" @click="priceToSell = row.goldPrice; actionError = ''">卖出
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
    <ElDialog :model-value="priceToSell !== null" :title="`卖出权益订单 · ${priceToSell ?? ''}`" width="420px"
      :close-on-click-modal="actingOrder === null" :show-close="actingOrder === null" @close="priceToSell = null">
      <p>确认卖出该挂单价下的 {{groupedOrders.find(group => group.goldPrice === priceToSell)?.count ?? 0}} 笔权益订单？</p>
      <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
      <template #footer>
        <ElButton :disabled="actingOrder !== null" @click="priceToSell = null">取消</ElButton>
        <ElButton type="primary" :loading="actingOrder !== null" @click="submitOrderSell">确认卖出</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
