import { createRouter, createWebHistory } from 'vue-router'
import { adminToken, apiRequest, setAdminToken } from '../api/client'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/users' },
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/users', component: () => import('../views/UsersView.vue'), meta: { title: '用户管理' } },
    { path: '/users/:id', component: () => import('../views/UserDetailView.vue'), meta: { title: '用户详情' } },
    { path: '/payments', component: () => import('../views/PaymentsView.vue'), meta: { title: '支付记录' } },
    { path: '/skus', component: () => import('../views/SkusView.vue'), meta: { title: '商品管理' } },
    { path: '/:pathMatch(.*)*', redirect: '/users' },
  ],
})
router.beforeEach(async (to) => {
  if (to.path === '/login') return adminToken.value ? '/users' : true
  if (!adminToken.value) return '/login'
  try {
    const session = await apiRequest<{ role: string }>('/api/v1/auth/session')
    if (session.role !== 'ADMIN') { setAdminToken(''); return '/login' }
    return true
  } catch { setAdminToken(''); return '/login' }
})
router.afterEach((to) => { document.title = `${String(to.meta.title ?? '管理后台')} · 金价监控` })
export default router
