<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { createAdminOrder } from '../api/orders'
import { searchPublicPlaces, type PlaceCatalogItem } from '../api/places'
import type { AdminCreateOrderPayload } from '../domain/types'

const router = useRouter()
const submitting = ref(false)

const form = reactive({
  passengerCount: 1,
  departureAt: null as Date | null,
  mobile: '',
  remark: '',
  pickupAddress: '',
  pickupLongitude: null as number | null,
  pickupLatitude: null as number | null,
  destinationAddress: '',
  destinationLongitude: null as number | null,
  destinationLatitude: null as number | null,
})

async function queryPlaces(query: string, callback: (items: Array<PlaceCatalogItem & { value: string }>) => void): Promise<void> {
  if (query.trim().length < 2) {
    callback([])
    return
  }
  try {
    const items = await searchPublicPlaces(query)
    callback(items.map((item) => ({ ...item, value: item.name })))
  } catch {
    callback([])
  }
}

function selectPickup(place: PlaceCatalogItem): void {
  form.pickupAddress = place.addressText || place.name
  form.pickupLatitude = place.latitude ?? null
  form.pickupLongitude = place.longitude ?? null
}

function selectDestination(place: PlaceCatalogItem): void {
  form.destinationAddress = place.addressText || place.name
  form.destinationLatitude = place.latitude ?? null
  form.destinationLongitude = place.longitude ?? null
}

function coordinatePairValid(latitude: number | null | undefined, longitude: number | null | undefined): boolean {
  if (latitude == null && longitude == null) return true
  if (latitude == null || longitude == null) return false
  return Number.isFinite(latitude) && latitude >= -90 && latitude <= 90
    && Number.isFinite(longitude) && longitude >= -180 && longitude <= 180
}

