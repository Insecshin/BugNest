import { afterEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { api, ApiError } from '../src/api.ts'
import { validateNickname, validatePassword } from '../src/validation.ts'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

test('every mutation gets a fresh CSRF token and carries the same-origin session', async () => {
  const calls = []
  let token = 0
  globalThis.fetch = async (path, init) => {
    calls.push({ path, init })
    if (path === '/auth/csrf') return Response.json({ headerName: 'X-CUSTOM-CSRF', token: `token-${++token}` })
    return new Response(null, { status: 204 })
  }
  await api.updateNickname(42, '新昵称')
  await api.logout()
  assert.deepEqual(calls.map(c => c.path), ['/auth/csrf', '/accounts/42/nickname', '/auth/csrf', '/auth/logout'])
  assert.ok(calls.every(c => c.init.credentials === 'same-origin'))
  assert.equal(calls[1].init.headers['X-CUSTOM-CSRF'], 'token-1')
  assert.equal(calls[3].init.headers['X-CUSTOM-CSRF'], 'token-2')
  assert.deepEqual(JSON.parse(calls[1].init.body), { nickname: '新昵称' })
})

test('an unavailable CSRF endpoint prevents the write request', async () => {
  const calls = []
  globalThis.fetch = async path => { calls.push(path); return new Response('Service unavailable', { status: 503 }) }
  await assert.rejects(api.login('user', 'secret'), e => e instanceof ApiError && e.status === 503)
  assert.deepEqual(calls, ['/auth/csrf'])
})

test('handles structured rate-limit responses and Retry-After', async () => {
  globalThis.fetch = async path => path === '/auth/csrf'
    ? Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })
    : Response.json({ code: 'RATE_LIMITED', message: '操作太频繁' }, { status: 429, headers: { 'Retry-After': '30' } })
  await assert.rejects(api.login('user', 'secret'), e => e instanceof ApiError && e.status === 429 && e.retryAfter === 30)
})

test('translates plain-text business errors and safely handles HTML server errors', async () => {
  globalThis.fetch = async () => new Response('Email already exists', { status: 409 })
  await assert.rejects(api.me(), /这个邮箱已注册/)
  globalThis.fetch = async () => new Response('<html>proxy failed</html>', { status: 502 })
  await assert.rejects(api.me(), /操作未完成/)
})

test('shows a network error while preserving cancellation', async () => {
  globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
  await assert.rejects(api.me(), e => e instanceof ApiError && e.status === 0)
  globalThis.fetch = async () => { throw new DOMException('Aborted', 'AbortError') }
  await assert.rejects(api.previewUsername('昵称', '', new AbortController().signal), e => e.name === 'AbortError')
})

test('password validation counts normalized Unicode code points and preserves spaces', () => {
  assert.equal(validatePassword('😀'.repeat(15), '😀'.repeat(15)), '')
  assert.match(validatePassword('😀'.repeat(14), '😀'.repeat(14)), /至少/)
  const password = 'café a memorable phrase'
  assert.equal(validatePassword(password, password.normalize('NFD')), '')
  assert.match(validatePassword(' '.repeat(15), ' '.repeat(14)), /不一致/)
  assert.match(validatePassword('a'.repeat(1025), 'a'.repeat(1025)), /最多/)
})

test('nickname validation matches the Unicode length boundary on the server', () => {
  assert.equal(validateNickname(' 😀 '.trim()), '')
  assert.equal(validateNickname('😀'.repeat(50)), '')
  assert.match(validateNickname('😀'.repeat(51)), /50/)
  assert.match(validateNickname('   '), /1–50/)
})
