<template>
  <div class="page">
    <div class="eyebrow">ACCOUNTS</div><h1>用户管理</h1>
    <div class="filters">
      <el-input v-model="phone" placeholder="手机号" clearable @keyup.enter="search" />
      <el-select v-model="status" placeholder="全部状态" clearable><el-option label="可用" :value="1" /><el-option label="不可用" :value="0" /></el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-table v-loading="loading" :data="data.items" empty-text="暂无用户" @row-click="openUser">
      <el-table-column prop="id" label="ID" width="90" /><el-table-column prop="phone" label="手机号" /><el-table-column prop="level" label="等级" width="90" />
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ row.status === 1 ? '可用' : '不可用' }}</template></el-table-column>
      <el-table-column prop="validFromDate" label="有效期开始" /><el-table-column prop="validToDate" label="有效期结束" />
      <el-table-column prop="createTime" label="创建时间" min-width="180" />
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="10" layout="prev, pager, next, total" :total="data.total" @current-change="load" />
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiRequest, query, type Page, type User } from '../api/client'
const router = useRouter()
const phone = ref(''), status = ref<number | undefined>(), page = ref(1), loading = ref(false), error = ref('')
const data = ref<Page<User>>({ items: [], page: 1, pageSize: 10, total: 0, totalPages: 0 })
async function load() { loading.value = true; error.value = ''; try { data.value = await apiRequest(`/api/v1/admin/users?${query({ phone: phone.value, status: status.value, page: page.value })}`) } catch (e) { error.value = e instanceof Error ? e.message : '查询失败' } finally { loading.value = false } }
function search() { page.value = 1; void load() }
function openUser(row: User) { void router.push(`/users/${row.id}`) }
onMounted(load)
</script>
