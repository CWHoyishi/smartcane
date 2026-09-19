<template>
  <!-- 登录页不套侧边栏框架 -->
  <router-view v-if="isLoginPage" />
  <el-container v-else class="layout">
    <el-aside v-if="!isNarrow" class="sidebar" :width="collapsed ? '64px' : '200px'">
      <div class="brand">
        <div class="brand__logo">杖</div>
        <div v-if="!collapsed" class="brand__name">智能拐杖系统</div>
      </div>

      <el-menu
        class="sidebar__menu"
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="transparent"
        text-color="#bfcbd9"
        active-text-color="#ffffff"
        @select="handleSelect"
      >
        <template v-for="group in visibleGroups" :key="group.label">
          <div v-if="!collapsed" class="sidebar__group">{{ group.label }}</div>
          <el-menu-item
            v-for="item in group.items"
            :key="item.path"
            :index="item.path"
            :title="collapsed ? item.title : ''"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>

      <!-- 用户信息放侧栏底部：顶栏留给页面标题，避免两处争抢注意力 -->
      <div class="sidebar__user" :class="{ 'sidebar__user--mini': collapsed }">
        <div class="sidebar__user-name">{{ currentUser.realName || currentUser.username || '未登录' }}</div>
        <div class="sidebar__user-role">{{ isAdminUser ? '管理员' : '监护人' }}</div>
        <el-button class="sidebar__logout" link @click="handleLogout">退出登录</el-button>
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="topbar__title">{{ pageTitle }}</div>
        <div class="topbar__right">
          <el-dropdown v-if="isNarrow" trigger="click" @command="handleSelect">
            <el-button icon="Menu">导航</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="item in flatMenus" :key="item.path" :command="item.path">
                  {{ item.title }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span class="topbar__user">{{ currentUser.realName || currentUser.username }}</span>
          <el-tag :type="isAdminUser ? 'danger' : 'success'" size="small" effect="light">
            {{ isAdminUser ? '管理员' : '监护人' }}
          </el-tag>
          <el-button v-if="isNarrow" link type="primary" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { clearSession, getUser, updateUser } from '@/utils/auth'

/** 菜单分组：分组只在视觉上分区，权限判断仍然逐项做 */
const MENU_GROUPS = [
  {
    label: '监控',
    items: [
      { path: '/latest', title: '实时监测', icon: 'Location' },
      { path: '/map', title: '实时地图', icon: 'MapLocation' },
      { path: '/sensor', title: '传感器数据', icon: 'DataLine' }
    ]
  },
  { label: '健康', items: [{ path: '/health-stat', title: '健康统计', icon: 'TrendCharts' }] },
  { label: '告警', items: [{ path: '/fall', title: '告警处理', icon: 'Warning' }] },
  { label: '系统', items: [{ path: '/device', title: '设备管理', icon: 'Monitor', adminOnly: true }] }
]

const router = useRouter()
const route = useRoute()
const currentUser = ref(getUser() || {})

const isLoginPage = computed(() => route.path === '/login')
const isAdminUser = computed(() => currentUser.value.role === 'ADMIN')
const activeMenu = computed(() => route.path)

/** 视口宽度：<1200 侧栏收成图标，<768 侧栏收起改用顶栏下拉导航 */
const viewportWidth = ref(window.innerWidth)
const onResize = () => {
  viewportWidth.value = window.innerWidth
}
const collapsed = computed(() => viewportWidth.value < 1200)
const isNarrow = computed(() => viewportWidth.value < 768)

// 菜单只是可见性控制，越权由后端拦截；设备增删改是管理员专属，监护人看不到入口
const visibleGroups = computed(() =>
  MENU_GROUPS.map((group) => ({
    label: group.label,
    items: group.items.filter((item) => !item.adminOnly || isAdminUser.value)
  })).filter((group) => group.items.length > 0)
)

const flatMenus = computed(() => visibleGroups.value.flatMap((group) => group.items))

const pageTitle = computed(() => (flatMenus.value.find((item) => item.path === route.path) || {}).title || '')

// 登录成功是同一次页面加载内的路由切换，App 不会重新挂载（onMounted 只跑一次），
// 所以监听「进入业务页」：先读本地缓存把菜单点亮，再异步回查角色
watch(
  isLoginPage,
  (onLoginPage) => {
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
  if (index !== route.path) {
    router.push(index)
  }
}

onMounted(() => {
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<style>
.layout {
  height: 100vh;
}

/* ===== 侧栏（保持深色，见 tokens.css 的 --sc-aside-*） ===== */
.sidebar {
  display: flex;
  flex-direction: column;
  background-color: var(--sc-aside-bg);
  transition: width 0.2s ease-out;
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand__logo {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--sc-primary);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand__name {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
}

.sidebar__menu {
  flex: 1;
  border-right: none;
  padding: 8px;
  overflow-y: auto;
}

.sidebar__group {
  padding: 14px 12px 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.35);
  letter-spacing: 1px;
}

.sidebar__menu .el-menu-item {
  height: 40px;
  line-height: 40px;
  border-radius: 6px;
  margin-bottom: 2px;
}

.sidebar__menu .el-menu-item:hover {
  background-color: var(--sc-aside-bg-hover);
}

.sidebar__menu .el-menu-item.is-active {
  background-color: var(--sc-primary);
}

.sidebar__user {
  padding: 14px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--sc-aside-text);
}

.sidebar__user--mini {
  padding: 14px 8px;
  text-align: center;
}

.sidebar__user-name {
  font-size: 13px;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar__user-role {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}

.sidebar__logout {
  margin-top: 8px;
  color: var(--sc-aside-text);
  font-size: 12px;
}

.sidebar__logout:hover {
  color: #fff;
}

/* ===== 顶栏与内容区 ===== */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 56px;
  padding: 0 20px;
  background: var(--sc-surface);
  border-bottom: 1px solid var(--sc-border);
}

.topbar__title {
  font-size: 16px;
  font-weight: 600;
}

.topbar__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar__user {
  font-size: 13px;
  color: var(--sc-text-2);
}

.content {
  padding: 20px;
  background: var(--sc-bg);
  /* 主内容不随大屏无限拉伸，超过 1440px 后居中留白 */
  overflow-y: auto;
}

.content > * {
  max-width: 1440px;
  margin: 0 auto;
  width: 100%;
}
</style>