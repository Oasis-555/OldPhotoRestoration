import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from '@/api/axios'

export const useUserStore = defineStore('user', () => {
  const userId = ref(localStorage.getItem('userId') ? Number(localStorage.getItem('userId')) : null)
  const userName = ref(localStorage.getItem('userName') || '')
  const email = ref(localStorage.getItem('email') || '')
  const token = ref(localStorage.getItem('token') || '')
  const userType = ref(localStorage.getItem('userType') ? Number(localStorage.getItem('userType')) : null)

  const isAuthenticated = computed(() => !!token.value)

  // 初始化时恢复 axios header
  if (token.value) {
    axios.defaults.headers.common['Authorization'] = `Bearer ${token.value}`
  }

  const login = async (credentials) => {
    const response = await axios.post('/auth/login', credentials)
    if (response.data.code !== 200) {
      throw new Error(response.data.message || '登录失败')
    }
    const data = response.data.data

    userId.value = data.userId
    userName.value = data.userName
    email.value = data.email
    token.value = data.token
    userType.value = data.userType

    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', String(data.userId))
    localStorage.setItem('userName', data.userName)
    localStorage.setItem('email', data.email || '')
    localStorage.setItem('userType', String(data.userType || ''))

    axios.defaults.headers.common['Authorization'] = `Bearer ${data.token}`
    return data
  }

  const register = async (credentials) => {
    const response = await axios.post('/auth/register', credentials)
    if (response.data.code !== 200) {
      throw new Error(response.data.message || '注册失败')
    }
    return response.data.data
  }

  const logout = () => {
    userId.value = null
    userName.value = ''
    email.value = ''
    token.value = ''
    userType.value = null

    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('userName')
    localStorage.removeItem('email')
    localStorage.removeItem('userType')
    delete axios.defaults.headers.common['Authorization']
  }

  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
    axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`
  }

  const loadUserInfo = async () => {
    if (!token.value) return
    try {
      const response = await axios.get('/auth/me')
      const user = response.data.data
      userId.value = user.id
      userName.value = user.userName
      email.value = user.email
      userType.value = user.userType
    } catch (error) {
      logout()
    }
  }

  return {
    userId,
    userName,
    email,
    token,
    userType,
    isAuthenticated,
    login,
    register,
    logout,
    setToken,
    loadUserInfo
  }
})
