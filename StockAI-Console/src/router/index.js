import { createRouter, createWebHistory } from 'vue-router'
import AssetConfig from '../components/AssetConfig/AssetConfig.vue'
import Dashboard from '../components/Dashboard.vue'
import Config from '../components/Config.vue'
import MarketOverview from '../components/MarketOverview.vue'
import TradePlan from '../components/TradePlan.vue'

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
    },
    {
      path: '/market',
      name: '市场',
      component: MarketOverview
    },
    {
      path: '/regime',
      redirect: '/market'
    },
    {
      path: '/screener',
      redirect: '/market'
    },
    {
      path: '/trade-plan',
      name: '交易计划',
      component: TradePlan
    }
  ]
})

export default router
