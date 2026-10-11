<script setup lang="ts">
import { ArrowRight, ArrowDown, Plus, Search, Grid, List, Menu, Close, Setting, User, SwitchButton, CircleCheck, Warning, Timer, Document, Folder, TrendCharts, MoreFilled, ArrowLeft, View } from '@element-plus/icons-vue'
defineProps<{ name: string; size?: number }>()
const icons: Record<string, unknown> = { arrow: ArrowRight, down: ArrowDown, plus: Plus, search: Search, grid: Grid, list: List, menu: Menu, close: Close, settings: Setting, user: User, logout: SwitchButton, check: CircleCheck, warning: Warning, timer: Timer, document: Document, folder: Folder, chart: TrendCharts, more: MoreFilled, back: ArrowLeft, view: View }
</script>
<template><component :is="icons[name]" class="app-icon" :style="{ width: `${size ?? 18}px`, height: `${size ?? 18}px` }" aria-hidden="true" /></template>
