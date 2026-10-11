import { reactive } from 'vue'

export const statuses = ['待处理', '进行中', '待验证', '已解决'] as const
export const priorities = ['紧急', '高', '中', '低'] as const
export const projects = ['BugNest Web', '账户服务', '移动端'] as const
export interface Issue {
  id: number; title: string; description: string;
  status: typeof statuses[number]; priority: typeof priorities[number];
  project: typeof projects[number]; assignee: string; date: string
}

// shortcut: demo issues live in memory only; replace with the issue API when the backend exists.
export const issues = reactive<Issue[]>([
  { id: 128, title: '登录状态失效后，页面未提示重新登录', description: '会话过期后提交表单，应显示明确提示，并引导用户重新登录。', status: '进行中', priority: '紧急', project: 'BugNest Web', assignee: '林悦', date: '10-09' },
  { id: 127, title: '移动端缺陷列表在小屏幕下显示不完整', description: '在 375px 宽度下查看列表，检查标题、优先级和操作区域。', status: '待处理', priority: '高', project: '移动端', assignee: '陈默', date: '10-09' },
  { id: 126, title: '注册时重复邮箱的错误提示不够清晰', description: '重复邮箱返回冲突时，应提示用户使用已有账户登录。', status: '待验证', priority: '中', project: '账户服务', assignee: '许知', date: '10-08' },
  { id: 125, title: '筛选条件切换后，列表数量未及时更新', description: '状态和项目筛选应即时更新列表及结果数量。', status: '进行中', priority: '高', project: 'BugNest Web', assignee: '林悦', date: '10-08' },
  { id: 124, title: '昵称包含中文时，头像文字截断', description: '使用昵称首个 Unicode 字符作为头像文字。', status: '已解决', priority: '低', project: '账户服务', assignee: '陈默', date: '10-07' },
  { id: 123, title: '新建缺陷时缺少标题为空的校验', description: '空白标题应在提交前被拦截，并给出输入提示。', status: '待处理', priority: '中', project: 'BugNest Web', assignee: '许知', date: '10-07' },
  { id: 122, title: '看板中的长标题换行异常', description: '较长的缺陷标题需要自然换行，不能挤压状态信息。', status: '待验证', priority: '低', project: '移动端', assignee: '林悦', date: '10-06' },
  { id: 121, title: '用户名预览没有展示占用状态', description: '手动用户名不可用时显示反馈，允许用户继续修改。', status: '已解决', priority: '中', project: '账户服务', assignee: '许知', date: '10-06' },
])
