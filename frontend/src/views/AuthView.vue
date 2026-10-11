<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElInput, ElButton, ElForm, ElFormItem } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { api, ApiError } from '../api'
import { setAccount } from '../session'
import { validateNickname, validatePassword } from '../validation'

const route = useRoute()
const router = useRouter()
const registering = computed(() => route.path === '/register')
const form = reactive({ identifier: '', email: '', nickname: '', userName: '', password: '', confirmPassword: '' })
const busy = ref(false)
const error = ref('')
const created = ref(false)
const preview = ref('')
const previewAvailable = ref(true)
const previewError = ref('')
const previewLoading = ref(false)
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | undefined

watch(() => route.path, () => { error.value = ''; created.value = false; form.password = ''; form.confirmPassword = '' })
watch(() => [registering.value, form.nickname, form.userName], (_, __, onCleanup) => {
  preview.value = ''
  previewError.value = ''
  previewLoading.value = false
  if (!registering.value || validateNickname(form.nickname)) return
  const controller = new AbortController()
  previewLoading.value = true
  const timer = setTimeout(async () => {
    try {
      const result = await api.previewUsername(form.nickname, form.userName, controller.signal)
      if (controller.signal.aborted) return
      preview.value = result.userName
      previewAvailable.value = result.available
    } catch (e) {
      if (!controller.signal.aborted) previewError.value = e instanceof Error ? e.message : '预览暂时不可用'
    } finally { if (!controller.signal.aborted) previewLoading.value = false }
  }, 500)
  onCleanup(() => { clearTimeout(timer); controller.abort() })
})
onUnmounted(() => clearInterval(cooldownTimer))

async function submit() {
  if (busy.value || cooldown.value) return
  error.value = ''
  if (registering.value) {
    error.value = validateNickname(form.nickname) || validatePassword(form.password, form.confirmPassword)
    if (!error.value && form.userName.trim() && !/^[a-z0-9_]{1,30}$/.test(form.userName.trim())) error.value = '用户名只能包含小写字母、数字和下划线，最多 30 个字符'
    if (error.value) return
  }
  busy.value = true
  try {
    if (registering.value) {
      await api.signUp({ email: form.email, nickname: form.nickname, userName: form.userName, password: form.password, confirmPassword: form.confirmPassword })
      created.value = true
      form.password = ''; form.confirmPassword = ''
    } else {
      setAccount(await api.login(form.identifier.trim(), form.password))
      form.password = ''
      await router.push('/')
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作未完成，请重试'
    if (e instanceof ApiError && e.status === 429 && e.retryAfter > 0) {
      cooldown.value = Math.ceil(e.retryAfter)
      clearInterval(cooldownTimer)
      cooldownTimer = setInterval(() => { cooldown.value--; if (cooldown.value <= 0) clearInterval(cooldownTimer) }, 1000)
    }
  } finally { busy.value = false }
}
</script>

<template>
  <div class="auth-page">
    <header class="auth-header"><RouterLink to="/" class="brand"><img src="/favicon.svg" alt="" /><span>BugNest</span></RouterLink><RouterLink to="/" class="back-link"><AppIcon name="back" :size="16" />返回工作台</RouterLink></header>
    <main class="auth-form-side"><div class="auth-form-wrap">
      <div v-if="created" class="registration-success" role="status"><span class="success-mark"><AppIcon name="check" :size="34" /></span><h1>注册成功</h1><p>账户已创建，请登录。</p><ElButton type="primary" @click="router.push('/login')">前往登录<AppIcon name="arrow" :size="16" /></ElButton></div>
      <template v-else><h1>{{ registering ? '注册账户' : '登录' }}</h1><ElForm label-position="top" :disabled="busy" @submit.prevent="submit">
        <template v-if="registering"><div class="form-two-cols"><ElFormItem label="昵称" required><ElInput v-model="form.nickname" placeholder="怎么称呼你？" autocomplete="nickname" aria-label="昵称" /></ElFormItem><ElFormItem label="用户名（可选）"><ElInput v-model="form.userName" maxlength="30" placeholder="留空自动生成" autocomplete="username" aria-label="用户名" /></ElFormItem></div><p v-if="form.nickname.trim()" :class="['username-preview', { unavailable: previewError || (!previewAvailable && preview) }]" aria-live="polite">{{ previewLoading ? '正在检查用户名…' : previewError ? previewError : preview ? (previewAvailable ? `可用用户名：@${preview}` : preview.includes(' ') || preview.length > 30 ? preview : `@${preview} 已被占用`) : '填写昵称后自动生成用户名' }}</p><ElFormItem label="邮箱" required><ElInput v-model="form.email" type="email" maxlength="254" placeholder="you@example.com" autocomplete="email" aria-label="邮箱" :input-style="{ fontFamily: 'inherit' }" /></ElFormItem></template>
        <ElFormItem v-else label="邮箱或用户名" required><ElInput v-model="form.identifier" placeholder="输入你的邮箱或用户名" autocomplete="username" aria-label="邮箱或用户名" /></ElFormItem>
        <ElFormItem label="密码" required><ElInput v-model="form.password" type="password" show-password :placeholder="registering ? '至少 15 个字符，试试一段短句' : '输入你的密码'" :autocomplete="registering ? 'new-password' : 'current-password'" aria-label="密码" /></ElFormItem><template v-if="registering"><p class="password-hint">支持中文、空格和长密码，无需刻意组合特殊符号。</p><ElFormItem label="确认密码" required><ElInput v-model="form.confirmPassword" type="password" show-password placeholder="再输入一次密码" autocomplete="new-password" aria-label="确认密码" /></ElFormItem></template>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p><ElButton class="auth-submit" type="primary" native-type="submit" :loading="busy" :disabled="cooldown > 0 || (!registering && (!form.identifier.trim() || !form.password)) || (registering && (!form.email || !form.nickname.trim() || !form.password || !form.confirmPassword))">{{ cooldown > 0 ? `${cooldown} 秒后重试` : registering ? '创建账户' : '登录' }}<AppIcon v-if="!busy" name="arrow" :size="17" /></ElButton>
      </ElForm><p class="auth-switch">{{ registering ? '已经有账户了？' : '还没有账户？' }}<RouterLink :to="registering ? '/login' : '/register'">{{ registering ? '直接登录' : '创建一个账户' }}</RouterLink></p></template>
    </div></main>
  </div>
</template>
