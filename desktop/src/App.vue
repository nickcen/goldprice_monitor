<script setup lang="ts">
import { ElButton, ElMenu, ElMenuItem } from 'element-plus';
import type { Config, GoldPrice } from './api/contracts';
import type { PricePoint } from './history';
import AuthPage from './pages/AuthPage.vue';
import HomePage from './pages/HomePage.vue';
import AccountPage from './pages/AccountPage.vue';
import GoldAccountsPage from './pages/GoldAccountsPage.vue';
function selectMenu(index: string) {
  if (index === 'home' || index === 'account' || index === 'gold') props.actions.navigate(index);
}
const props = defineProps<{ state: { page: 'restoring' | 'login' | 'register' | 'home' | 'account' | 'gold'; setupError: string; config: Config | null; configError: string | null; price?: GoldPrice; priceError: string | null; message: string; points: PricePoint[]; historyDay: string; historyError: string; savingConfig: boolean; configDraft: { min: string; max: string } | null }; actions: { switchAuth: () => void; submitAuth: (phone: string, password: string, confirm: string) => void; navigate: (page: 'home' | 'account' | 'gold') => void; logout: () => void; retry: () => void; refresh: () => void; save: (min: string, max: string) => void; draft: (min: string, max: string) => void; request: <T>(path: string, options?: { method?: string; body?: unknown }) => Promise<T> } }>();
</script>
<template>
  <main v-if="state.setupError" class="auth"><span class="eyebrow">GOLDPRICE MONITOR</span>
    <h1>需要配置 API</h1>
    <p role="alert">{{ state.setupError }}</p>
  </main>
  <main v-else-if="state.page === 'restoring'" class="auth"><span class="eyebrow">GOLDPRICE MONITOR</span>
    <h1>正在恢复登录…</h1>
  </main>
  <AuthPage v-else-if="state.page === 'login' || state.page === 'register'" :mode="state.page" :message="state.message"
    @switch="actions.switchAuth" @submit="actions.submitAuth" />
  <main v-else class="app-shell">
    <nav class="sidebar" aria-label="主导航">
      <div class="brand-mark" aria-hidden="true">◆</div>
      <ElMenu class="sidebar-menu" :default-active="state.page" @select="selectMenu">
        <ElMenuItem
          v-for="item in [{ page: 'home', icon: '⌂', label: '首页' }, { page: 'gold', icon: '◆', label: '账号' }, { page: 'account', icon: '◉', label: '我的' }] as const"
          :key="item.page" :index="item.page" :aria-current="state.page === item.page ? 'page' : undefined">
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span><span class="nav-label">{{ item.label
            }}</span>
        </ElMenuItem>
      </ElMenu>
    </nav>
    <div class="content">
      <header v-if="state.page === 'home'" class="page-header">
        <div><span class="eyebrow">GOLDPRICE MONITOR</span>
          <h1>首页</h1>
        </div>
        <ElButton class="secondary" @click="actions.logout">登出</ElButton>
      </header>
      <HomePage v-if="state.page === 'home'" :config="state.config" :price="state.price" :price-error="state.priceError"
        :message="state.message" :points="state.points" :history-day="state.historyDay"
        :history-error="state.historyError" />
      <AccountPage v-else-if="state.page === 'account'" :config="state.config" :config-error="state.configError"
        :message="state.message" :saving="state.savingConfig" :draft="state.configDraft" @refresh="actions.refresh"
        @retry="actions.retry" @save="actions.save" @draft="actions.draft" />
      <GoldAccountsPage v-else :request="actions.request" />
    </div>
  </main>
</template>

<style scoped>
.sidebar-menu {
  width: 100%;
  border-right: 0;
  background: transparent;
}

.sidebar-menu .el-menu-item {
  height: 58px;
  margin-bottom: 12px;
  padding: 8px 4px !important;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  color: #364458;
  line-height: 1.2;
}

.sidebar-menu .el-menu-item:hover {
  background: #dce8f6;
}

.sidebar-menu .el-menu-item.is-active {
  background: #d5e3f5;
  color: #234f7c;
}

.sidebar-menu .el-menu-item:focus-visible {
  outline: 3px solid #e9a74f;
  outline-offset: 2px;
}

.nav-icon {
  font-size: 22px;
  line-height: 1;
}

.nav-label {
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

@media (max-width: 700px) {
  .nav-icon {
    font-size: 20px;
  }

  .nav-label {
    font-size: 12px;
  }
}
</style>
