import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearSession, getToken } from '@/utils/auth'

const request = axios.create({
  baseURL: '/api',
  timeout: 5000
})

request.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 显式导入 router 会形成 router → 视图 → api → request → router 的循环依赖，
// 所以这里用整页跳转完成「登录失效回登录页」
function redirectToLogin() {
  clearSession()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login?expired=1'
  }
}

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res
    }
    const isLogin = (response.config.url || '').startsWith('/auth/login')
    if (res.code === 401 && !isLogin) {
      // 登录态问题不弹通用错误：整页跳转后提示由登录页给出
      redirectToLogin()
    } else {
      ElMessage.error(res.message || '请求失败')
    }
    const err = new Error(res.message || '请求失败')
    err.code = res.code
    return Promise.reject(err)
  },
  error => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request