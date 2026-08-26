import axios, { type AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { ApiResponse } from '@/types'

// 创建axios实例
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    const token = userStore.token
    
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    
    // 如果是URLSearchParams或FormData，不设置Content-Type，让axios自动设置
    if (config.data instanceof URLSearchParams || config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { code, message, data } = response.data
    
    if (code === 200) {
      return data
    } else {
      ElMessage.error(message || '请求失败')
      return Promise.reject(new Error(message))
    }
  },
  (error: AxiosError<ApiResponse>) => {
    const { response } = error
    
    if (response) {
      const { status, data } = response
      
      switch (status) {
        case 401:
          ElMessage.error('登录已过期，请重新登录')
          // 清除token并跳转到登录页
          const userStore = useUserStore()
          userStore.logout()
          window.location.href = '/login'
          break
        case 403:
          ElMessage.error('没有权限执行此操作')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误，请稍后重试')
          break
        default:
          ElMessage.error(data?.message || '网络错误')
      }
    } else {
      ElMessage.error('网络连接失败，请检查网络')
    }
    
    return Promise.reject(error)
  }
)

// 封装GET请求
export function get<T>(url: string, params?: object): Promise<T> {
  return request.get(url, { params }) as Promise<T>
}

// 封装POST请求
export function post<T>(url: string, data?: object): Promise<T> {
  return request.post(url, data) as Promise<T>
}

// 封装PUT请求
export function put<T>(url: string, data?: object): Promise<T> {
  return request.put(url, data) as Promise<T>
}

// 封装DELETE请求
export function del<T>(url: string, params?: object): Promise<T> {
  return request.delete(url, { params }) as Promise<T>
}

export default request
