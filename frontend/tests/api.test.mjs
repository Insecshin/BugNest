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

function waitForAbort(signal) {
  return new Promise((_, reject) => signal.addEventListener('abort', () => reject(signal.reason), { once: true }))
}

test('request timeout reports a Chinese error and does not retry', async t => {
  const controller = new AbortController()
  t.mock.method(AbortSignal, 'timeout', ms => { assert.equal(ms, 8000); return controller.signal })
  let calls = 0
  globalThis.fetch = async (_, init) => { calls++; return waitForAbort(init.signal) }
  const pending = api.me()
  controller.abort(new DOMException('Timed out', 'TimeoutError'))
  await assert.rejects(pending, e => e instanceof ApiError && e.status === 0 && /请求超时/.test(e.message))
  assert.equal(calls, 1)
})

test('timeout also covers reading the response body', async t => {
  const controller = new AbortController()
  t.mock.method(AbortSignal, 'timeout', () => controller.signal)
  let bodyStarted
  const ready = new Promise(resolve => { bodyStarted = resolve })
  globalThis.fetch = async (_, init) => ({ text: () => { bodyStarted(); return waitForAbort(init.signal) } })
  const pending = api.me()
  await ready
  controller.abort(new DOMException('Timed out', 'TimeoutError'))
  await assert.rejects(pending, /请求超时/)
})

test('CSRF and write share one timeout budget and caller cancellation is preserved', async t => {
  const timeout = new AbortController()
  const caller = new AbortController()
  t.mock.method(AbortSignal, 'timeout', () => timeout.signal)
  const signals = []
  let writeStarted
  const ready = new Promise(resolve => { writeStarted = resolve })
  globalThis.fetch = async (path, init) => {
    signals.push(init.signal)
    if (path === '/auth/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })
    writeStarted()
    return waitForAbort(init.signal)
  }
  const pending = api.previewUsername('昵称', '', caller.signal)
  await ready
  assert.equal(signals[0], signals[1])
  caller.abort()
  await assert.rejects(pending, e => e.name === 'AbortError')
  assert.equal(signals.length, 2)
})

test('a timeout during CSRF bootstrap prevents the write', async t => {
  const controller = new AbortController()
  t.mock.method(AbortSignal, 'timeout', () => controller.signal)
  const calls = []
  globalThis.fetch = async (path, init) => { calls.push(path); return waitForAbort(init.signal) }
  const pending = api.login('user', 'secret')
  controller.abort(new DOMException('Timed out', 'TimeoutError'))
  await assert.rejects(pending, /请求超时/)
  assert.deepEqual(calls, ['/auth/csrf'])
})
