<template>
  <el-container style="height: 100vh">
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
        <el-menu-item index="/device">
          <el-icon><Monitor /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/sensor">
          <el-icon><DataLine /></el-icon>
          <span>传感器数据</span>
        </el-menu-item>
        <el-menu-item index="/latest">
          <el-icon><Location /></el-icon>
          <span>实时监测</span>
        </el-menu-item>
        <el-menu-item index="/fall">
          <el-icon><Warning /></el-icon>
          <span>告警处理</span>
        </el-menu-item>
        <el-menu-item index="/health-stat">
          <el-icon><TrendCharts /></el-icon>
          <span>健康统计</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background-color: white; border-bottom: 1px solid #e0e0e0; display: flex; align-items: center; justify-content: space-between">
        <div style="font-size: 20px; font-weight: 500">{{ pageTitle }}</div>
      </el-header>
      <el-main style="background-color: #f0f2f5">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => {
  const titles = {
    '/device': '设备管理',
    '/sensor': '传感器数据',
    '/latest': '实时监测',
    '/fall': '告警处理',
    '/health-stat': '健康统计'
  }
  return titles[route.path] || ''
})

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
