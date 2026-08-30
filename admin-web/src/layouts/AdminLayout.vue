<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { logoutAdmin } from '../api/auth'
import { getSession } from '../storage/auth'

const route = useRoute()
const router = useRouter()
const session = computed(() => getSession())

const authorityLabel = computed(() => {
  const value = session.value?.authority
  if (value === 'ROLE_ADMIN') return '管理员'
  if (value === 'ROLE_DISPATCHER') return '调度员'
  if (value === 'ROLE_FINANCE') return '财务'
  return '后台用户'
})

async function logout(): Promise<void> {
  try {
    await logoutAdmin()
  } catch {
    // Local session is cleared even when revoke request fails.
  }
  ElMessage.success('已退出登录')
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <span class="brand-mark">R</span>
        <div>
          <strong>Ride Dispatch</strong>
          <small>人工调度平台</small>
        </div>
      </div>

      <nav class="admin-nav">
        <router-link to="/orders" :class="{ active: route.path.startsWith('/orders') }">
          <span>订单调度</span>
          <small>接单 · 派单 · 履约</small>
        </router-link>
        <router-link to="/drivers" :class="{ active: route.path.startsWith('/drivers') }">
          <span>司机管理</span>
          <small>车辆 · 状态 · 二维码</small>
        </router-link>
        <router-link to="/brand" :class="{ active: route.path.startsWith('/brand') }">
          <span>平台品牌</span>
          <small>名称 · Logo</small>
        </router-link>
        <router-link v-if="session?.authority === 'ROLE_ADMIN' || session?.authority === 'ROLE_DISPATCHER'" to="/places" :class="{ active: route.path.startsWith('/places') }">
          <span>常用地点</span>
          <small>地点目录 · 文本搜索</small>
        </router-link>
        <router-link to="/payments" :class="{ active: route.path.startsWith('/payments') }">
          <span>支付记录</span>
          <small>支付单 · 回调 · 渠道</small>
        </router-link>
        <router-link v-if="session?.authority === 'ROLE_ADMIN' || session?.authority === 'ROLE_FINANCE'" to="/payment-exceptions" :class="{ active: route.path.startsWith('/payment-exceptions') }">
          <span>退款异常</span>
          <small>人工退款 · 留痕</small>
        </router-link>
        <router-link v-if="session?.authority === 'ROLE_ADMIN' || session?.authority === 'ROLE_DISPATCHER'" to="/passenger-safety" :class="{ active: route.path.startsWith('/passenger-safety') }">
          <span>安全与投诉</span>
          <small>乘客投诉 · 报警记录</small>
        </router-link>
        <router-link to="/withdrawals" :class="{ active: route.path.startsWith('/withdrawals') }">
          <span>提现审核</span>
          <small>冻结 · 驳回 · 打款</small>
        </router-link>
        <router-link v-if="session?.authority === 'ROLE_ADMIN' || session?.authority === 'ROLE_DISPATCHER' || session?.authority === 'ROLE_FINANCE'" to="/operation-logs" :class="{ active: route.path.startsWith('/operation-logs') }">
          <span>操作日志</span>
          <small>全局审计 · 导出</small>
        </router-link>
      </nav>

      <div class="sidebar-footnote">
        <span>当前角色</span>
        <strong>{{ authorityLabel }}</strong>
      </div>
    </aside>

    <div class="admin-workspace">
      <header class="admin-topbar">
        <div>
          <strong>{{ route.meta.title || (route.name === 'drivers' ? '司机管理' : '订单调度') }}</strong>
          <small>小车队人工调度 · 业务状态以服务端为准</small>
        </div>
        <el-button plain @click="logout">退出登录</el-button>
      </header>
      <main class="admin-main">
        <router-view />
      </main>
    </div>
  </div>
</template>
