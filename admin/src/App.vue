<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">◆</span><span>金价监控</span></div>
      <div class="nav-label">WORKSPACE</div>
      <nav aria-label="主导航">
        <RouterLink v-for="item in items" :key="item.path" :to="item.path" class="nav-item">{{ item.label }}</RouterLink>
      </nav>
      <div class="sidebar-foot">管理后台 · 基础工程</div>
    </aside>
    <div class="main-area">
      <header class="topbar"><span>Goldprice Monitor</span><span class="environment">ADMIN CONSOLE</span></header>
      <main><RouterView /></main>
    </div>
  </div>
</template>
<script setup lang="ts">
const items = [
  { path: '/overview', label: '总览' },
  { path: '/prices', label: '金价数据' },
  { path: '/rules', label: '监控规则' },
  { path: '/status', label: '运行状态' },
]
</script>
