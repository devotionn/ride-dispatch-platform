import { createRouter, createWebHistory } from 'vue-router'

import OrderStatusView from './views/OrderStatusView.vue'
import PaymentView from './views/PaymentView.vue'
import RideCreateView from './views/RideCreateView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/ride' },
    { path: '/ride', name: 'public-ride', component: RideCreateView },
    { path: '/ride/d/:driverShortCode', name: 'driver-ride', component: RideCreateView },
    { path: '/order/:orderNo', name: 'order-status', component: OrderStatusView },
    { path: '/payment/:paymentToken', name: 'payment', component: PaymentView },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router
