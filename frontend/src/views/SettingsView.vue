<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElButton, ElInput, ElForm, ElFormItem, ElMessage } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { api, ApiError } from '../api'
import { account, sessionLoading, sessionError, restoreSession } from '../session'
import { validateNickname } from '../validation'

const nickname = ref('')
const busy = ref(false)
const error = ref('')
watch(account, value => { nickname.value = value?.nickname ?? '' }, { immediate: true })
async function save() {
  if (!account.value || busy.value) return
  error.value = validateNickname(nickname.value)
  if (error.value) return
  busy.value = true
  try {
    await api.updateNickname(account.value.userId, nickname.value)
    account.value = { ...account.value, nickname: nickname.value.trim() }
    ElMessage.success('昵称已更新')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败，请重试'
    if (e instanceof ApiError && e.status === 401) account.value = null
  } finally { busy.value = false }
}
</script>
<template>
  <div class="settings-page"><div class="page-heading"><h1>账户设置</h1></div>
    <div v-if="sessionLoading" class="empty-state" role="status">正在读取账户信息…</div>
    <div v-else-if="!account" class="guest-settings"><span class="settings-symbol"><AppIcon name="user" :size="30" /></span><h2>{{ sessionError ? '暂时无法读取账户' : '请先登录' }}</h2><p>{{ sessionError || '登录后可以查看你的用户名，更新昵称。' }}</p><ElButton v-if="sessionError" @click="restoreSession">重新连接</ElButton><RouterLink v-else to="/login" class="button-link">登录账户<AppIcon name="arrow" :size="16" /></RouterLink><RouterLink to="/register" class="text-link">创建账户</RouterLink></div>
    <section v-else class="profile-section"><div class="profile-description"><h2>个人资料</h2><p>昵称会展示给团队成员。<br />用户名用于登录，创建后不可修改。</p><div class="profile-avatar">{{ Array.from(account.nickname)[0] }}</div></div><ElForm class="profile-form" label-position="top" @submit.prevent="save"><ElFormItem label="用户名"><ElInput :model-value="`@${account.userName}`" readonly aria-label="用户名（不可修改）" /></ElFormItem><p class="form-hint">这是你的唯一用户名，创建后不可修改。</p><ElFormItem label="昵称" required><ElInput v-model="nickname" autocomplete="nickname" placeholder="你的昵称" aria-label="修改昵称" /></ElFormItem><p v-if="error" class="form-error" role="alert">{{ error }}</p><ElButton type="primary" native-type="submit" :loading="busy" :disabled="nickname.trim() === account.nickname">保存修改</ElButton></ElForm></section>
  </div>
</template>
