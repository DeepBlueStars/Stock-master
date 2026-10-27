// 应用配置文件
// 用于管理所有外部配置项，如 API 地址、超时时间等

// 部署时由 deploy-stockai.sh 生成 /env.js，可在不重新打包的情况下覆盖构建时配置
const runtimeEnv = (typeof window !== 'undefined' && window.__STOCKAI_ENV__) || {}

// 优先级：运行时配置 > 构建时环境变量 > 本地默认值
const API_BASE_URL = runtimeEnv.VITE_API_BASE_URL || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8888';

export default {
  // API 基础地址
  baseUrl: API_BASE_URL
};
