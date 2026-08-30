import { createRouter, createWebHistory } from 'vue-router'

import AdminLayout from './layouts/AdminLayout.vue'
import BrandView from './views/BrandView.vue'
import CreateOrderView from './views/CreateOrderView.vue'
import DriversView from './views/DriversView.vue'
import LoginView from './views/LoginView.vue'
import OrdersView from './views/OrdersView.vue'
import PaymentsView from './views/PaymentsView.vue'
import PaymentExceptionsView from './views/PaymentExceptionsView.vue'
import WithdrawalsView from './views/WithdrawalsView.vue'
import OperationLogsView from './views/OperationLogsView.vue'
import PassengerSafetyView from './views/PassengerSafetyView.vue'
import PlacesView from './views/PlacesView.vue'
import { getSession, isAuthenticated } from './storage/auth'

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
        { path: 'places', name: 'places', component: PlacesView, meta: { title: '常用地点管理', authorities: ['ROLE_ADMIN', 'ROLE_DISPATCHER'] } },
        { path: 'brand', name: 'brand', component: BrandView, meta: { title: '平台品牌' } },
        { path: 'payments', name: 'payments', component: PaymentsView, meta: { title: '支付记录' } },
        { path: 'payment-exceptions', name: 'payment-exceptions', component: PaymentExceptionsView, meta: { title: '退款异常', authorities: ['ROLE_ADMIN', 'ROLE_FINANCE'] } },
        { path: 'passenger-safety', name: 'passenger-safety', component: PassengerSafetyView, meta: { title: '安全与投诉', authorities: ['ROLE_ADMIN', 'ROLE_DISPATCHER'] } },
        { path: 'withdrawals', name: 'withdrawals', component: WithdrawalsView, meta: { title: '提现审核' } },
        { path: 'operation-logs', name: 'operation-logs', component: OperationLogsView, meta: { title: '操作日志', authorities: ['ROLE_ADMIN', 'ROLE_DISPATCHER', 'ROLE_FINANCE'] } },
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
  const authorities = to.meta.authorities as string[] | undefined
  if (authorities && !authorities.includes(getSession()?.authority ?? '')) return { name: 'orders' }
  return true
})

export default router
