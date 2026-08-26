import { createRouter, createWebHistory } from 'vue-router'
import AssetConfig from '../components/AssetConfig/AssetConfig.vue'
import Dashboard from '../components/Dashboard.vue'
import Config from '../components/Config.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: '总览',
      component: AssetConfig
    },
    {
      path: '/stock',
      name: '股票',
      component: Dashboard
    },
    {
      path: '/config',
      name: '配置',
      component: Config
    }
  ]
})

export default router
