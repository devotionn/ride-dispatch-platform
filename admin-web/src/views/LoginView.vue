<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { loginAdmin } from '../api/auth'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit(): Promise<void> {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await loginAdmin(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/orders'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-intro">
      <div class="login-brand-mark">R</div>
      <p>RIDE DISPATCH PLATFORM</p>
      <h1>让人工调度更清楚、更快、更可追溯。</h1>
      <ul>
        <li>实时查看待接单与待确认订单</li>
        <li>按上车点筛选附近有效司机</li>
        <li>派单、拒单、改派全过程留痕</li>
      </ul>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <p class="login-kicker">调度管理后台</p>
        <h2>登录工作台</h2>
        <p class="login-caption">使用管理员、调度员或财务账号登录。</p>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入后台账号" @keyup.enter="submit" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" placeholder="请输入密码" @keyup.enter="submit" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="login-submit" @click="submit">进入调度后台</el-button>
        </el-form>
        <small class="login-security-note">登录凭证由服务端可撤销会话控制，退出后立即失效。</small>
      </div>
    </section>
  </div>
</template>
