<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElButton, ElCard, ElInput, ElMessageBox, ElPagination, ElTable, ElTableColumn, ElTag } from 'element-plus';
import { ApiError } from '../api/client';
type Account = { id: number; username: string; isOnline: boolean };
const props = defineProps<{ request: <T>(path: string, options?: { method?: string; body?: unknown }) => Promise<T> }>();
const rows = ref<Account[]>([]), message = ref(''), editing = ref<number | null>(null), accountModal = ref(false), loginId = ref<number | null>(null), loginMode = ref<'sms' | 'password'>('sms'), captcha = ref<{ base64str: string; cachkey: string } | null>(null), busy = ref(false);
const username = ref(''), password = ref(''), mobile = ref(''), code = ref('');
const pageSize = 10;
const currentPage = ref(1);
const visibleRows = computed(() => rows.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize));
const errorText = (e: unknown) => e instanceof ApiError && e.response && typeof e.response === 'object' && 'message' in e.response ? String(e.response.message) : e instanceof Error ? e.message : '操作失败';
const captchaSrc = () => captcha.value?.base64str.startsWith('data:image/') ? captcha.value.base64str : `data:image/png;base64,${captcha.value?.base64str ?? ''}`;
async function load() { try { rows.value = await props.request<Account[]>('/api/v1/me/gold-accounts'); currentPage.value = Math.min(currentPage.value, Math.max(1, Math.ceil(rows.value.length / pageSize))); message.value = ''; } catch (e) { message.value = errorText(e); } }
onMounted(() => void load());
function openAccount(row?: Account) { editing.value = row?.id ?? null; username.value = row?.username ?? ''; password.value = ''; message.value = ''; accountModal.value = true; }
function closeAccount() { editing.value = null; accountModal.value = false; message.value = ''; }
async function save() { if (busy.value) return; busy.value = true; try { const name = username.value.trim(); await props.request(editing.value === null ? '/api/v1/me/gold-accounts' : `/api/v1/me/gold-accounts/${editing.value}`, { method: editing.value === null ? 'POST' : 'PATCH', body: editing.value === null || password.value ? { username: name, password: password.value } : { username: name } }); closeAccount(); await load(); message.value = '已保存'; } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
async function remove(id: number) { if (busy.value) return; try { await ElMessageBox.confirm('确定删除此Gold账号？', '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }); } catch { return; } busy.value = true; try { await props.request(`/api/v1/me/gold-accounts/${id}`, { method: 'DELETE' }); await load(); } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
function openLogin(id: number) { loginId.value = id; loginMode.value = 'sms'; captcha.value = null; mobile.value = ''; code.value = ''; message.value = ''; }
function closeLogin() { loginId.value = null; captcha.value = null; }
async function refreshCaptcha() { if (busy.value || loginId.value === null) return; const id = loginId.value; busy.value = true; captcha.value = null; try { const result = await props.request<{ base64str: string; cachkey: string }>(`/api/v1/me/gold-accounts/${id}/password-code`); if (loginId.value === id && loginMode.value === 'password') { captcha.value = result; message.value = ''; } } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
function changeMode(mode: 'sms' | 'password') { loginMode.value = mode; captcha.value = null; message.value = ''; code.value = ''; if (mode === 'password') void refreshCaptcha(); }
async function sendSms() { if (busy.value || loginId.value === null) return; const value = mobile.value.trim(); if (!/^(?:1[3-9]\d{9}|\+[1-9]\d{7,14})$/.test(value)) { message.value = '手机号格式无效'; return; } busy.value = true; try { await props.request(`/api/v1/me/gold-accounts/${loginId.value}/send-sms`, { method: 'POST', body: { mobile: value } }); message.value = '验证码已发送'; } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
async function login() { if (busy.value || loginId.value === null) return; if (loginMode.value === 'password' && !captcha.value) { message.value = '请先获取图形验证码'; return; } busy.value = true; try { const path = loginMode.value === 'sms' ? 'login' : 'password-login'; const body = loginMode.value === 'sms' ? { mobile: mobile.value.trim(), code: code.value.trim() } : { code: code.value.trim(), cachkey: captcha.value!.cachkey }; await props.request(`/api/v1/me/gold-accounts/${loginId.value}/${path}`, { method: 'POST', body }); closeLogin(); await load(); message.value = '登录成功'; } catch (e) { message.value = errorText(e); } finally { busy.value = false; } }
</script>
<template>
  <ElCard class="gold-accounts">
    <template #header>
      <div class="gold-heading">
        <ElButton type="primary" @click="openAccount()">新增账号</ElButton>
      </div>
    </template>
    <p v-if="message" role="status">{{ message }}</p>
    <ElTable :data="visibleRows" row-key="id" style="width: 100%" empty-text="暂无账号">
      <ElTableColumn prop="username" label="账号" min-width="180" />
      <ElTableColumn label="状态" min-width="100">
        <template #default="{ row }: { row: Account }">
          <ElTag :type="row.isOnline ? 'success' : 'info'">{{ row.isOnline ? '在线' : '未在线' }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" min-width="220" align="right">
        <template #default="{ row }: { row: Account }">
          <ElButton @click="openAccount(row)">编辑</ElButton>
          <ElButton v-if="!row.isOnline" type="primary" @click="openLogin(row.id)">登录</ElButton>
          <ElButton @click="remove(row.id)">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElPagination
      v-if="rows.length > pageSize"
      v-model:current-page="currentPage"
      class="gold-pagination"
      :page-size="pageSize"
      :total="rows.length"
      layout="total, prev, pager, next"
    />
  </ElCard>
  <div v-if="accountModal" class="gold-modal-backdrop">
    <section class="gold-modal" role="dialog" aria-modal="true"
      :aria-label="editing === null ? '新增Gold账号' : '编辑Gold账号'">
      <h2>{{ editing === null ? '新增Gold账号' : '编辑Gold账号' }}</h2>
      <form @submit.prevent="save"><label>账号名
          <ElInput v-model="username" required :maxlength="120" />
        </label><label>密码（明文保存）
          <ElInput v-model="password" type="password" :maxlength="255" autocomplete="off"
            :placeholder="editing === null ? '可选' : '留空则不修改'" />
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
    <section class="gold-modal" role="dialog" aria-modal="true" aria-label="Gold账号登录">
      <h2>Gold账号登录</h2>
      <div>
        <ElButton class="secondary" @click="changeMode('sms')">短信登录</ElButton>
        <ElButton class="secondary" @click="changeMode('password')">账号密码登录</ElButton>
      </div>
      <form @submit.prevent="login"><template v-if="loginMode === 'sms'"><label>手机号
            <ElInput v-model="mobile" type="tel" required />
          </label></template><template v-else>
          <p>账号：{{rows.find(row => row.id === loginId)?.username}}</p><img v-if="captcha" alt="图形验证码"
            :src="captchaSrc()">
          <ElButton class="secondary" @click="refreshCaptcha">刷新验证码</ElButton>
        </template><label>验证码
          <ElInput v-model="code" required />
        </label>
        <div>
          <ElButton v-if="loginMode === 'sms'" class="secondary" @click="sendSms">获取验证码</ElButton>
          <ElButton native-type="submit" type="primary" :disabled="busy">登录</ElButton>
          <ElButton class="secondary" @click="closeLogin">取消</ElButton>
        </div>
      </form>
      <p role="status">{{ message }}</p>
    </section>
  </div>
</template>
