import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken, getUser } from '@/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    // 设备管理是管理员专属，监护人落到实时监测
    redirect: () => (isAdmin() ? '/device' : '/latest')
  },
  {
    path: '/device',
    name: 'Device',
    component: () => import('@/views/DeviceView.vue'),
    meta: { roles: ['ADMIN'] }
  },
  {
    path: '/sensor',
    name: 'Sensor',
    component: () => import('@/views/SensorDataView.vue')
  },
  {
    path: '/latest',
    name: 'Latest',
    component: () => import('@/views/LatestDataView.vue')
  },
  {
    path: '/map',
    name: 'Map',
    component: () => import('@/views/MapView.vue')
  },
  {
    path: '/fall',
    name: 'FallAlarms',
    component: () => import('@/views/FallAlarmView.vue')
  },
  {
    path: '/health-stat',
    name: 'HealthStat',
    component: () => import('@/views/HealthStatView.vue')
  }
]

function isAdmin() {
  const user = getUser()
  return !!user && user.role === 'ADMIN'
}

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 前端守卫只做「有没有 token」和菜单可见性判断，真正的越权拦截在后端（DataScopeService）
router.beforeEach(to => {
  const token = getToken()
  if (to.meta.public) {
    return token ? { path: '/' } : true
  }
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !to.meta.roles.includes(getUser()?.role)) {
    ElMessage.error('无权限访问该页面')
    return { path: '/' }
  }
  return true
})

export default router