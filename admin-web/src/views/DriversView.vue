<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { createDriver, getDriverDetail, getDriverQr, listDrivers, updateDriver, updateDriverStatus } from '../api/drivers'
import type { DriverDetailView } from '../api/drivers'
import type { DriverView } from '../domain/types'

const loading = ref(false)
const drivers = ref<DriverView[]>([])
const createOpen = ref(false)
const createLoading = ref(false)
const search = ref('')
const editOpen = ref(false)
const editLoading = ref(false)
const qrOpen = ref(false)
const qrLoading = ref(false)
const qrPreview = ref<{ name: string; shortCode: string; path: string; imageDataUrl: string } | null>(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const driverDetail = ref<DriverDetailView | null>(null)
const editingDriver = ref<DriverView | null>(null)
const editForm = reactive({
  name: '', mobile: '', password: '', maxPassengers: 4, availablePassengers: 4, plateNo: '', brandModel: '',
})

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

function openEdit(driver: DriverView): void {
  editingDriver.value = driver
  editForm.name = driver.name
  editForm.mobile = driver.mobile
  editForm.password = ''
  editForm.maxPassengers = driver.maxPassengers
  editForm.availablePassengers = driver.availablePassengers
  editForm.plateNo = driver.plateNo ?? ''
  editForm.brandModel = driver.brandModel ?? ''
  editOpen.value = true
}

async function submitEdit(): Promise<void> {
  const driver = editingDriver.value
  if (!driver) return
  if (!editForm.name.trim() || !/^1\d{10}$/.test(editForm.mobile) || !editForm.plateNo.trim()) {
    ElMessage.warning('请填写正确的姓名、手机号和车牌号')
    return
  }
  if (editForm.password && editForm.password.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  if (editForm.availablePassengers > editForm.maxPassengers) {
    ElMessage.warning('当前可接人数不能超过车辆最大载客人数')
    return
  }
  editLoading.value = true
  try {
    await updateDriver(driver.id, {
      name: editForm.name.trim(), mobile: editForm.mobile, password: editForm.password || undefined,
      maxPassengers: editForm.maxPassengers, availablePassengers: editForm.availablePassengers,
      plateNo: editForm.plateNo.trim(), brandModel: editForm.brandModel.trim() || undefined,
    })
    ElMessage.success('司机资料已更新')
    editOpen.value = false
    await load()
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    editLoading.value = false
  }
}

async function toggleAccount(driver: DriverView): Promise<void> {
  const next = driver.accountStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(next === 'ACTIVE' ? '确认启用该司机账号？' : '停用后司机将立即离线且不能接新单，确认继续？', '确认操作', {
      type: next === 'ACTIVE' ? 'info' : 'warning',
    })
    await updateDriverStatus(driver.id, next)
    ElMessage.success(next === 'ACTIVE' ? '司机账号已启用' : '司机账号已停用')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(messageOf(error))
  }
}

async function showQr(driver: DriverView): Promise<void> {
  qrLoading.value = true
  try {
    const qr = await getDriverQr(driver.id)
    qrPreview.value = { name: driver.name, shortCode: qr.shortCode, path: qr.path, imageDataUrl: qr.imageDataUrl }
    qrOpen.value = true
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    qrLoading.value = false
  }
}

async function showDetail(driver: DriverView): Promise<void> {
  detailLoading.value = true
  try {
    driverDetail.value = await getDriverDetail(driver.id)
    detailOpen.value = true
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    detailLoading.value = false
  }
}

function qrLink(path: string): string {
  const configured = (import.meta.env.VITE_PASSENGER_H5_BASE_URL ?? '').trim().replace(/\/$/, '')
  return `${configured || window.location.origin}${path}`
}

async function copyQrLink(): Promise<void> {
  if (!qrPreview.value) return
  await navigator.clipboard.writeText(qrLink(qrPreview.value.path))
  ElMessage.success('司机专属下单链接已复制')
}

function downloadQr(): void {
  const qr = qrPreview.value
  if (!qr) return
  const link = document.createElement('a')
  link.href = qr.imageDataUrl
  link.download = `driver-${qr.shortCode}-qr.png`
  link.click()
}

function money(fen: number): string {
  return `¥${(fen / 100).toFixed(2)}`
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
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :loading="detailLoading" @click="showDetail(scope.row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="primary" :loading="qrLoading" @click="showQr(scope.row)">查看二维码</el-button>
            <el-button link type="primary" @click="copyDriverLink(scope.row)">复制</el-button>
            <el-button link :type="scope.row.accountStatus === 'ACTIVE' ? 'danger' : 'success'" @click="toggleAccount(scope.row)">{{ scope.row.accountStatus === 'ACTIVE' ? '停用' : '启用' }}</el-button>
          </template>
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

    <el-dialog v-model="editOpen" title="编辑司机与车辆" width="min(680px, 92vw)" destroy-on-close>
      <el-form label-position="top">
        <div class="form-grid two">
          <el-form-item label="司机姓名"><el-input v-model="editForm.name" maxlength="80" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="editForm.mobile" maxlength="11" /></el-form-item>
        </div>
        <div class="form-grid two">
          <el-form-item label="新密码（不修改请留空）"><el-input v-model="editForm.password" type="password" show-password maxlength="100" /></el-form-item>
          <el-form-item label="车牌号"><el-input v-model="editForm.plateNo" maxlength="32" /></el-form-item>
        </div>
        <div class="form-grid two">
          <el-form-item label="品牌 / 车型"><el-input v-model="editForm.brandModel" maxlength="120" /></el-form-item>
          <el-form-item label="车辆最大载客人数"><el-input-number v-model="editForm.maxPassengers" :min="1" :max="20" /></el-form-item>
        </div>
        <el-form-item label="当前可接人数"><el-input-number v-model="editForm.availablePassengers" :min="0" :max="editForm.maxPassengers" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="submitEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="qrOpen" :title="`${qrPreview?.name ?? ''} 专属二维码`" width="min(420px, 92vw)">
      <div v-if="qrPreview" class="qr-dialog">
        <img :src="qrPreview.imageDataUrl" :alt="`${qrPreview.name} 司机专属二维码`" class="driver-qr-image" />
        <strong>{{ qrPreview.shortCode }}</strong>
        <p>{{ qrLink(qrPreview.path) }}</p>
        <div class="qr-actions">
          <el-button @click="copyQrLink">复制链接</el-button>
          <el-button type="primary" @click="downloadQr">下载 PNG</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="detailOpen" :title="`${driverDetail?.driver.name ?? ''} 司机详情`" width="min(760px, 94vw)">
      <div v-if="driverDetail" class="driver-detail">
        <div class="metric-strip detail-metrics">
          <div><strong>{{ driverDetail.completedOrderCount }}</strong><span>完成订单</span></div>
          <div><strong>{{ money(driverDetail.businessIncome) }}</strong><span>业务收入</span></div>
          <div><strong>{{ money(driverDetail.availableBalance) }}</strong><span>可提现余额</span></div>
        </div>
        <section>
          <h3>当前订单（{{ driverDetail.activeOrders.length }}）</h3>
          <el-table :data="driverDetail.activeOrders" size="small" empty-text="暂无当前订单">
            <el-table-column prop="orderNo" label="订单号" />
            <el-table-column prop="status" label="状态" width="150" />
            <el-table-column label="金额" width="120"><template #default="scope">{{ scope.row.finalAmount == null ? '—' : money(scope.row.finalAmount) }}</template></el-table-column>
          </el-table>
        </section>
        <section>
          <h3>提现记录</h3>
          <el-table :data="driverDetail.withdrawals" size="small" empty-text="暂无提现记录">
            <el-table-column prop="withdrawalNo" label="提现单" />
            <el-table-column label="金额" width="110"><template #default="scope">{{ money(scope.row.amount) }}</template></el-table-column>
            <el-table-column prop="status" label="状态" width="150" />
            <el-table-column prop="account" label="收款账号" />
          </el-table>
        </section>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.qr-dialog {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.driver-qr-image {
  width: 260px;
  height: 260px;
  image-rendering: pixelated;
  border: 1px solid var(--admin-line);
  border-radius: 14px;
}

.qr-dialog p {
  max-width: 100%;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--admin-muted);
  font-size: 12px;
  text-align: center;
}

.qr-actions {
  display: flex;
  gap: 10px;
}

.driver-detail {
  display: grid;
  gap: 20px;
}

.detail-metrics {
  margin: 0;
}

.driver-detail h3 {
  margin: 0 0 10px;
  color: var(--admin-ink);
  font-size: 15px;
}
</style>
