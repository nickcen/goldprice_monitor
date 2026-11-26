<template>
  <div class="login-card">
    <div class="eyebrow">ADMIN CONSOLE</div>
    <h1>管理员登录</h1>
    <el-form @submit.prevent="login">
      <el-form-item label="账号"><el-input v-model="username" autocomplete="username" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="password" type="password" show-password autocomplete="current-password" /></el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading" :disabled="!username || !password">登录</el-button>
    </el-form>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiRequest, jsonBody, setAdminToken } from '../api/client'
const router = useRouter()
const username = ref(''), password = ref(''), loading = ref(false)
async function login() {
  loading.value = true
  try {
    const data = await apiRequest<{ token: string; role: string }>('/api/v1/admin/auth/login', jsonBody({ username: username.value, password: password.value }))
    if (data.role !== 'ADMIN') throw new Error('账号没有管理员权限')
    setAdminToken(data.token)
    password.value = ''
    await router.replace('/users')
  } finally { loading.value = false }
}
</script>
