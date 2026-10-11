export interface Account { userId: number; userName: string; nickname: string }
export interface UsernamePreview { userName: string; available: boolean }
export interface SignUp { email: string; password: string; confirmPassword: string; nickname: string; userName: string }

export class ApiError extends Error {
  status: number
  retryAfter: number
  constructor(message: string, status: number, retryAfter = 0) {
    super(message)
    this.status = status
    this.retryAfter = retryAfter
  }
}

const messages: Record<string, string> = {
  'Email is invalid': '请输入有效的邮箱地址',
  'Email already exists': '这个邮箱已注册，请直接登录',
  'User name already exists': '用户名已被使用，请换一个',
  'Nickname must contain 1 to 50 characters': '昵称需要 1–50 个字符',
  'Password must contain at least 15 characters': '密码至少需要 15 个字符',
  'Password must contain at most 1024 characters': '密码最多支持 1024 个字符',
  'Passwords do not match': '两次输入的密码不一致',
  'Password is too common or compromised': '这个密码太常见，请换一个更独特的密码',
  'User name must contain only lowercase letters, numbers, and underscores': '用户名只能包含小写字母、数字和下划线，最多 30 个字符',
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, { ...init, credentials: 'same-origin' })
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error
    throw new ApiError('暂时无法连接服务，请确认后端已启动后重试', 0)
  }
  const body = await response.text()
  let data: unknown = body
  try { data = body ? JSON.parse(body) : undefined } catch { /* Business errors can be plain text. */ }
  if (!response.ok) {
    const raw = typeof data === 'object' && data !== null && 'message' in data
      ? String(data.message) : typeof data === 'string' ? data : ''
    const fallback = response.status === 401 ? '登录状态已过期，请重新登录'
      : response.status === 403 ? '请求验证失败，请重新操作'
      : response.status === 429 ? '操作太频繁，请稍后重试'
      : response.status === 503 ? '服务暂时不可用，请稍后重试' : '操作未完成，请稍后重试'
    const retryAfter = Number(response.headers.get('Retry-After'))
    throw new ApiError(messages[raw] ?? (raw && !raw.includes('<') ? raw : fallback), response.status,
      Number.isFinite(retryAfter) ? Math.max(0, retryAfter) : 0)
  }
  return data as T
}

// Fetch a fresh token for each mutation: login and logout rotate the CSRF session.
async function mutate<T>(path: string, method: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const csrf = await request<{ headerName: string; token: string }>('/auth/csrf', { signal })
  return request<T>(path, {
    method, signal,
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export const api = {
  me: () => request<Account>('/accounts/me'),
  login: (identifier: string, password: string) => mutate<Account>('/auth/login', 'POST', { identifier, password }),
  signUp: (body: SignUp) => mutate<{ userId: number }>('/auth/sign-up', 'POST', body),
  previewUsername: (nickname: string, userName: string, signal?: AbortSignal) =>
    mutate<UsernamePreview>('/auth/sign-up/username-preview', 'POST', { nickname, userName }, signal),
  updateNickname: (id: number, nickname: string) => mutate<void>(`/accounts/${id}/nickname`, 'PATCH', { nickname }),
  logout: () => mutate<void>('/auth/logout', 'POST'),
}
