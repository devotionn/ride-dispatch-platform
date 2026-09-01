<script setup lang="ts">
import { computed, ref } from 'vue'
import { showFailToast, showToast } from 'vant'

import { submitComplaint } from '../api/safety'

const props = defineProps<{
  open: boolean
  orderNo: string
  passengerToken: string
}>()

const emit = defineEmits<{
  close: []
  submitted: [complaintNo: string]
}>()

const CATEGORIES = [
  { value: 'SERVICE_ATTITUDE', label: '服务态度' },
  { value: 'ROUTE_DETOUR', label: '路线/绕路问题' },
  { value: 'FEE_DISPUTE', label: '收费争议' },
  { value: 'DRIVING_SAFETY', label: '驾驶安全' },
  { value: 'VEHICLE_CONDITION', label: '车辆状况' },
  { value: 'OTHER', label: '其他' },
] as const

const category = ref<string>('SERVICE_ATTITUDE')
const description = ref('')
const contactMobile = ref('')
const submitting = ref(false)

const canSubmit = computed(() => description.value.trim().length >= 5 && !submitting.value)

async function submit(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    const result = await submitComplaint(props.orderNo, props.passengerToken, {
      category: category.value,
      description: description.value.trim(),
      contactMobile: contactMobile.value.trim() || undefined,
    })
    showToast(`投诉已提交（${result.complaintNo}），调度人员会尽快跟进`)
    emit('submitted', result.complaintNo)
    emit('close')
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '投诉提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="complaint-overlay" role="dialog" aria-modal="true" aria-label="乘客投诉">
      <section class="complaint-sheet">
        <header class="complaint-header">
          <div>
            <p class="section-kicker">乘客投诉</p>
            <h2>投诉与建议</h2>
          </div>
          <button type="button" class="complaint-close" aria-label="关闭" @click="emit('close')">×</button>
        </header>

        <div class="complaint-block">
          <p class="complaint-label">投诉类型</p>
          <div class="complaint-categories">
            <button
              v-for="item in CATEGORIES"
              :key="item.value"
              type="button"
              class="complaint-category"
              :class="{ active: category === item.value }"
              @click="category = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div class="complaint-block">
          <p class="complaint-label">问题描述</p>
          <textarea
            v-model="description"
            class="complaint-textarea"
            rows="4"
            maxlength="1000"
            placeholder="请描述发生的事情（至少 5 个字），例如时间、地点、涉及人员等"
          />
        </div>

        <div class="complaint-block">
          <p class="complaint-label">联系电话（选填，便于回访）</p>
          <input v-model="contactMobile" class="complaint-input" maxlength="30" placeholder="选填">
        </div>

        <button type="button" class="complaint-submit" :disabled="!canSubmit" @click="submit">
          {{ submitting ? '提交中…' : '提交投诉' }}
        </button>
        <p class="complaint-note">投诉将提交给平台调度/客服人员处理，处理结果由后台跟进。</p>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.complaint-overlay{position:fixed;inset:0;z-index:3000;background:rgba(15,23,42,.46);display:flex;align-items:flex-end;justify-content:center;padding-top:40px}
.complaint-sheet{width:min(100%,640px);max-height:92vh;overflow:auto;background:#fff;border-radius:24px 24px 0 0;padding:22px 18px calc(22px + env(safe-area-inset-bottom));box-shadow:0 -12px 40px rgba(15,23,42,.14)}
.complaint-header{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:14px}
.complaint-header h2{margin:4px 0 0;font-size:22px}
.complaint-close{border:0;background:#f3f4f6;width:36px;height:36px;border-radius:50%;font-size:25px;line-height:1}
.complaint-block{margin-bottom:14px;display:grid;gap:8px}
.complaint-label{margin:0;font-size:13px;color:#475569}
.complaint-categories{display:flex;flex-wrap:wrap;gap:8px}
.complaint-category{border:1px solid #dbe1e8;background:#fff;color:#1e293b;border-radius:999px;padding:8px 14px;font-size:13px}
.complaint-category.active{border-color:#16a36a;background:#eefaf4;color:#0f7a4d;font-weight:700}
.complaint-textarea,.complaint-input{width:100%;box-sizing:border-box;border:1px solid #dbe1e8;border-radius:12px;padding:12px 13px;font-size:15px;outline:none;font-family:inherit}
.complaint-submit{display:block;width:100%;border:0;border-radius:14px;background:#1f6fff;color:#fff;font-size:16px;font-weight:700;padding:14px;cursor:pointer}
.complaint-submit:disabled{opacity:.55}
.complaint-note{margin:10px 0 0;font-size:12px;color:#64748b;line-height:1.6}
</style>
