const TOKEN_KEY = 'smartcane_token'
const USER_KEY = 'smartcane_user'

/** 会话 token；空串表示未登录 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY)) || null
  } catch (e) {
    // 本地缓存被手工改坏时按未登录处理，不能让整个应用起不来
    return null
  }
}

export function setSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  updateUser(user)
}

/** 只刷新用户信息（/auth/me 返回值不含 token，不能覆盖会话） */
export function updateUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user || {}))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}