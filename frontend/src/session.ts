import { ref } from 'vue'
import { api, ApiError, type Account } from './api'

export const account = ref<Account | null>(null)
export const sessionError = ref('')
export const sessionLoading = ref(true)

export async function restoreSession() {
  sessionLoading.value = true
  sessionError.value = ''
  try {
    account.value = await api.me()
  } catch (error) {
    account.value = null
    if (!(error instanceof ApiError && error.status === 401)) {
      sessionError.value = error instanceof Error ? error.message : '无法恢复会话'
    }
  } finally {
    sessionLoading.value = false
  }
}
