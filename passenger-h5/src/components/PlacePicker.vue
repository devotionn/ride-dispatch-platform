<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { showFailToast } from 'vant'

import { searchPlaces, type PlaceCatalogItem } from '../api/places'
import type { GeoPointPayload } from '../domain/types'

const props = withDefaults(defineProps<{
  open: boolean
  title: string
  modelValue: GeoPointPayload | null
  allowCurrentLocation?: boolean
}>(), {
  allowCurrentLocation: false,
})

const emit = defineEmits<{
  close: []
  select: [point: GeoPointPayload]
}>()

const keyword = ref('')
const results = ref<PlaceCatalogItem[]>([])
const searching = ref(false)
const locating = ref(false)
const currentPosition = reactive<{ latitude: number | null; longitude: number | null; accuracy: number | null }>({
  latitude: null,
  longitude: null,
  accuracy: null,
})
const manualAddress = ref('')
let searchTimer: number | undefined

const hasCurrentPosition = computed(() => currentPosition.latitude !== null && currentPosition.longitude !== null)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    keyword.value = ''
    results.value = []
    manualAddress.value = props.modelValue?.address ?? ''
    currentPosition.latitude = props.modelValue?.latitude ?? null
    currentPosition.longitude = props.modelValue?.longitude ?? null
    currentPosition.accuracy = props.modelValue?.accuracyMeters ?? null
  },
)

watch(keyword, () => {
  if (searchTimer !== undefined) window.clearTimeout(searchTimer)
  const q = keyword.value.trim()
  if (q.length < 2) {
    results.value = []
    return
  }
  searchTimer = window.setTimeout(() => void runSearch(q), 350)
})

async function runSearch(query: string): Promise<void> {
  searching.value = true
  try {
    results.value = await searchPlaces(query)
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '地点搜索失败')
  } finally {
    searching.value = false
  }
}

function choosePlace(place: PlaceCatalogItem): void {
  emit('select', {
    address: place.addressText || place.name,
    latitude: place.latitude ?? null,
    longitude: place.longitude ?? null,
    placeId: place.id,
    source: 'PLACE_CATALOG',
    coordinateSystem: place.latitude != null && place.longitude != null ? 'WGS84' : undefined,
  })
}

function locate(): void {
  if (!navigator.geolocation) {
    showFailToast('当前浏览器不支持定位，请手工填写上车地点')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      currentPosition.latitude = position.coords.latitude
      currentPosition.longitude = position.coords.longitude
      currentPosition.accuracy = position.coords.accuracy
      locating.value = false
      if (!manualAddress.value.trim()) {
        manualAddress.value = '我的当前位置'
      }
    },
    (error) => {
      locating.value = false
      const message = error.code === error.PERMISSION_DENIED
        ? '定位权限未开启，可继续手工填写上车地点'
        : '暂时无法获取当前位置，可继续手工填写地点'
      showFailToast(message)
    },
    { enableHighAccuracy: true, timeout: 10_000, maximumAge: 30_000 },
  )
}

function confirmCurrent(): void {
  const address = manualAddress.value.trim()
  if (!hasCurrentPosition.value) {
    showFailToast('请先获取当前位置')
    return
  }
  if (!address) {
    showFailToast('请补充上车位置说明，例如“小区北门”')
    return
  }
  emit('select', {
    address,
    latitude: currentPosition.latitude,
    longitude: currentPosition.longitude,
    accuracyMeters: currentPosition.accuracy,
    source: 'BROWSER_LOCATION',
    coordinateSystem: 'WGS84',
  })
}

