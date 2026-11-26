<template>
  <div v-if="!adminToken" class="login-shell"><RouterView /></div>
  <div v-else class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">◆</span><span>金价监控</span></div>
      <div class="nav-label">管理后台</div>
      <nav aria-label="主导航">
        <RouterLink to="/users" class="nav-item">用户管理</RouterLink>
        <RouterLink to="/payments" class="nav-item">支付记录</RouterLink>
        <RouterLink to="/skus" class="nav-item">商品管理</RouterLink>
      </nav>
      <div class="sidebar-foot"><el-button text @click="logout">退出登录</el-button></div>
    </aside>
    <div class="main-area">
      <header class="topbar"><span>Goldprice Monitor</span><span class="environment">ADMIN CONSOLE</span></header>
      <main><RouterView /></main>
    </div>
  </div>
</template>
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { adminToken, apiRequest, setAdminToken } from './api/client'
const router = useRouter()
async function logout() {
  try { await apiRequest('/api/v1/auth/logout', { method: 'POST' }) } finally {
    setAdminToken('')
    await router.replace('/login')
  }
}
</script>
