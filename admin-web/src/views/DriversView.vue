<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { createDriver, listDrivers } from '../api/drivers'
import type { DriverView } from '../domain/types'

const loading = ref(false)
const drivers = ref<DriverView[]>([])
const createOpen = ref(false)
const createLoading = ref(false)
const search = ref('')

const form = reactive({
  driverNo: '',
  name: '',
  mobile: '',
  password: '',
  maxPassengers: 4,
  availablePassengers: 4,
  plateNo: '',
  brandModel: '',
})

const filteredDrivers = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return drivers.value
  return drivers.value.filter((driver) =>
    [driver.driverNo, driver.name, driver.mobile, driver.plateNo, driver.brandModel]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(keyword)),
  )
})

const availableCount = computed(() => drivers.value.filter((driver) => driver.accountStatus === 'ACTIVE' && driver.workStatus === 'AVAILABLE').length)
const staleOrPausedCount = computed(() => drivers.value.filter((driver) => driver.workStatus !== 'AVAILABLE').length)

onMounted(() => void load())

async function load(): Promise<void> {
  loading.value = true
  try {
    drivers.value = await listDrivers()
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (!form.driverNo.trim() || !form.name.trim() || !form.plateNo.trim()) {
    ElMessage.warning('请填写司机工号、姓名和车牌号')
    return
  }
  if (!/^1\d{10}$/.test(form.mobile)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (form.password.length < 8) {
    ElMessage.warning('司机初始密码至少 8 位')
    return
  }
  if (form.availablePassengers > form.maxPassengers) {
    ElMessage.warning('当前可接人数不能超过车辆最大载客人数')
    return
  }

  createLoading.value = true
  try {
    const created = await createDriver({
      driverNo: form.driverNo.trim(),
      name: form.name.trim(),
      mobile: form.mobile,
      password: form.password,
      maxPassengers: form.maxPassengers,
      availablePassengers: form.availablePassengers,
      plateNo: form.plateNo.trim(),
      brandModel: form.brandModel.trim() || undefined,
    })
    ElMessage.success(`司机 ${created.name} 已创建`)
    createOpen.value = false
    reset()
    await load()
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    createLoading.value = false
  }
}

function reset(): void {
  form.driverNo = ''
  form.name = ''
  form.mobile = ''
  form.password = ''
  form.maxPassengers = 4
  form.availablePassengers = 4
  form.plateNo = ''
  form.brandModel = ''
}

async function copyDriverLink(driver: DriverView): Promise<void> {
  const configured = (import.meta.env.VITE_PASSENGER_H5_BASE_URL ?? '').trim().replace(/\/$/, '')
  const base = configured || window.location.origin
  const url = `${base}/ride/d/${encodeURIComponent(driver.qrShortCode)}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('司机专属下单链接已复制')
  } catch {
    ElMessage.info(url)
  }
}

function accountText(driver: DriverView): string {
  if (driver.accountStatus !== 'ACTIVE') return '已停用'
  if (driver.workStatus === 'AVAILABLE') return '可接单'
  if (driver.workStatus === 'PAUSED') return '暂停接单'
  return '离线'
}

function statusType(driver: DriverView): 'success' | 'warning' | 'danger' | 'info' {
  if (driver.accountStatus !== 'ACTIVE') return 'danger'
  if (driver.workStatus === 'AVAILABLE') return 'success'
  if (driver.workStatus === 'PAUSED') return 'warning'
  return 'info'
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}
</script>

<template>
  <section class="workspace-page">
    <header class="page-heading">
      <div>
        <p class="page-kicker">DRIVER DIRECTORY</p>
        <h1>司机管理</h1>
        <p>司机状态、车辆容量和专属二维码都直接参与实际下单与人工派单。</p>
      </div>
      <div class="page-actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="createOpen = true">新增司机</el-button>
      </div>
    </header>

    <div class="metric-strip">
      <div><strong>{{ drivers.length }}</strong><span>司机总数</span></div>
      <div><strong>{{ availableCount }}</strong><span>当前可接单</span></div>
      <div><strong>{{ staleOrPausedCount }}</strong><span>暂停 / 离线</span></div>
    </div>

    <section class="panel-card">
      <div class="table-toolbar">
        <el-input v-model="search" clearable placeholder="搜索姓名、工号、手机号、车牌" style="width:320px" />
        <span>司机专属二维码只负责绑定司机进入下单页，不用于付款。</span>
      </div>

      <el-table v-loading="loading" :data="filteredDrivers" row-key="id">
        <el-table-column label="司机" min-width="155">
          <template #default="scope">
            <div class="stack-cell"><strong>{{ scope.row.name }}</strong><span>{{ scope.row.driverNo }}</span></div>
          </template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机号" width="135" />
        <el-table-column label="工作状态" width="115">
          <template #default="scope"><el-tag :type="statusType(scope.row)">{{ accountText(scope.row) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="可接人数" width="115">
          <template #default="scope"><strong>{{ scope.row.availablePassengers }}</strong> / {{ scope.row.maxPassengers }} 人</template>
        </el-table-column>
        <el-table-column label="车辆" min-width="180">
          <template #default="scope"><div class="stack-cell"><strong>{{ scope.row.plateNo || '未绑定车牌' }}</strong><span>{{ scope.row.brandModel || '车型未填写' }}</span></div></template>
        </el-table-column>
        <el-table-column prop="qrShortCode" label="二维码短码" min-width="175" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="scope"><el-button link type="primary" @click="copyDriverLink(scope.row)">复制专属下单链接</el-button></template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="createOpen" title="新增司机与车辆" width="min(680px, 92vw)" destroy-on-close @closed="reset">
      <el-form label-position="top">
        <div class="form-grid two">
          <el-form-item label="司机工号"><el-input v-model="form.driverNo" maxlength="50" placeholder="例如 D001" /></el-form-item>
          <el-form-item label="司机姓名"><el-input v-model="form.name" maxlength="80" /></el-form-item>
        </div>
        <div class="form-grid two">
          <el-form-item label="手机号"><el-input v-model="form.mobile" maxlength="11" /></el-form-item>
          <el-form-item label="初始密码"><el-input v-model="form.password" type="password" show-password maxlength="100" /></el-form-item>
        </div>
        <div class="form-grid two">
          <el-form-item label="车牌号"><el-input v-model="form.plateNo" maxlength="32" placeholder="例如 苏K12345" /></el-form-item>
          <el-form-item label="品牌 / 车型"><el-input v-model="form.brandModel" maxlength="120" placeholder="选填" /></el-form-item>
        </div>
        <div class="form-grid two">
          <el-form-item label="车辆最大载客人数"><el-input-number v-model="form.maxPassengers" :min="1" :max="20" /></el-form-item>
          <el-form-item label="当前可接人数"><el-input-number v-model="form.availablePassengers" :min="0" :max="form.maxPassengers" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submit">创建司机</el-button>
      </template>
    </el-dialog>
  </section>
</template>
