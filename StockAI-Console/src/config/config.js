// 应用配置文件
// 用于管理所有外部配置项，如 API 地址、超时时间等

// 根据环境变量获取 API 基础地址
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8888';

export default {
  // API 基础地址
  baseUrl: API_BASE_URL
};
