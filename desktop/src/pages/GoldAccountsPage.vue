<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElButton, ElCard, ElForm, ElFormItem, ElInput, ElMessageBox, ElNotification, ElPagination, ElTable, ElTableColumn, ElTag } from 'element-plus';
import { ApiError } from '../api/client';
import { formatDateTime } from '../dateTime';
type Account = { id: number; username: string; isOnline: boolean; validUntil: string | null };
const props = defineProps<{ request: <T>(path: string, options?: { method?: string; body?: unknown }) => Promise<T> }>();
const rows = ref<Account[]>([]), message = ref(''), editing = ref<number | null>(null), accountModal = ref(false), loginId = ref<number | null>(null), captcha = ref<{ base64str: string; cachkey: string } | null>(null), busy = ref(false);
const username = ref(''), password = ref(''), code = ref('');
const pageSize = 10;
const currentPage = ref(1);
const visibleRows = computed(() => rows.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize));
const errorText = (e: unknown) => e instanceof ApiError && e.response && typeof e.response === 'object' && 'message' in e.response ? String(e.response.message) : e instanceof Error ? e.message : '操作失败';
const captchaSrc = () => captcha.value?.base64str.startsWith('data:image/') ? captcha.value.base64str : `data:image/png;base64,${captcha.value?.base64str ?? ''}`;
async function load() { try { rows.value = await props.request<Account[]>('/api/v1/me/gold-accounts'); currentPage.value = Math.min(currentPage.value, Math.max(1, Math.ceil(rows.value.length / pageSize))); message.value = ''; } catch (e) { message.value = errorText(e); } }
onMounted(() => void load());
async function openAccount(row?: Account) { message.value = ''; if (!row) { editing.value = null; username.value = ''; password.value = ''; accountModal.value = true; return; } try { const account = await props.request<Account & { password: string | null }>(`/api/v1/me/gold-accounts/${row.id}`); editing.value = row.id; username.value = account.username; password.value = account.password ?? ''; accountModal.value = true; } catch (e) { message.value = errorText(e); } }
function closeAccount() { editing.value = null; accountModal.value = false; message.value = ''; }
async function save() { if (busy.value) return; busy.value = true; try { const name = username.value.trim(); await props.request(editing.value === null ? '/api/v1/me/gold-accounts' : `/api/v1/me/gold-accounts/${editing.value}`, { method: editing.value === null ? 'POST' : 'PATCH', body: editing.value === null || password.value ? { username: name, password: password.value } : { username: name } }); closeAccount(); await load(); message.value = '已保存'; } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
async function remove(id: number) { if (busy.value) return; try { await ElMessageBox.confirm('确定删除此Gold账号？', '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }); } catch { return; } busy.value = true; try { await props.request(`/api/v1/me/gold-accounts/${id}`, { method: 'DELETE' }); await load(); } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
function openLogin(id: number) { loginId.value = id; captcha.value = null; code.value = ''; message.value = ''; void refreshCaptcha(); }
function closeLogin() { loginId.value = null; captcha.value = null; }
async function refreshCaptcha() { if (busy.value || loginId.value === null) return; const id = loginId.value; busy.value = true; captcha.value = null; code.value = ''; try { const result = await props.request<{ base64str: string; cachkey: string }>(`/api/v1/me/gold-accounts/${id}/password-code`); if (loginId.value === id) { captcha.value = result; message.value = ''; } } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
async function login() { if (busy.value || loginId.value === null) return; if (!captcha.value) { message.value = '请先获取图形验证码'; return; } if (!code.value.trim()) { message.value = '请输入验证码'; return; } busy.value = true; try { await props.request(`/api/v1/me/gold-accounts/${loginId.value}/password-login`, { method: 'POST', body: { code: code.value.trim(), cachkey: captcha.value.cachkey } }); closeLogin(); await load(); ElNotification({ title: '登录成功', type: 'success', duration: 3000 }); } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
async function logout(id: number) { if (busy.value) return; busy.value = true; try { await props.request(`/api/v1/me/gold-accounts/${id}/logout`, { method: 'POST' }); await load(); message.value = '已登出'; } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
</script>
<template>
  <ElCard class="gold-accounts">
    <template #header>
      <div class="gold-heading">
        <h2>账号管理</h2>
        <ElButton type="primary" @click="openAccount()">新增账号</ElButton>
      </div>
    </template>
    <p v-if="message" role="status">{{ message }}</p>
    <ElTable :data="visibleRows" row-key="id" style="width: 100%" empty-text="暂无账号">
      <ElTableColumn prop="username" label="账号" min-width="130" />
      <ElTableColumn label="有效期至" min-width="155">
        <template #default="{ row }: { row: Account }">{{ formatDateTime(row.validUntil) }}</template>
      </ElTableColumn>
      <ElTableColumn label="状态" min-width="75">
        <template #default="{ row }: { row: Account }">
          <ElTag :type="row.isOnline ? 'success' : 'info'">{{ row.isOnline ? '在线' : '未在线' }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" min-width="205" align="right">
        <template #default="{ row }: { row: Account }">
          <ElButton @click="openAccount(row)">编辑</ElButton>
          <ElButton v-if="!row.isOnline" type="primary" @click="openLogin(row.id)">登录</ElButton>
          <ElButton v-else @click="logout(row.id)">登出</ElButton>
          <ElButton @click="remove(row.id)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <div class="gold-account-cards">
      <div v-for="row in visibleRows" :key="row.id" class="gold-account-item">
        <strong>{{ row.username }}</strong>
        <span>有效期至：{{ formatDateTime(row.validUntil) }}</span>
        <span>状态：{{ row.isOnline ? '在线' : '未在线' }}</span>
        <div class="gold-account-actions">
          <ElButton @click="openAccount(row)">编辑</ElButton>
          <ElButton v-if="!row.isOnline" type="primary" @click="openLogin(row.id)">登录</ElButton>
          <ElButton v-else @click="logout(row.id)">登出</ElButton>
          <ElButton @click="remove(row.id)">删除</ElButton>
        </div>
      </div>
      <p v-if="!visibleRows.length">暂无账号</p>
    </div>
    <ElPagination v-model:current-page="currentPage" class="gold-pagination" :page-size="pageSize" :total="rows.length"
      layout="total, prev, pager, next" />
  </ElCard>
  <div v-if="accountModal" class="gold-modal-backdrop">
    <section class="gold-modal" role="dialog" aria-modal="true" :aria-label="editing === null ? '新增账号' : '编辑账号'">
      <h2>{{ editing === null ? '新增账号' : '编辑账号' }}</h2>
      <form @submit.prevent="save"><label>账号名
          <ElInput v-model="username" required :maxlength="120" />
        </label><label>密码
          <ElInput v-model="password" type="text" :maxlength="255" autocomplete="off"
            :placeholder="editing === null ? '可选' : '未设置密码'" />
        </label>
        <div>
          <ElButton native-type="submit" type="primary" :disabled="busy">{{ editing === null ? '新增账号' : '保存修改' }}
          </ElButton>
          <ElButton class="secondary" @click="closeAccount">取消</ElButton>
        </div>
      </form>
      <p role="status">{{ message }}</p>
    </section>
  </div>
  <div v-if="loginId !== null" class="gold-modal-backdrop">
    <section class="gold-modal gold-login-modal" role="dialog" aria-modal="true" aria-label="账号登录">
      <h2>账号登录</h2>
      <ElForm class="gold-login-form" label-position="right" label-width="72px" @submit.prevent="login">
        <ElFormItem label="账号">
          <span>{{rows.find(row => row.id === loginId)?.username}}</span>
        </ElFormItem>
        <ElFormItem label="验证码">
          <div class="gold-captcha-row">
            <img v-if="captcha" alt="图形验证码" :src="captchaSrc()">
            <ElInput v-model="code" aria-label="图形验证码" />
            <ElButton class="secondary" @click="refreshCaptcha">刷新验证码</ElButton>
          </div>
        </ElFormItem>
        <ElFormItem class="gold-login-actions">
          <ElButton native-type="submit" type="primary" :disabled="busy">登录</ElButton>
          <ElButton class="secondary" @click="closeLogin">取消</ElButton>
        </ElFormItem>
      </ElForm>
      <p role="status">{{ message }}</p>
    </section>
  </div>
</template>
