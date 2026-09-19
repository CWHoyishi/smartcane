<template>
  <!-- 登录页不套侧边栏框架 -->
  <router-view v-if="isLoginPage" />
  <el-container v-else style="height: 100vh">
    <el-aside width="200px" style="background-color: #2e3b4e; color: white">
      <div style="padding: 20px; font-size: 18px; font-weight: bold; text-align: center; border-bottom: 1px solid #4a5568">
        智能拐杖系统
      </div>
      <el-menu
        :default-active="activeMenu"
        class="el-menu-vertical"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        background-color="#2e3b4e"
        @select="handleSelect"
      >
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background-color: white; border-bottom: 1px solid #e0e0e0; display: flex; align-items: center; justify-content: space-between">
        <div style="font-size: 20px; font-weight: 500">{{ pageTitle }}</div>
        <div style="display: flex; align-items: center; gap: 12px">
          <span style="color: #606266">{{ currentUser.realName || currentUser.username }}</span>
          <el-tag :type="isAdminUser ? 'danger' : 'success'" size="small">{{ isAdminUser ? '管理员' : '监护人' }}</el-tag>
          <el-button link type="primary" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main style="background-color: #f0f2f5">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { clearSession, getUser, updateUser } from '@/utils/auth'

const router = useRouter()
const route = useRoute()
const currentUser = ref(getUser() || {})

const isLoginPage = computed(() => route.path === '/login')
const isAdminUser = computed(() => currentUser.value.role === 'ADMIN')
const activeMenu = computed(() => route.path)

const menus = [
  { path: '/device', title: '设备管理', icon: 'Monitor', adminOnly: true },
  { path: '/sensor', title: '传感器数据', icon: 'DataLine' },
  { path: '/latest', title: '实时监测', icon: 'Location' },
  { path: '/map', title: '实时地图', icon: 'MapLocation' },
  { path: '/fall', title: '告警处理', icon: 'Warning' },
  { path: '/health-stat', title: '健康统计', icon: 'TrendCharts' }
]

// 菜单只是可见性控制，越权由后端拦截；设备增删改是管理员专属，监护人看不到入口
const visibleMenus = computed(() => menus.filter(m => !m.adminOnly || isAdminUser.value))

const pageTitle = computed(() => (menus.find(m => m.path === route.path) || {}).title || '')

// 登录成功是同一次页面加载内的路由切换，App 不会重新挂载（onMounted 只跑一次），
// 所以监听「进入业务页」：先读本地缓存把菜单点亮，再异步回查角色
watch(
  isLoginPage,
  onLoginPage => {
    if (onLoginPage) {
      return
    }
    const cached = getUser()
    if (cached) {
      currentUser.value = cached
    }
    refreshUser()
  },
  { immediate: true }
)

/** 刷新角色与姓名：本地缓存可能过期，权限调整后需要立刻反映到菜单 */
async function refreshUser() {
  try {
    const res = await authApi.me()
    if (res.data) {
      currentUser.value = res.data
      updateUser(res.data)
    }
  } catch (e) {
    // 401 已由响应拦截器跳登录页，这里不再重复处理
    console.warn('[获取当前用户失败]', e.message)
  }
}

async function handleLogout() {
  try {
    await authApi.logout()
  } catch (e) {
    // 会话可能已经过期，登出接口失败也要清掉本地登录态
    console.warn('[登出接口失败]', e.message)
  }
  clearSession()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

const handleSelect = (index) => {
  router.push(index)
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.el-menu-vertical {
  border-right: none;
}
</style>