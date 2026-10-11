<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElMessage } from 'element-plus'
import AppIcon from './components/AppIcon.vue'
import { api } from './api'
import { account, restoreSession, sessionLoading, setAccount } from './session'

const route = useRoute()
const router = useRouter()
const mobileMenu = ref(false)
const loggingOut = ref(false)
const isAuth = computed(() => ['/login', '/register'].includes(route.path))
const avatar = computed(() => Array.from(account.value?.nickname ?? '访')[0])
onMounted(restoreSession)

async function logout() {
  loggingOut.value = true
  try {
    await api.logout()
    setAccount(null)
    mobileMenu.value = false
    await router.push('/login')
    ElMessage.success('已安全退出登录')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退出失败，请重试')
  } finally { loggingOut.value = false }
}
</script>

<template>
  <RouterView v-if="isAuth" />
  <div v-else class="app-shell">
    <button v-if="mobileMenu" class="sidebar-scrim" aria-label="关闭导航" @click="mobileMenu = false"></button>
    <aside class="sidebar" :class="{ 'is-open': mobileMenu }">
      <div class="workspace-label"><span class="workspace-icon">B</span><span>BugNest 工作空间</span></div>
      <div class="nav-label">工作空间</div>
      <nav aria-label="主导航">
        <RouterLink to="/" :class="{ active: route.path === '/' }" @click="mobileMenu = false"><AppIcon name="grid" /><span>概览</span></RouterLink>
        <RouterLink to="/issues" :class="{ active: route.path === '/issues' }" @click="mobileMenu = false"><AppIcon name="document" /><span>缺陷管理</span><span class="nav-dot"></span></RouterLink>
        <RouterLink to="/settings" :class="{ active: route.path === '/settings' }" @click="mobileMenu = false"><AppIcon name="settings" /><span>账户设置</span></RouterLink>
      </nav>
    </aside>

    <div class="main-shell">
      <header class="topbar">
        <RouterLink to="/" class="brand" @click="mobileMenu = false"><img src="/favicon.svg" alt="" /><span>BugNest</span></RouterLink>
        <div class="breadcrumb"><button class="mobile-toggle icon-button" aria-label="打开导航" @click="mobileMenu = true"><AppIcon name="menu" /></button><nav class="top-navigation" aria-label="页面导航"><RouterLink to="/" :class="{ active: route.path === '/' }">工作台</RouterLink><RouterLink to="/issues" :class="{ active: route.path === '/issues' }">问题追踪</RouterLink><RouterLink to="/settings" :class="{ active: route.path === '/settings' }">个人账户</RouterLink></nav></div>
        <div class="topbar-right"><span class="demo-badge"><span></span>示例工作区</span><span class="topbar-divider"></span>
          <span v-if="sessionLoading" class="session-loading">读取会话…</span>
          <template v-else-if="account"><RouterLink to="/settings" class="user-pill"><span class="avatar">{{ avatar }}</span><span>{{ account.nickname }}</span></RouterLink><ElButton text :loading="loggingOut" aria-label="退出登录" @click="logout"><AppIcon name="logout" /></ElButton></template>
          <RouterLink v-else to="/login" class="user-pill"><span class="avatar visitor">访</span><span>登录账户</span><AppIcon name="arrow" :size="14" /></RouterLink>
        </div>
      </header>
      <main id="main-content"><RouterView /></main>
    </div>
  </div>
</template>