function confirmManual(): void {
  const address = manualAddress.value.trim()
  if (!address) {
    showFailToast('请填写地点名称或详细地址')
    return
  }
  emit('select', {
    address,
    latitude: null,
    longitude: null,
    source: 'MANUAL',
  })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="place-picker-overlay" role="dialog" aria-modal="true" :aria-label="title">
      <section class="place-picker-sheet">
        <header class="place-picker-header">
          <div>
            <p class="section-kicker">地点选择</p>
            <h2>{{ title }}</h2>
          </div>
          <button type="button" class="place-close" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <button v-if="allowCurrentLocation" type="button" class="locate-card" :disabled="locating" @click="locate">
          <strong>{{ locating ? '正在定位…' : '📍 使用我的当前位置' }}</strong>
          <span>仅获取完成派单所需的当前位置坐标</span>
        </button>

        <div v-if="allowCurrentLocation && hasCurrentPosition" class="current-location-card">
          <strong>当前位置已获取</strong>
          <small>
            {{ currentPosition.longitude?.toFixed(6) }}, {{ currentPosition.latitude?.toFixed(6) }}
            <template v-if="currentPosition.accuracy"> · 精度约 {{ Math.round(currentPosition.accuracy) }}m</template>
          </small>
          <label>
            <span>补充上车位置</span>
            <input v-model="manualAddress" maxlength="255" placeholder="例如：XX小区北门、酒店大厅" />
          </label>
          <button type="button" class="primary-action" @click="confirmCurrent">使用此位置</button>
        </div>

        <div class="place-search-block">
          <label class="place-search-label">
            <span>搜索常用地点</span>
            <input v-model="keyword" maxlength="80" placeholder="至少输入 2 个字，例如：扬州东" />
          </label>
          <small v-if="searching">搜索中…</small>
          <div v-if="results.length" class="place-results">
            <button v-for="place in results" :key="place.id" type="button" @click="choosePlace(place)">
              <strong>{{ place.name }}</strong>
              <span>{{ place.addressText }}</span>
              <small v-if="place.latitude == null || place.longitude == null">该地点暂无坐标，将按文字地址下单</small>
            </button>
          </div>
          <p v-else-if="keyword.trim().length >= 2 && !searching" class="empty-copy">没有匹配地点，可直接手工填写。</p>
        </div>

        <div class="manual-place-block">
          <label>
            <span>{{ allowCurrentLocation ? '或手工填写上车地点' : '手工填写目的地' }}</span>
            <input v-model="manualAddress" maxlength="255" placeholder="例如：扬州东站 / XX小区北门" />
          </label>
          <button type="button" class="secondary-action" @click="confirmManual">确认文字地点</button>
          <small>手工地点无需经纬度，仍可正常提交；上车点无坐标时由后台人工派单。</small>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.place-picker-overlay{position:fixed;inset:0;z-index:3000;background:rgba(15,23,42,.46);display:flex;align-items:flex-end;justify-content:center;padding-top:40px}.place-picker-sheet{width:min(100%,640px);max-height:92vh;overflow:auto;background:#fff;border-radius:24px 24px 0 0;padding:22px 18px calc(22px + env(safe-area-inset-bottom));box-shadow:0 -12px 40px rgba(15,23,42,.14)}.place-picker-header{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:18px}.place-picker-header h2{margin:4px 0 0;font-size:22px}.place-close{border:0;background:#f3f4f6;width:36px;height:36px;border-radius:50%;font-size:25px;line-height:1}.locate-card,.primary-action,.secondary-action,.place-results button{width:100%;border:0;text-align:left}.locate-card{background:#eefaf4;border-radius:16px;padding:16px;display:grid;gap:5px;color:#15372a}.locate-card strong{font-size:16px}.locate-card span,.manual-place-block small{font-size:12px;color:#64748b}.current-location-card,.place-search-block,.manual-place-block{margin-top:16px;padding:16px;border:1px solid #e5e7eb;border-radius:16px;display:grid;gap:10px}.current-location-card small{color:#64748b}.current-location-card label,.place-search-label,.manual-place-block label{display:grid;gap:7px;font-size:13px;color:#475569}.current-location-card input,.place-search-label input,.manual-place-block input{width:100%;box-sizing:border-box;border:1px solid #dbe1e8;border-radius:12px;padding:12px 13px;font-size:15px;outline:none}.primary-action,.secondary-action{padding:12px 14px;text-align:center;border-radius:12px;font-size:15px}.primary-action{background:#18a86b;color:#fff}.secondary-action{background:#f1f5f9;color:#1e293b}.place-results{display:grid;gap:8px}.place-results button{background:#f8fafc;border-radius:12px;padding:12px;display:grid;gap:4px}.place-results button strong{font-size:15px}.place-results button span,.place-results button small,.empty-copy{font-size:12px;color:#64748b}.empty-copy{margin:0}.section-kicker{margin:0;font-size:12px;color:#16a36a;font-weight:700;letter-spacing:.08em}
</style>
