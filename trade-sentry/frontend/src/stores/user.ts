import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get, post } from '@/api/request'
import type { User, LoginCredentials, RegisterData } from '@/types'

export const useUserStore = defineStore('user', () => {
  // State
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<User | null>(null)
  const isLoggedIn = computed(() => !!token.value)
  
  // Getters
  const balance = computed(() => ({
    total: userInfo.value?.totalBalance || 10000,
    available: userInfo.value?.availableBalance || 10000,
    frozen: userInfo.value?.frozenBalance || 0
  }))
  
  // Actions
  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }
  
  const clearToken = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }
  
  const login = async (credentials: LoginCredentials) => {
    try {
      // 使用URLSearchParams发送表单数据（OAuth2要求）
      const formData = new URLSearchParams()
      formData.append('username', credentials.username)
      formData.append('password', credentials.password)
      
      const res = await post<{ token: string; user: User }>('/auth/login', formData)
      setToken(res.token)
      userInfo.value = res.user
      return true
    } catch (error) {
      return false
    }
  }
  
  const register = async (data: RegisterData) => {
    try {
      await post('/auth/register', data)
      return true
    } catch (error) {
      return false
    }
  }
  
  const logout = () => {
    clearToken()
  }
  
  const fetchUserInfo = async () => {
    try {
      const res = await get<User>('/auth/profile')
      userInfo.value = res
      return res
    } catch (error) {
      clearToken()
      return null
    }
  }
  
  const fetchBalance = async () => {
    try {
      const res = await get<{ total: number; available: number; frozen: number }>('/account/balance')
      if (userInfo.value) {
        userInfo.value.totalBalance = res.total
        userInfo.value.availableBalance = res.available
        userInfo.value.frozenBalance = res.frozen
      }
      return res
    } catch (error) {
      return null
    }
  }
  
  // 初始化时获取用户信息
  if (token.value) {
    fetchUserInfo()
  }
  
  return {
    token,
    userInfo,
    isLoggedIn,
    balance,
    login,
    register,
    logout,
    fetchUserInfo,
    fetchBalance
  }
})
