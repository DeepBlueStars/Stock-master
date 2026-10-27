// 前端运行时配置占位文件。
// 生产部署时由 deploy-stockai.sh 根据服务器外部 .env（优先）或 tar 包内 .env 重新生成。
// 未部署运行时配置时保持为空对象，应用会回退到构建时的 VITE_API_BASE_URL。
window.__STOCKAI_ENV__ = window.__STOCKAI_ENV__ || {}
