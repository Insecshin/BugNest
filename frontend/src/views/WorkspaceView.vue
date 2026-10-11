<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElButton, ElInput, ElSelect, ElOption, ElDrawer, ElForm, ElFormItem, ElMessage } from 'element-plus'
import AppIcon from '../components/AppIcon.vue'
import { issues, statuses, priorities, projects, type Issue } from '../issues'

const route = useRoute()
const search = ref('')
const status = ref('全部状态')
const project = ref('全部项目')
const priority = ref('全部优先级')
const view = ref<'list' | 'board'>('list')
const drawer = ref(false)
const editingId = ref<number | null>(null)
const formError = ref('')
const draft = reactive({ title: '', description: '', status: '待处理' as Issue['status'], priority: '中' as Issue['priority'], project: 'BugNest Web' as Issue['project'], assignee: '未分配' })
const overview = computed(() => route.path === '/')
const filtered = computed(() => issues.filter(issue =>
  (status.value === '全部状态' || issue.status === status.value) &&
  (project.value === '全部项目' || issue.project === project.value) &&
  (priority.value === '全部优先级' || issue.priority === priority.value) &&
  `${issue.title} BN-${issue.id} ${issue.assignee}`.toLowerCase().includes(search.value.trim().toLowerCase()),
))
const stats = computed(() => statuses.map(s => ({ name: s, count: issues.filter(i => i.status === s).length })))
const resolved = computed(() => Math.round(issues.filter(i => i.status === '已解决').length / Math.max(issues.length, 1) * 100))
const unresolved = computed(() => issues.filter(i => i.status !== '已解决'))
const distribution = computed(() => priorities.map(p => ({ name: p, count: unresolved.value.filter(i => i.priority === p).length })))

function openIssue(issue?: Issue) {
  editingId.value = issue?.id ?? null
  Object.assign(draft, issue ?? { title: '', description: '', status: '待处理', priority: '中', project: 'BugNest Web', assignee: '未分配' })
  formError.value = ''
  drawer.value = true
}
function saveIssue() {
  if (!draft.title.trim()) { formError.value = '请先填写缺陷标题'; return }
  if (draft.title.trim().length > 120) { formError.value = '标题最多 120 个字符'; return }
  const existing = issues.find(i => i.id === editingId.value)
  if (existing) Object.assign(existing, draft, { title: draft.title.trim() })
  else issues.unshift({ ...draft, title: draft.title.trim(), id: Math.max(0, ...issues.map(i => i.id)) + 1,
    date: new Intl.DateTimeFormat('en-US', { month: '2-digit', day: '2-digit', timeZone: 'Asia/Shanghai' }).format(new Date()).replace('/', '-') })
  drawer.value = false
  ElMessage.success(existing ? '示例缺陷已更新' : '示例缺陷已创建')
}
function clearFilters() { search.value = ''; status.value = '全部状态'; priority.value = '全部优先级'; project.value = '全部项目' }
const statusClass = (value: string) => `status-${statuses.indexOf(value as Issue['status'])}`
const priorityClass = (value: string) => `priority-${priorities.indexOf(value as Issue['priority'])}`
</script>

