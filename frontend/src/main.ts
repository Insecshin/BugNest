import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('./views/WorkspaceView.vue') },
    { path: '/issues', component: () => import('./views/WorkspaceView.vue') },
    { path: '/settings', component: () => import('./views/SettingsView.vue') },
    { path: '/login', component: () => import('./views/AuthView.vue') },
    { path: '/register', component: () => import('./views/AuthView.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('./views/NotFoundView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

createApp(App).use(router).mount('#app')
