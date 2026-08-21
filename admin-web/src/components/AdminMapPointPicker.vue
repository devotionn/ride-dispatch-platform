<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { createAmapSession, isAmapConfigured } from '../map/amap'
import type { MapPlace, MapPoint, MapSession } from '../map/types'

const props = defineProps<{
  modelValue: boolean
  title: string
  point: MapPoint | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  select: [point: MapPoint]
}>()

const configured = isAmapConfigured()
const container = ref<HTMLDivElement | null>(null)
const selected = ref<MapPoint | null>(null)
const keyword = ref('')
const results = ref<MapPlace[]>([])
const loading = ref(false)
const searching = ref(false)
const locating = ref(false)
const loadError = ref('')
let session: MapSession | null = null
let cycle = 0

const unavailable = computed(() => !configured || Boolean(loadError.value))

watch(
  () => props.modelValue,
  async (open) => {
    const currentCycle = ++cycle
    if (!open) {
      destroy()
      return
    }
    selected.value = props.point ? { ...props.point } : null
    keyword.value = ''
    results.value = []
    loadError.value = ''
    if (!configured) return

    loading.value = true
    await nextTick()
    try {
      if (!container.value) throw new Error('地图容器初始化失败')
      const created = await createAmapSession(container.value, selected.value, (point) => {
        if (props.modelValue && currentCycle === cycle) selected.value = point
      })
      if (!props.modelValue || currentCycle !== cycle) {
        created.destroy()
        return
      }
      session = created
    } catch (error) {
      if (currentCycle === cycle) loadError.value = messageOf(error)
    } finally {
      if (currentCycle === cycle) loading.value = false
    }
  },
)

onBeforeUnmount(() => {
  cycle += 1
  destroy()
})

async function search(): Promise<void> {
  if (!session || !keyword.value.trim()) return
  searching.value = true
  try {
    results.value = await session.search(keyword.value.trim())
    if (!results.value.length) ElMessage.info('没有找到匹配地点')
  } catch (error) {
    ElMessage.error(messageOf(error))
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
    ElMessage.error(messageOf(error))
  } finally {
    locating.value = false
  }
}

function confirm(): void {
  if (!selected.value) {
    ElMessage.warning('请先搜索、定位或点击地图选择地点')
    return
  }
  emit('select', selected.value)
  emit('update:modelValue', false)
}

function close(): void {
  emit('update:modelValue', false)
}

function destroy(): void {
  session?.destroy()
  session = null
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '地图操作失败'
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="min(860px, 92vw)" destroy-on-close @close="close">
    <el-alert
      v-if="unavailable"
      type="warning"
      :closable="false"
      :title="loadError || '地图服务尚未配置，请先在环境变量中配置高德 Web Key 与安全代理。'"
    />

    <template v-else>
      <div class="admin-map-search">
        <el-input v-model="keyword" clearable placeholder="搜索小区、商场、车站或详细地址" @keyup.enter="search" />
        <el-button type="primary" :loading="searching" @click="search">搜索地点</el-button>
        <el-button :loading="locating" @click="locate">定位到我</el-button>
      </div>

      <div v-if="results.length" class="admin-map-results">
        <button v-for="place in results" :key="`${place.longitude}-${place.latitude}-${place.name}`" type="button" @click="choose(place)">
          <strong>{{ place.name }}</strong>
          <span>{{ place.address }}</span>
        </button>
      </div>

      <div class="admin-map-canvas-wrap">
        <div ref="container" class="admin-map-canvas" />
        <div v-if="loading" class="admin-map-loading">正在加载地图…</div>
      </div>

      <div class="admin-map-selected">
        <template v-if="selected">
          <div><span>已选地点</span><strong>{{ selected.address }}</strong></div>
          <small>{{ selected.longitude.toFixed(6) }}, {{ selected.latitude.toFixed(6) }}</small>
        </template>
        <template v-else>
          <div><span>尚未选择</span><strong>搜索、定位或点击地图选点</strong></div>
        </template>
      </div>
    </template>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :disabled="unavailable || !selected" @click="confirm">确认此地点</el-button>
    </template>
  </el-dialog>
</template>
