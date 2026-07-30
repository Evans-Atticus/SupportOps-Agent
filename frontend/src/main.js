import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import HomeView from './views/HomeView.vue'
import LoginView from './views/LoginView.vue'
import WorkspaceView from './views/WorkspaceView.vue'
import TraceCenterView from './views/TraceCenterView.vue'
import PersonalCenterView from './views/PersonalCenterView.vue'
import AnalyticsDashboardView from './views/AnalyticsDashboardView.vue'
import { getCurrentUser } from './api/auth.js'
import { primaryRole } from './auth/roles.js'
import './styles.css'

// 应用路由：三个页面分别对应诊断首页、登录页和工作台。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/workspace', name: 'workspace', component: WorkspaceView,
      meta: { requiresAuth: true, roles: ['CUSTOMER'], accessDenied: 'ai-service' } },
    { path: '/trace', name: 'trace', component: TraceCenterView, meta: { requiresAuth: true } },
    { path: '/personal-center', name: 'personal-center', component: PersonalCenterView, meta: { requiresAuth: true } },
    { path: '/analytics', name: 'analytics-dashboard', component: AnalyticsDashboardView, meta: { requiresAuth: true, roles: ['ADMIN'] } }
  ],
  // 每次切换页面都回到顶部，避免复用页面时保留旧滚动位置。
  scrollBehavior() {
    return { top: 0 }
  }
})

// 工作台必须持有会话级 JWT；关闭标签页后令牌会随 sessionStorage 一起清理。
router.beforeEach(async (to) => {
  if (to.meta.requiresAuth && !sessionStorage.getItem('supportops_access_token')) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (Array.isArray(to.meta.roles) && to.meta.roles.length) {
    try {
      const user = await getCurrentUser()
      if (!to.meta.roles.includes(primaryRole(user))) {
        if (to.meta.accessDenied === 'ai-service') {
          return { name: 'home', query: { accessDenied: 'ai-service' } }
        }
        return { name: 'personal-center' }
      }
    } catch {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  if (to.name === 'login' && sessionStorage.getItem('supportops_access_token')) {
    return { name: 'personal-center' }
  }
  return true
})

// 请求层发现 JWT 失效时立即返回登录页，避免用户继续操作失效页面。
window.addEventListener('supportops:unauthorized', () => {
  if (router.currentRoute.value.name !== 'login') router.replace({ name: 'login' })
})

// 注册路由并挂载 Vue 根组件。
createApp(App).use(router).mount('#app')
