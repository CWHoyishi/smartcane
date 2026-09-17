import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/device'
  },
  {
    path: '/device',
    name: 'Device',
    component: () => import('@/views/DeviceView.vue')
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

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
