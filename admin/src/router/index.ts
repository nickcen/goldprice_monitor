import { createRouter, createWebHistory } from 'vue-router'
import OverviewView from '../views/OverviewView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/overview', component: OverviewView, meta: { title: '总览' } },
    { path: '/prices', component: () => import('../views/SectionView.vue'), props: { title: '金价数据', description: '金价查询将在 API 契约确定后接入。' } },
    { path: '/rules', component: () => import('../views/SectionView.vue'), props: { title: '监控规则', description: '规则管理将在 API 契约确定后接入。' } },
    { path: '/status', component: () => import('../views/SectionView.vue'), props: { title: '运行状态', description: '运行状态将在 API 契约确定后接入。' } },
    { path: '/:pathMatch(.*)*', redirect: '/overview' },
  ],
})

router.afterEach((to) => { document.title = `${String(to.meta.title ?? '管理后台')} · 金价监控` })
export default router
