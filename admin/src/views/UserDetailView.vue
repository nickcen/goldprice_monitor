<template>
  <div class="page">
    <el-button text @click="router.push('/users')">← 返回用户列表</el-button>
    <h1>用户详情</h1>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-loading="loading">
      <template v-if="user">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ user.id }}</el-descriptions-item><el-descriptions-item label="手机号">{{ user.phone }}</el-descriptions-item>
          <el-descriptions-item label="等级">{{ user.level ?? '—' }}</el-descriptions-item><el-descriptions-item label="状态">{{ user.status === 1 ? '可用' : '不可用' }}</el-descriptions-item>
          <el-descriptions-item label="有效期开始">{{ user.validFromDate ?? '—' }}</el-descriptions-item><el-descriptions-item label="有效期结束">{{ user.validToDate ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ user.createTime }}</el-descriptions-item>
        </el-descriptions>
        <div class="actions"><el-button @click="statusDialog = true">修改状态</el-button><el-button @click="validityDialog = true">修改有效期</el-button><el-button @click="passwordDialog = true">修改密码</el-button></div>
        <h2>支付记录</h2>
        <el-table v-loading="paymentsLoading" :data="payments.items" empty-text="暂无支付记录">
          <el-table-column prop="id" label="ID" width="80" /><el-table-column label="用户"><template #default="{ row }">{{ row.userId }} · {{ row.phone }}</template></el-table-column><el-table-column label="商品"><template #default="{ row }">{{ row.skuId }} · {{ row.skuName }}</template></el-table-column><el-table-column prop="paymentNumber" label="系统支付单号" />
          <el-table-column label="第三方支付单号"><template #default="{ row }">{{ row.thirdpartyNumber ?? '—' }}</template></el-table-column>
          <el-table-column label="支付时间"><template #default="{ row }">{{ row.payTime ?? '—' }}</template></el-table-column>
          <el-table-column prop="createTime" label="创建时间" />
        </el-table>
        <el-pagination v-model:current-page="paymentPage" :page-size="10" layout="prev, pager, next, total" :total="payments.total" @current-change="loadPayments" />
      </template>
    </div>
    <el-dialog v-model="statusDialog" title="修改状态" width="420"><el-select v-model="editStatus"><el-option label="可用" :value="1" /><el-option label="不可用" :value="0" /></el-select><template #footer><el-button @click="statusDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveStatus">保存</el-button></template></el-dialog>
    <el-dialog v-model="validityDialog" title="修改有效期" width="480"><el-date-picker v-model="from" value-format="YYYY-MM-DD" placeholder="开始日期" /><el-date-picker v-model="to" value-format="YYYY-MM-DD" placeholder="结束日期" /><template #footer><el-button @click="validityDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveValidity">保存</el-button></template></el-dialog>
    <el-dialog v-model="passwordDialog" title="修改密码" width="420"><el-input v-model="password" type="password" show-password placeholder="新密码，8–72 位" /><el-input v-model="confirm" type="password" show-password placeholder="确认新密码" /><template #footer><el-button @click="passwordDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePassword">保存</el-button></template></el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiRequest, query, type Page, type Payment, type User } from '../api/client'
const route = useRoute(), router = useRouter(), id = Number(route.params.id)
const user = ref<User | null>(null), loading = ref(false), error = ref(''), saving = ref(false)
const paymentsLoading = ref(false), paymentPage = ref(1), payments = ref<Page<Payment>>({ items: [], page: 1, pageSize: 10, total: 0, totalPages: 0 })
const statusDialog = ref(false), validityDialog = ref(false), passwordDialog = ref(false)
const editStatus = ref(1), from = ref<string | null>(null), to = ref<string | null>(null), password = ref(''), confirm = ref('')
async function load() { loading.value = true; error.value = ''; try { const loaded = await apiRequest<User>(`/api/v1/admin/users/${id}`); user.value = loaded; editStatus.value = loaded.status; from.value = loaded.validFromDate; to.value = loaded.validToDate } catch (e) { error.value = e instanceof Error ? e.message : '加载失败' } finally { loading.value = false } }
async function loadPayments() { paymentsLoading.value = true; try { payments.value = await apiRequest(`/api/v1/admin/payments?${query({ userId: id, page: paymentPage.value })}`) } finally { paymentsLoading.value = false } }
async function saveStatus() { saving.value = true; try { await apiRequest(`/api/v1/admin/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status: editStatus.value }) }); statusDialog.value = false; await load() } finally { saving.value = false } }
async function saveValidity() { if (from.value && to.value && to.value <= from.value) { ElMessage.error('结束日期须晚于开始日期'); return } saving.value = true; try { await apiRequest(`/api/v1/admin/users/${id}/validity`, { method: 'PATCH', body: JSON.stringify({ validFromDate: from.value || null, validToDate: to.value || null }) }); validityDialog.value = false; await load() } finally { saving.value = false } }
async function savePassword() { if (password.value !== confirm.value) { ElMessage.error('两次输入的密码不一致'); return } if (password.value.length < 8 || password.value.length > 72) { ElMessage.error('密码长度应为 8–72 位'); return } saving.value = true; try { await apiRequest(`/api/v1/admin/users/${id}/password`, { method: 'PUT', body: JSON.stringify({ password: password.value }) }); password.value = ''; confirm.value = ''; passwordDialog.value = false; ElMessage.success('密码已更新') } finally { saving.value = false } }
onMounted(() => { void load(); void loadPayments() })
</script>
