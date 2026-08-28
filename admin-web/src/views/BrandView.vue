<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getAdminBrand, updateAdminBrand, uploadBrandLogo } from '../api/brand'
import type { PlatformBrand } from '../domain/types'
import { getSession } from '../storage/auth'

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const brand = ref<PlatformBrand | null>(null)
const form = reactive({
  companyName: '',
  logoUrl: '',
})

const isAdmin = computed(() => getSession()?.authority === 'ROLE_ADMIN')
const logoPreview = computed(() => form.logoUrl.trim())
const updatedText = computed(() => {
  if (!brand.value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(brand.value.updatedAt))
})

onMounted(() => void load())

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await getAdminBrand()
    brand.value = result
    form.companyName = result.companyName
    form.logoUrl = result.logoUrl ?? ''
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (!isAdmin.value || saving.value) return
  if (!form.companyName.trim()) {
    ElMessage.warning('请填写公司名称')
    return
  }
  saving.value = true
  try {
    const result = await updateAdminBrand({
      companyName: form.companyName.trim(),
      logoUrl: form.logoUrl.trim() || undefined,
    })
    brand.value = result
    form.companyName = result.companyName
    form.logoUrl = result.logoUrl ?? ''
    ElMessage.success('品牌配置已更新')
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    saving.value = false
  }
}

async function uploadLogo(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !isAdmin.value || uploading.value) return
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
    ElMessage.warning('Logo 仅支持 PNG、JPEG 或 WebP 图片')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('Logo 文件不能超过 2 MB')
    return
  }
  uploading.value = true
  try {
    const result = await uploadBrandLogo(file)
    brand.value = result
    form.logoUrl = result.logoUrl ?? ''
    ElMessage.success('Logo 已上传并替换')
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    uploading.value = false
  }
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}
</script>

<template>
  <section class="workspace-page">
    <header class="page-heading">
      <div>
        <p class="page-kicker">PLATFORM BRAND</p>
        <h1>平台品牌</h1>
        <p>公司名称和 Logo 会用于后台、司机端与乘客 H5 的基础展示。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <div class="metric-strip">
      <div><strong>{{ form.companyName || '未设置' }}</strong><span>当前公司名称</span></div>
      <div><strong>{{ logoPreview ? '已配置' : '未配置' }}</strong><span>Logo 地址</span></div>
      <div><strong>{{ updatedText }}</strong><span>最近更新</span></div>
    </div>

    <section class="panel-card brand-card">
      <el-alert
        v-if="!isAdmin"
        title="只有管理员可以修改品牌配置"
        description="调度员和财务角色可查看当前配置，但保存按钮不可用。"
        type="info"
        show-icon
        :closable="false"
      />

      <el-form label-position="top" class="brand-form" @submit.prevent="save">
        <el-form-item label="公司名称">
          <el-input
            v-model="form.companyName"
            :disabled="!isAdmin"
            maxlength="120"
            show-word-limit
            placeholder="请输入对外展示的公司名称"
          />
        </el-form-item>

        <el-form-item label="Logo URL">
          <el-input
            v-model="form.logoUrl"
            :disabled="!isAdmin"
            maxlength="500"
            placeholder="https://example.com/logo.png"
          />
          <div class="brand-hint">可直接填写外部 URL，也可上传不超过 2 MB 的 PNG、JPEG 或 WebP 文件。</div>
        </el-form-item>

        <el-form-item label="上传 Logo 文件">
          <input class="logo-file-input" type="file" accept="image/png,image/jpeg,image/webp" :disabled="!isAdmin || uploading" @change="uploadLogo" />
        </el-form-item>

        <div v-if="logoPreview" class="brand-preview">
          <img :src="logoPreview" alt="平台 Logo 预览" @error="($event.target as HTMLImageElement).style.display = 'none'" />
          <span>预览</span>
        </div>

        <div class="brand-actions">
          <el-button plain :loading="loading" :disabled="saving" @click="load">重置</el-button>
          <el-button type="primary" :loading="saving" :disabled="!isAdmin || loading" @click="save">保存品牌配置</el-button>
        </div>
      </el-form>
    </section>
  </section>
</template>

<style scoped>
.brand-card {
  padding: 24px;
}

.brand-form {
  max-width: 680px;
  margin-top: 18px;
}

.brand-hint {
  width: 100%;
  margin-top: 6px;
  color: var(--admin-muted);
  font-size: 12px;
}

.brand-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px dashed var(--admin-line);
  border-radius: 12px;
  background: #f8fafc;
}

.brand-preview img {
  max-width: 96px;
  max-height: 48px;
  object-fit: contain;
}

.logo-file-input {
  width: 100%;
  padding: 10px;
  border: 1px dashed var(--admin-line);
  border-radius: 10px;
  background: #f8fafc;
}

.brand-preview span {
  color: var(--admin-muted);
  font-size: 12px;
}

.brand-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 8px;
}

@media (max-width: 720px) {
  .brand-actions {
    flex-direction: column-reverse;
  }

  .brand-actions .el-button {
    width: 100%;
  }
}
</style>
