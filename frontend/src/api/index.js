import axios from 'axios'
import { auth } from '../stores/auth'

const api = axios.create({ baseURL: '/api' })

// 请求拦截：自动带 token
api.interceptors.request.use(cfg => {
  if (auth.token) cfg.headers['Authorization'] = `Bearer ${auth.token}`
  return cfg
})

// 响应拦截：401 跳登录
api.interceptors.response.use(
  r => r,
  err => {
    if (err.response?.status === 401) auth.logout()
    return Promise.reject(err)
  }
)

export default api
