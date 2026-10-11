import { ref } from 'vue'
import { api, ApiError, type Account } from './api.ts'

export const account = ref<Account | null>(null)
export const sessionError = ref('')
export const sessionLoading = ref(true)
let revision = 0

export function setAccount(value: Account | null) {
  revision++
  account.value = value
  sessionError.value = ''
  sessionLoading.value = false
}

export async function restoreSession() {
  const currentRevision = ++revision
  sessionLoading.value = true
  sessionError.value = ''
  try {
    const restored = await api.me()
    if (currentRevision !== revision) return
    account.value = restored
  } catch (error) {
    if (currentRevision !== revision) return
    account.value = null
    if (!(error instanceof ApiError && error.status === 401)) {
      sessionError.value = error instanceof Error ? error.message : '无法恢复会话'
    }
  } finally {
    if (currentRevision === revision) sessionLoading.value = false
  }
}