async function submit(): Promise<void> {
  if (!form.pickupAddress.trim()) {
    ElMessage.warning('请填写上车点')
    return
  }
  if (!form.destinationAddress.trim()) {
    ElMessage.warning('请填写目的地')
    return
  }
  if (!coordinatePairValid(form.pickupLatitude, form.pickupLongitude)) {
    ElMessage.warning('上车点经纬度必须同时填写且格式正确，或全部留空')
    return
  }
  if (!coordinatePairValid(form.destinationLatitude, form.destinationLongitude)) {
    ElMessage.warning('目的地经纬度必须同时填写且格式正确，或全部留空')
    return
  }
  if (!/^1\d{10}$/.test(form.mobile)) {
    ElMessage.warning('请输入正确的乘客手机号')
    return
  }
  if (!form.departureAt) {
    ElMessage.warning('请选择出发时间')
    return
  }

  const payload: AdminCreateOrderPayload = {
    pickup: {
      address: form.pickupAddress.trim(),
      latitude: form.pickupLatitude,
      longitude: form.pickupLongitude,
    },
    destination: {
      address: form.destinationAddress.trim(),
      latitude: form.destinationLatitude,
      longitude: form.destinationLongitude,
    },
    passengerCount: form.passengerCount,
    departureAt: form.departureAt.toISOString(),
    mobile: form.mobile,
    remark: form.remark.trim() || undefined,
  }

  submitting.value = true
  try {
    const result = await createAdminOrder(payload)
    ElMessage.success(`订单 ${result.orderNo} 已创建，已进入待接单`)
    await router.push({ name: 'orders' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建订单失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="workspace-page create-order-page">
    <header class="page-heading">
      <div>
        <p class="page-kicker">ASSISTED BOOKING</p>
        <h1>后台代客建单</h1>
        <p>适用于电话、现场或其他非 H5 渠道的乘客。地点可从常用地点库选择，也可直接手工填写。</p>
      </div>
      <div class="page-actions">
        <el-button @click="router.push({ name: 'orders' })">返回订单中心</el-button>
      </div>
    </header>

    <section class="panel-card create-order-workbench">
      <div class="create-order-route-grid">
        <div class="location-work-card">
          <div class="location-card-head">
            <div><span class="point-a">A</span><section><small>上车点</small><h3>{{ form.pickupAddress || '尚未填写' }}</h3></section></div>
          </div>
          <el-form label-position="top">
            <el-form-item label="搜索 / 输入上车地点">
              <el-autocomplete
                v-model="form.pickupAddress"
                :fetch-suggestions="queryPlaces"
                clearable
                style="width:100%"
                placeholder="输入至少 2 个字搜索常用地点，或直接填写"
                @select="selectPickup"
              >
                <template #default="{ item }">
                  <div><strong>{{ item.name }}</strong><small style="display:block;color:#909399">{{ item.addressText }}</small></div>
                </template>
              </el-autocomplete>
            </el-form-item>
            <div class="form-grid two">
              <el-form-item label="经度（选填）"><el-input-number v-model="form.pickupLongitude" :controls="false" :precision="7" /></el-form-item>
              <el-form-item label="纬度（选填）"><el-input-number v-model="form.pickupLatitude" :controls="false" :precision="7" /></el-form-item>
            </div>
            <el-alert v-if="form.pickupAddress && form.pickupLatitude == null && form.pickupLongitude == null" type="warning" :closable="false" show-icon>
              上车点暂无定位坐标：订单仍可创建，但无法自动计算 10km 内附近司机，需要人工选择司机。
            </el-alert>
          </el-form>
        </div>

        <div class="location-work-card">
          <div class="location-card-head">
            <div><span class="point-b">B</span><section><small>目的地</small><h3>{{ form.destinationAddress || '尚未填写' }}</h3></section></div>
          </div>
          <el-form label-position="top">
            <el-form-item label="搜索 / 输入目的地">
              <el-autocomplete
                v-model="form.destinationAddress"
                :fetch-suggestions="queryPlaces"
                clearable
                style="width:100%"
                placeholder="输入至少 2 个字搜索常用地点，或直接填写"
                @select="selectDestination"
              >
                <template #default="{ item }">
                  <div><strong>{{ item.name }}</strong><small style="display:block;color:#909399">{{ item.addressText }}</small></div>
                </template>
              </el-autocomplete>
            </el-form-item>
            <div class="form-grid two">
              <el-form-item label="经度（选填）"><el-input-number v-model="form.destinationLongitude" :controls="false" :precision="7" /></el-form-item>
              <el-form-item label="纬度（选填）"><el-input-number v-model="form.destinationLatitude" :controls="false" :precision="7" /></el-form-item>
            </div>
            <el-alert v-if="form.destinationAddress && form.destinationLatitude == null && form.destinationLongitude == null" type="info" :closable="false" show-icon>
              目的地仅有文字地址也可正常创建和履约。
            </el-alert>
          </el-form>
        </div>
      </div>

      <div class="create-order-passenger-card">
        <div class="drawer-section-heading"><div><span>乘客与时间</span><h3>补全预约信息</h3></div></div>
        <el-form label-position="top">
          <div class="form-grid three">
            <el-form-item label="乘车人数"><el-input-number v-model="form.passengerCount" :min="1" :max="20" /></el-form-item>
            <el-form-item label="出发时间"><el-date-picker v-model="form.departureAt" type="datetime" placeholder="选择日期与时间" style="width:100%" /></el-form-item>
            <el-form-item label="乘客手机号"><el-input v-model="form.mobile" maxlength="11" placeholder="11 位手机号" /></el-form-item>
          </div>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="选填，例如大件行李、老人乘车等" /></el-form-item>
        </el-form>
      </div>

      <div class="create-order-submit-row">
        <div><strong>创建后状态：待接单</strong><span>上车点有坐标时可自动筛选附近司机；无坐标时仍可人工派单。</span></div>
        <el-button type="primary" size="large" :loading="submitting" @click="submit">创建订单</el-button>
      </div>
    </section>
  </section>
</template>