<template>
  <div class="workspace-page">
    <div class="page-heading"><h1>{{ overview ? '工作台概览' : '缺陷管理' }}</h1><ElButton type="primary" @click="openIssue()"><AppIcon name="plus" />新建缺陷</ElButton></div>

    <template v-if="overview">
      <section class="overview-strip" aria-label="缺陷状态概览">
        <button v-for="(item, index) in stats" :key="item.name" class="stat-cell" :class="{ selected: status === item.name }" @click="status = status === item.name ? '全部状态' : item.name"><div class="stat-label"><span :class="['status-symbol', statusClass(item.name)]"><AppIcon :name="['document', 'timer', 'view', 'check'][index] ?? 'document'" :size="16" /></span>{{ item.name }}<AppIcon name="arrow" :size="14" /></div><div class="stat-value">{{ String(item.count).padStart(2, '0') }}</div></button>
      </section>

      <div class="insight-grid">
        <section class="progress-panel"><div class="section-caption"><span>解决进度</span><span class="subtle">示例数据</span></div><div class="progress-content"><div class="progress-ring" :style="{ '--progress': `${resolved}%` }"><div><strong>{{ resolved }}<small>%</small></strong><span>已解决</span></div></div><div class="progress-copy"><p>共 {{ issues.length }} 个缺陷</p><div class="progress-legend"><span><i></i>已解决 {{ issues.length - unresolved.length }}</span><span><i></i>未解决 {{ unresolved.length }}</span></div></div></div></section>
        <section class="priority-panel"><div class="section-caption"><span>未解决缺陷 · 优先级</span><span class="subtle">{{ unresolved.length }} 个</span></div><div class="priority-bars"><div v-for="item in distribution" :key="item.name" class="priority-bar-row"><span><i :class="priorityClass(item.name)"></i>{{ item.name }}</span><div class="bar-track"><div :class="priorityClass(item.name)" :style="{ width: `${item.count / Math.max(unresolved.length, 1) * 100}%` }"></div></div><strong>{{ item.count }}</strong></div></div></section>
      </div>
    </template>

    <section class="issues-section" aria-label="缺陷列表">
      <div class="section-heading"><h2>{{ overview ? '缺陷列表' : '全部缺陷' }}<span class="count-badge">{{ issues.length }}</span></h2><div class="view-switch" role="group" aria-label="切换视图"><button :class="{ active: view === 'list' }" :aria-pressed="view === 'list'" @click="view = 'list'"><AppIcon name="list" :size="16" />列表</button><button :class="{ active: view === 'board' }" :aria-pressed="view === 'board'" @click="view = 'board'"><AppIcon name="grid" :size="16" />看板</button></div></div>
      <div class="filter-bar"><ElInput v-model="search" placeholder="搜索缺陷标题、编号或负责人…" clearable aria-label="搜索缺陷"><template #prefix><AppIcon name="search" :size="17" /></template></ElInput><div class="filter-selects"><ElSelect v-model="status" aria-label="筛选状态"><ElOption v-for="s in ['全部状态', ...statuses]" :key="s" :label="s" :value="s" /></ElSelect><ElSelect v-model="priority" aria-label="筛选优先级"><ElOption v-for="p in ['全部优先级', ...priorities]" :key="p" :label="p" :value="p" /></ElSelect><ElSelect v-model="project" aria-label="筛选项目"><ElOption v-for="p in ['全部项目', ...projects]" :key="p" :label="p" :value="p" /></ElSelect></div></div>

      <div v-if="!filtered.length" class="empty-state"><AppIcon name="search" :size="28" /><h3>这里暂时没有匹配的问题</h3><p>换个关键词，或清除筛选条件再看看。</p><ElButton @click="clearFilters">清除筛选</ElButton></div>
      <div v-else-if="view === 'list'" class="issue-table-wrap"><table class="issue-table"><thead><tr><th class="issue-number">编号</th><th>缺陷标题</th><th>状态</th><th>优先级</th><th>负责人</th><th>更新日期</th><th><span class="sr-only">操作</span></th></tr></thead><tbody><tr v-for="issue in filtered" :key="issue.id"><td class="issue-number">BN-{{ issue.id }}</td><td class="issue-title-cell"><button @click="openIssue(issue)">{{ issue.title }}</button><span><i :class="['project-dot', `project-${projects.indexOf(issue.project)}`]"></i>{{ issue.project }}</span></td><td><span :class="['status-tag', statusClass(issue.status)]"><i></i>{{ issue.status }}</span></td><td><span :class="['priority-label', priorityClass(issue.priority)]"><span class="priority-lines"><i></i><i></i><i></i></span>{{ issue.priority }}</span></td><td><span class="assignee"><span :class="['mini-avatar', `avatar-${issue.id % 3}`]">{{ Array.from(issue.assignee)[0] }}</span>{{ issue.assignee }}</span></td><td class="issue-date">{{ issue.date }}</td><td><button class="icon-button row-open" :aria-label="`编辑 BN-${issue.id}`" @click="openIssue(issue)"><AppIcon name="arrow" :size="16" /></button></td></tr></tbody></table></div>
      <div v-else class="kanban"><section v-for="s in statuses" :key="s" class="kanban-column"><h3><span :class="['status-tag', statusClass(s)]"><i></i>{{ s }}</span><span>{{ filtered.filter(i => i.status === s).length }}</span></h3><button v-for="issue in filtered.filter(i => i.status === s)" :key="issue.id" class="kanban-card" @click="openIssue(issue)"><span class="kanban-meta">BN-{{ issue.id }}<span :class="['priority-label', priorityClass(issue.priority)]">{{ issue.priority }}</span></span><strong>{{ issue.title }}</strong><span class="kanban-project">{{ issue.project }}</span><span class="kanban-footer"><span class="assignee"><span :class="['mini-avatar', `avatar-${issue.id % 3}`]">{{ Array.from(issue.assignee)[0] }}</span>{{ issue.assignee }}</span><span>{{ issue.date }}</span></span></button><p v-if="!filtered.some(i => i.status === s)" class="kanban-empty">这个阶段暂时没有缺陷</p></section></div>
      <div class="table-footer"><span>显示 {{ filtered.length }} / {{ issues.length }} 个缺陷</span><span><span class="status-dot"></span>演示数据 · 修改仅保留本次访问</span></div>
    </section>
    <div class="demo-notice"><AppIcon name="view" :size="16" /><p>当前为界面演示。缺陷数据尚未连接服务器，刷新页面会恢复示例；登录、注册与账户设置使用真实服务。</p></div>

    <ElDrawer v-model="drawer" :title="editingId ? `编辑缺陷 · BN-${editingId}` : '新建缺陷'" size="min(520px, 100%)" destroy-on-close>
      <ElForm label-position="top" @submit.prevent="saveIssue"><ElFormItem label="缺陷标题" required><ElInput v-model="draft.title" maxlength="120" placeholder="简洁描述发生了什么" aria-label="缺陷标题" /></ElFormItem><ElFormItem label="问题描述"><ElInput v-model="draft.description" type="textarea" :rows="5" maxlength="2000" placeholder="复现步骤、实际结果、预期结果…" aria-label="问题描述" /></ElFormItem><div class="form-two-cols"><ElFormItem label="状态"><ElSelect v-model="draft.status" aria-label="缺陷状态"><ElOption v-for="s in statuses" :key="s" :label="s" :value="s" /></ElSelect></ElFormItem><ElFormItem label="优先级"><ElSelect v-model="draft.priority" aria-label="缺陷优先级"><ElOption v-for="p in priorities" :key="p" :label="p" :value="p" /></ElSelect></ElFormItem></div><ElFormItem label="所属项目"><ElSelect v-model="draft.project" aria-label="所属项目"><ElOption v-for="p in projects" :key="p" :label="p" :value="p" /></ElSelect></ElFormItem><ElFormItem label="负责人"><ElSelect v-model="draft.assignee" aria-label="负责人"><ElOption v-for="name in ['未分配', '林悦', '陈默', '许知']" :key="name" :label="name" :value="name" /></ElSelect></ElFormItem><p v-if="formError" class="form-error" role="alert">{{ formError }}</p><div class="drawer-actions"><ElButton @click="drawer = false">取消</ElButton><ElButton type="primary" native-type="submit">{{ editingId ? '保存修改' : '创建缺陷' }}</ElButton></div><p class="form-hint">保存到本次演示，刷新后恢复示例数据。</p></ElForm>
    </ElDrawer>
  </div>
</template>
