<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-logo">杖</div>
      <div class="login-title">智能拐杖系统</div>
      <div class="login-subtitle">账号密码登录</div>
      <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>
      <div class="login-tip">演示账号：admin / admin123（管理员）、guardian / guardian123（监护人）</div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { authApi } from '@/api'
import { setSession } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(() => {
  // 请求拦截器用整页跳转回到登录页，提示只能在这里补
  if (route.query.expired === '1') {
    ElMessage.warning('登录已过期，请重新登录')
  }
})

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    const res = await authApi.login({ ...form })
    setSession(res.data.token, res.data)
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || '/')
  } catch (e) {
    // 失败提示已由响应拦截器统一弹出，这里只负责恢复按钮状态
    console.warn('[登录失败]', e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--sc-bg);
}

.login-card {
  width: 380px;
  padding: 12px 8px;
}

.login-logo {
  width: 48px;
  height: 48px;
  margin: 0 auto 14px;
  border-radius: 12px;
  background: var(--sc-primary);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
}

.login-subtitle {
  margin: 8px 0 24px;
  font-size: 13px;
  color: #86909c;
  text-align: center;
}

.login-btn {
  width: 100%;
}

.login-tip {
  margin-top: 16px;
  font-size: 12px;
  color: #86909c;
  text-align: center;
}
</style>