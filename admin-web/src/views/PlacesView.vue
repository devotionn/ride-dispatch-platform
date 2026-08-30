<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createPlace, listPlaces, setPlaceEnabled, updatePlace } from '../api/places'
import type { PlaceCatalogItem, PlaceCatalogPayload } from '../api/places'

const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const editing = ref<PlaceCatalogItem | null>(null)
const keyword = ref('')
const places = ref<PlaceCatalogItem[]>([])
const form = reactive<PlaceCatalogPayload>({ name: '', addressText: '', latitude: null, longitude: null, city: '', district: '', category: '', aliases: '' })

const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return places.value
  return places.value.filter((place) => [place.name, place.addressText, place.city, place.district, place.category, place.aliases]
    .some((value) => value?.toLowerCase().includes(query)))
})

onMounted(() => void load())

async function load(): Promise<void> {
  loading.value = true
  try { places.value = await listPlaces() } catch (error) { ElMessage.error(messageOf(error)) } finally { loading.value = false }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, { name: '', addressText: '', latitude: null, longitude: null, city: '', district: '', category: '', aliases: '' })
  dialogOpen.value = true
}

function openEdit(place: PlaceCatalogItem): void {
  editing.value = place
  Object.assign(form, { name: place.name, addressText: place.addressText, latitude: place.latitude ?? null, longitude: place.longitude ?? null, city: place.city ?? '', district: place.district ?? '', category: place.category ?? '', aliases: place.aliases ?? '' })
  dialogOpen.value = true
}

async function save(): Promise<void> {
  if (!form.name.trim() || !form.addressText.trim()) { ElMessage.warning('名称和详细地址不能为空'); return }
  if ((form.latitude == null) !== (form.longitude == null)) { ElMessage.warning('纬度和经度必须同时填写或同时留空'); return }
  if (form.latitude != null && (form.latitude < -90 || form.latitude > 90)) { ElMessage.warning('纬度必须在 -90 到 90 之间'); return }
  if (form.longitude != null && (form.longitude < -180 || form.longitude > 180)) { ElMessage.warning('经度必须在 -180 到 180 之间'); return }
  saving.value = true
  try {
    const payload = { ...form, name: form.name.trim(), addressText: form.addressText.trim() }
    if (editing.value) await updatePlace(editing.value.id, payload)
    else await createPlace(payload)
    ElMessage.success(editing.value ? '地点已更新' : '地点已创建')
    dialogOpen.value = false
    await load()
  } catch (error) { ElMessage.error(messageOf(error)) } finally { saving.value = false }
}

async function toggle(place: PlaceCatalogItem): Promise<void> {
  try { await setPlaceEnabled(place.id, !place.enabled); ElMessage.success(place.enabled ? '地点已停用' : '地点已启用'); await load() } catch (error) { ElMessage.error(messageOf(error)) }
}

function coordinate(place: PlaceCatalogItem): string { return place.latitude != null && place.longitude != null ? `${place.latitude}, ${place.longitude}` : '—' }
function time(value?: string | null): string { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
function messageOf(error: unknown): string { return error instanceof Error ? error.message : '操作失败，请稍后重试' }
</script>

<template>
  <section class="workspace-page">
    <header class="page-heading"><div><p class="page-kicker">PLACE CATALOG</p><h1>常用地点管理</h1><p>维护可搜索的文字地点目录；坐标为可选信息，不依赖地图服务。</p></div><div class="page-actions"><el-button @click="load">刷新</el-button><el-button type="primary" @click="openCreate">新增地点</el-button></div></header>
    <section class="panel-card"><div class="table-toolbar"><el-input v-model="keyword" clearable placeholder="搜索名称、地址、别名、城市或分类" style="max-width: 360px" /></div>
      <el-table v-loading="loading" :data="filtered" row-key="id"><el-table-column prop="name" label="名称" min-width="130" /><el-table-column prop="addressText" label="详细地址" min-width="230" /><el-table-column label="城市 / 区县" min-width="130"><template #default="scope">{{ scope.row.city || '—' }} / {{ scope.row.district || '—' }}</template></el-table-column><el-table-column label="分类 / 别名" min-width="160"><template #default="scope">{{ scope.row.category || '—' }}<br><small>{{ scope.row.aliases || '—' }}</small></template></el-table-column><el-table-column label="坐标 (WGS84)" min-width="150"><template #default="scope">{{ coordinate(scope.row) }}</template></el-table-column><el-table-column label="使用" width="100"><template #default="scope">{{ scope.row.usageCount }} 次</template></el-table-column><el-table-column label="最近使用" min-width="155"><template #default="scope">{{ time(scope.row.lastUsedAt) }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="155" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button link :type="scope.row.enabled ? 'danger' : 'success'" @click="toggle(scope.row)">{{ scope.row.enabled ? '停用' : '启用' }}</el-button></template></el-table-column></el-table>
    </section>
    <el-dialog v-model="dialogOpen" :title="editing ? '编辑常用地点' : '新增常用地点'" width="680px"><el-form label-width="100px"><el-form-item label="名称" required><el-input v-model="form.name" maxlength="120" /></el-form-item><el-form-item label="详细地址" required><el-input v-model="form.addressText" maxlength="255" /></el-form-item><el-row :gutter="16"><el-col :span="12"><el-form-item label="纬度"><el-input-number v-model="form.latitude" :controls="false" :precision="7" style="width: 100%" /></el-form-item></el-col><el-col :span="12"><el-form-item label="经度"><el-input-number v-model="form.longitude" :controls="false" :precision="7" style="width: 100%" /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="12"><el-form-item label="城市"><el-input v-model="form.city" maxlength="80" /></el-form-item></el-col><el-col :span="12"><el-form-item label="区县"><el-input v-model="form.district" maxlength="80" /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="12"><el-form-item label="分类"><el-input v-model="form.category" maxlength="60" /></el-form-item></el-col><el-col :span="12"><el-form-item label="别名"><el-input v-model="form.aliases" maxlength="500" placeholder="多个别名用空格或逗号分隔" /></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></el-dialog>
  </section>
</template>
