import axios from 'axios'

const instance = axios.create({
  baseURL: '/api',
  timeout: 300000,
})

// 请求拦截器：自动带上 Token
instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一错误处理
instance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      if (error.response.status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userId')
        localStorage.removeItem('userName')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      }
      // 从后端响应中提取错误信息
      const msg = error.response.data?.message || error.response.data?.error || '请求失败'
      error.message = msg
    } else if (error.request) {
      error.message = '无法连接到服务器，请检查后端服务是否启动'
    }
    return Promise.reject(error)
  }
)

export default instance
