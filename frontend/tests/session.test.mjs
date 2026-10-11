import { afterEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { api, ApiError } from '../src/api.ts'
import { account, sessionLoading, sessionError, restoreSession, setAccount } from '../src/session.ts'

const originalMe = api.me
afterEach(() => { api.me = originalMe; setAccount(null) })
const current = { userId: 1, userName: 'current', nickname: '当前昵称' }

test('a late unauthorized restore cannot clear a new login', async () => {
  let reject
  api.me = () => new Promise((_, fail) => { reject = fail })
  const pending = restoreSession()
  setAccount(current)
  reject(new ApiError('需要登录', 401))
  await pending
  assert.deepEqual(account.value, current)
  assert.equal(sessionLoading.value, false)
  assert.equal(sessionError.value, '')
})

test('a late success cannot restore a logged-out account or overwrite a nickname change', async () => {
  for (const updated of [null, { ...current, nickname: '新昵称' }]) {
    let resolve
    api.me = () => new Promise(done => { resolve = done })
    const pending = restoreSession()
    setAccount(updated)
    resolve(current)
    await pending
    assert.deepEqual(account.value, updated)
  }
})

test('overlapping restores only let the newest response update state', async () => {
  const responses = []
  api.me = () => new Promise(resolve => responses.push(resolve))
  const old = restoreSession(), recent = restoreSession()
  responses[0]({ ...current, nickname: '旧昵称' })
  await old
  assert.equal(sessionLoading.value, true)
  responses[1](current)
  await recent
  assert.deepEqual(account.value, current)
  assert.equal(sessionLoading.value, false)
})
