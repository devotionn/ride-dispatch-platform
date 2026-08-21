import { createRouter, createWebHistory } from 'vue-router'

import AdminLayout from './layouts/AdminLayout.vue'
import CreateOrderView from './views/CreateOrderView.vue'
import DriversView from './views/DriversView.vue'
import LoginView from './views/LoginView.vue'
import OrdersView from './views/OrdersView.vue'
import { isAuthenticated } from './storage/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/',
      component: AdminLayout,
      children: [
        { path: '', redirect: '/orders' },
        { path: 'orders', name: 'orders', component: OrdersView, meta: { title: '订单调度' } },
        { path: 'orders/create', name: 'create-order', component: CreateOrderView, meta: { title: '后台代客建单' } },
        { path: 'drivers', name: 'drivers', component: DriversView, meta: { title: '司机管理' } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) {
    if (to.name === 'login' && isAuthenticated()) return { name: 'orders' }
    return true
  }
  if (!isAuthenticated()) return { name: 'login', query: { redirect: to.fullPath } }
  return true
})

export default router
