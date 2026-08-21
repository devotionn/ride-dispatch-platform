<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { showFailToast } from 'vant'

import { createAmapSession, isAmapConfigured } from '../map/amap'
import type { MapPickerSession, MapPlace, MapPoint } from '../map/types'

const props = defineProps<{
  open: boolean
  title: string
  modelValue: MapPoint | null
}>()

const emit = defineEmits<{
  close: []
  select: [point: MapPoint]
}>()

const mapContainer = ref<HTMLDivElement | null>(null)
const selected = ref<MapPoint | null>(null)
const keyword = ref('')
const results = ref<MapPlace[]>([])
const loadingMap = ref(false)
const searching = ref(false)
const locating = ref(false)
const loadError = ref('')
const configured = isAmapConfigured()
const manual = reactive({ address: '', latitude: '', longitude: '' })
let session: MapPickerSession | null = null
let previousBodyOverflow = ''

const fallbackMode = computed(() => !configured || Boolean(loadError.value))

watch(
  () => props.open,
  async (open) => {
    if (!open) {
      destroySession()
      restoreBodyScroll()
      return
    }
    selected.value = props.modelValue ? { ...props.modelValue } : null
    manual.address = props.modelValue?.address ?? ''
    manual.latitude = props.modelValue ? String(props.modelValue.latitude) : ''
    manual.longitude = props.modelValue ? String(props.modelValue.longitude) : ''
    keyword.value = ''
    results.value = []
    loadError.value = ''
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    if (!configured) return
    loadingMap.value = true
    await nextTick()
    try {
      if (!mapContainer.value) throw new Error('地图容器初始化失败')
      session = await createAmapSession(mapContainer.value, selected.value, (point) => {
        selected.value = point
      })
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '地图加载失败'
    } finally {
      loadingMap.value = false
    }
  },
)

onBeforeUnmount(() => {
  destroySession()
  restoreBodyScroll()
})

function close(): void {
  emit('close')
}

async function search(): Promise<void> {
  if (!session || !keyword.value.trim()) return
  searching.value = true
  try {
    results.value = await session.search(keyword.value.trim())
    if (results.value.length === 0) showFailToast('没有找到匹配地点')
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '地点搜索失败')
  } finally {
    searching.value = false
  }
}

function choose(place: MapPlace): void {
  selected.value = {
    address: place.address || place.name,
    latitude: place.latitude,
    longitude: place.longitude,
  }
  session?.setPoint(selected.value)
  results.value = []
}

async function locate(): Promise<void> {
  if (!session) return
  locating.value = true
  try {
    selected.value = await session.locate()
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '定位失败')
  } finally {
    locating.value = false
  }
}

function confirmMapPoint(): void {
  if (!selected.value) {
    showFailToast('请先搜索、定位或点击地图选择地点')
    return
  }
  emit('select', selected.value)
}

function confirmManualPoint(): void {
  const latitude = Number(manual.latitude)
  const longitude = Number(manual.longitude)
  if (!manual.address.trim()) {
    showFailToast('请填写地点名称或详细地址')
    return
  }
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    showFailToast('纬度格式不正确')
    return
  }
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    showFailToast('经度格式不正确')
    return
  }
  emit('select', {
    address: manual.address.trim(),
    latitude,
    longitude,
  })
}

function destroySession(): void {
  session?.destroy()
  session = null
}

function restoreBodyScroll(): void {
  document.body.style.overflow = previousBodyOverflow
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="map-picker-overlay" role="dialog" aria-modal="true" :aria-label="title">
      <section class="map-picker-sheet">
        <header class="map-picker-header">
          <div>
            <p class="section-kicker">地图选点</p>
            <h2>{{ title }}</h2>
          </div>
          <button class="map-close-button" type="button" aria-label="关闭" @click="close">×</button>
        </header>

        <template v-if="!fallbackMode">
          <form class="map-search" @submit.prevent="search">
            <input v-model="keyword" maxlength="80" placeholder="搜索小区、商场、车站或详细地址" />
            <button type="submit" :disabled="searching">{{ searching ? '搜索中' : '搜索' }}</button>
          </form>

          <div v-if="results.length" class="map-search-results">
            <button v-for="place in results" :key="`${place.longitude}-${place.latitude}-${place.name}`" type="button" @click="choose(place)">
              <strong>{{ place.name }}</strong>
              <span>{{ place.address }}</span>
            </button>
          </div>

          <div class="map-canvas-wrap">
            <div ref="mapContainer" class="map-canvas" />
            <div v-if="loadingMap" class="map-loading">正在加载地图…</div>
          </div>

          <div class="map-toolbar">
            <button type="button" :disabled="locating || loadingMap" @click="locate">
              {{ locating ? '定位中…' : '定位到我' }}
            </button>
            <span>也可以直接点击地图选点</span>
          </div>

          <div class="map-selected-card" :class="{ empty: !selected }">
            <template v-if="selected">
              <strong>{{ selected.address }}</strong>
              <small>{{ selected.longitude.toFixed(6) }}, {{ selected.latitude.toFixed(6) }}</small>
            </template>
            <template v-else>
              <strong>尚未选择地点</strong>
              <small>搜索、定位或点击地图后再确认</small>
            </template>
          </div>

          <button class="map-confirm-button" type="button" @click="confirmMapPoint">确认此地点</button>
        </template>

        <template v-else>
          <div class="map-fallback-note">
            <strong>{{ configured ? '地图暂时不可用' : '地图服务尚未配置' }}</strong>
            <p>{{ loadError || '可先手工填写地址与坐标完成联调；生产环境配置高德 Key 后自动启用地图选点。' }}</p>
          </div>
          <label class="manual-map-field">
            <span>地点名称 / 详细地址</span>
            <input v-model="manual.address" maxlength="255" placeholder="例如：扬州东站" />
          </label>
          <div class="manual-coordinate-grid">
            <label class="manual-map-field">
              <span>经度</span>
              <input v-model="manual.longitude" inputmode="decimal" placeholder="119.5080000" />
            </label>
            <label class="manual-map-field">
              <span>纬度</span>
              <input v-model="manual.latitude" inputmode="decimal" placeholder="32.3910000" />
            </label>
          </div>
          <button class="map-confirm-button" type="button" @click="confirmManualPoint">确认手工地点</button>
        </template>
      </section>
    </div>
  </Teleport>
</template>
