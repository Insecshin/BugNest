import { spawn } from 'node:child_process'
import { mkdir, writeFile, appendFile } from 'node:fs/promises'
import { resolve, join } from 'node:path'
import { randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'
import https from 'node:https'
import http from 'node:http'

// Local Postman processes give each client a separate cookie jar; no cloud upload.
const root = resolve('target/loadtest')
const cli = resolve(process.env.POSTMAN_BIN || 'target/loadtest/postman-cli/node_modules/@postman/pm-bin-windows-x64/bin/postman.exe')
const baseUrl = process.env.LOADTEST_URL || 'https://localhost:5173'
assert.ok(new URL(baseUrl).hostname === 'localhost' || new URL(baseUrl).hostname === '127.0.0.1', 'Only local targets are allowed')
const runId = randomUUID().replaceAll('-', '').slice(0, 10)
const metrics = []
const runs = []
await mkdir(root, { recursive: true })

const recorder = (stage, statuses, extra = '') => `
const accepted = ${JSON.stringify(statuses)};
const sessionCookie = pm.response.headers.get('Set-Cookie');
if (sessionCookie) pm.collectionVariables.set('sessionCookie', sessionCookie.split(';')[0]);
let passed = accepted.includes(pm.response.code);
pm.test('${stage}: expected HTTP status', () => pm.expect(pm.response.code).to.be.oneOf(accepted));
try { ${extra} } catch (e) { passed = false; pm.test('${stage}: response contract', () => { throw e; }); }
console.log('BN_METRIC ' + JSON.stringify({ stage: '${stage}', status: pm.response.code, ms: pm.response.responseTime, sentAt: Number(pm.collectionVariables.get('sentAt')) || Date.now() - pm.response.responseTime, passed, retryAfter: Number(pm.response.headers.get('Retry-After')) || 0, userId: Number(pm.collectionVariables.get('userId')) || null }));
`
const csrfScript = `
pm.expect(pm.response.code).to.equal(200);
const csrf = pm.response.json();
pm.expect(csrf.headerName).to.be.a('string'); pm.expect(csrf.token).to.be.a('string');
pm.collectionVariables.set('csrfHeader', csrf.headerName); pm.collectionVariables.set('csrfToken', csrf.token);
const cookie = pm.response.headers.get('Set-Cookie');
if (cookie) pm.collectionVariables.set('previousCookie', cookie.split(';')[0]);
`
const identity = `
const value = pm.response.json();
pm.expect(value.userName).to.equal(pm.iterationData.get('userName'));
pm.expect(value).not.to.have.property('password'); pm.expect(value).not.to.have.property('passwordHash'); pm.expect(value).not.to.have.property('email');
if (pm.collectionVariables.get('userId')) pm.expect(value.userId).to.equal(Number(pm.collectionVariables.get('userId')));
pm.collectionVariables.set('userId', value.userId);
`
const loginContract = `
if (pm.response.code === 200) {
${identity}
const cookie = pm.response.headers.get('Set-Cookie');
pm.expect(cookie).to.include('HttpOnly'); pm.expect(cookie).to.include('Secure'); pm.expect(cookie).to.include('SameSite=Lax');
if (pm.collectionVariables.get('previousCookie')) pm.expect(cookie.split(';')[0]).not.to.equal(pm.collectionVariables.get('previousCookie'));
pm.collectionVariables.set('authenticated', true);
} else if (pm.response.code === 429) { pm.expect(Number(pm.response.headers.get('Retry-After'))).to.be.above(0); }
`
const barrier = `
console.log('BN_READY');
const remaining = Number(pm.iterationData.get('startAt')) - Date.now();
setTimeout(() => pm.collectionVariables.set('sentAt', Date.now()), Math.max(0, remaining));
`
function req(name, method, url, statuses, { body, csrf = false, before = '', extra = '', contentType = 'application/json' } = {}) {
  return { name, method, url: '{{baseUrl}}' + url,
    headers: method === 'GET' ? [] : [{ key: 'Content-Type', value: contentType }],
    ...(body === undefined ? {} : { body: { type: 'text', content: body } }), before: "if (pm.collectionVariables.get('sessionCookie')) pm.request.headers.upsert({key: 'Cookie', value: pm.collectionVariables.get('sessionCookie')});\n" + (csrf ? "pm.request.headers.upsert({key: pm.collectionVariables.get('csrfHeader'), value: pm.collectionVariables.get('csrfToken')});\n" : '') + before,
    after: recorder(name, statuses, extra) }
}
const csrf = name => req(name, 'GET', '/auth/csrf', [200], { extra: csrfScript })
const login = (name, statuses = [200], options = {}) => req(name, 'POST', '/auth/login', statuses, {
  csrf: true, body: '{"identifier":"{{identifier}}","password":"{{password}}"}', before: barrier,
  extra: loginContract, ...options })
const me = name => req(name, 'GET', '/accounts/me', [200], { extra: identity })
async function collection(name, requests) {
  const dir = join(root, 'collections')
  await mkdir(dir, { recursive: true })
  const file = join(dir, name + '.collection.json')
  await writeFile(file, JSON.stringify({ info: { name, schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json' }, variable: [{ key: 'baseUrl', value: baseUrl }],
    item: requests.map(r => ({ name: r.name, request: { method: r.method, url: r.url, header: r.headers, ...(r.body ? { body: { mode: 'raw', raw: r.body.content } } : {}) },
      event: [{ listen: 'prerequest', script: { type: 'text/javascript', exec: (r.before || "pm.collectionVariables.set('sentAt', Date.now());").split('\n') } },
        { listen: 'test', script: { type: 'text/javascript', exec: r.after.split('\n') } }] })) }, null, 2))
  return file
}
function execute(args, onLine = () => {}) {
  return new Promise((done, reject) => {
    const env = { ...process.env, NODE_OPTIONS: '--max-old-space-size=256', NO_PROXY: 'localhost,127.0.0.1', no_proxy: 'localhost,127.0.0.1' }
    delete env.HTTP_PROXY; delete env.HTTPS_PROXY; delete env.ALL_PROXY;
    delete env.http_proxy; delete env.https_proxy; delete env.all_proxy;
    const child = spawn(cli, args, { env, windowsHide: true })
    let output = ''
    child.stdout.on('data', chunk => { output += chunk; onLine(chunk.toString()) })
    child.stderr.on('data', chunk => { output += chunk })
    child.on('error', reject)
    child.on('close', code => done({ code, output }))
  })
}
async function run(name, path, data, onReady) {
  if (data.clients) return nativeParallel(name, path, data)
  const file = join(root, `${name}-${randomUUID()}.json`)
  await writeFile(file, JSON.stringify(Array.isArray(data) ? data : [data.clients ? { ...data, clients: undefined, clientsJson: JSON.stringify(data.clients) } : data]))
  const result = await execute(['collection', 'run', path, '--iteration-data', file, ...(Array.isArray(data) ? ['--iteration-count', String(data.length)] : []), '--insecure', '--timeout', '90000', '--timeout-request', '10000', '--no-report-events'], chunk => {
    if (onReady && chunk.includes('BN_READY')) { const callback = onReady; onReady = null; callback() }
  })
  const consoleText = result.output.split('\n').filter(line => /BN_METRIC|^\s*[│|] /.test(line)).map(line => line.replace(/^\s*[│|] /, '').trimEnd()).join('')
  const parsed = [...consoleText.matchAll(/BN_METRIC (\{.*?\})/g)].map(match => JSON.parse(match[1]))
  if (result.output.includes('ESOCKETTIMEDOUT')) for (const metric of parsed) if (!metric.status) { metric.status = 0; metric.ms = null; metric.error = 'ESOCKETTIMEDOUT' }
  metrics.push(...parsed.map(m => ({ scenario: name, ...m })))
  runs.push({ scenario: name, exitCode: result.code, metrics: parsed.length })
  await appendFile(join(root, `checkpoint-${runId}.ndjson`), JSON.stringify({ scenario: name, exitCode: result.code, metrics: parsed }) + '\n')
  if (result.code || !parsed.length) {
    await writeFile(join(root, `${name}.private.log`), result.output)
    console.log(JSON.stringify({ scenario: name, exitCode: result.code, metrics: parsed.length, diagnostic: 'Private local log saved; not included in report' }))
  }
  if (data.clients) assert.equal(parsed.filter(m => m.stage.startsWith('login')).length, data.clients.length * (name === 'ip-saturation' ? 4 : 1), 'Parallel script did not produce all expected login results')
  return parsed
}
async function nativeParallel(scenario, kind, data) {
  const records = []
  async function send(stage, state, method, path, body, statuses, user) {
    const sentAt = Date.now(), started = performance.now()
    const url = new URL(path, baseUrl), headers = {Cookie: state.cookie || ''}
    if (body !== undefined) { headers['Content-Type'] = 'application/json'; headers[state.headerName] = state.token }
    const response = await new Promise(done => {
      const request = (url.protocol === 'https:' ? https : http).request(url, {method, headers, rejectUnauthorized: false, agent: false}, response => {
        let text = ''; response.on('data', chunk => { text += chunk }); response.on('end', () => {
          let body = text; try { body = JSON.parse(text) } catch {}
          done({status: response.statusCode, headers: response.headers, body})
        })
      })
      request.setTimeout(10000, () => request.destroy(new Error('REQUEST_TIMEOUT')))
      request.on('error', error => done({status: 0, headers: {}, error: error.code || error.message, body: ''}))
      if (body !== undefined) request.write(JSON.stringify(body)); request.end()
    })
    let passed = statuses.includes(response.status)
    try {
      if (response.status === 200 && (path === '/auth/login' || path === '/accounts/me')) {
        assert.equal(response.body.userName, user.userName)
        if (state.userId) assert.equal(response.body.userId, state.userId)
        state.userId = response.body.userId
        for (const field of ['email','password','passwordHash']) assert.ok(!Object.hasOwn(response.body, field))
      }
      if (response.status === 200 && path === '/auth/login') {
        const cookie = response.headers['set-cookie'].join(';')
        assert.ok(cookie.includes('Secure') && cookie.includes('HttpOnly') && cookie.includes('SameSite=Lax'))
        assert.notEqual(cookie.split(';')[0], state.cookie)
      }
      if (response.status === 429) assert.ok(Number(response.headers['retry-after']) > 0)
    } catch { passed = false }
    const cookie = response.headers['set-cookie']?.find(c => c.startsWith('__Host-bugnest_session='))
    if (cookie) state.cookie = cookie.split(';')[0]
    if (path === '/auth/csrf' && response.status === 200) { state.token = response.body.token; state.headerName = response.body.headerName }
    const metric = {scenario, stage, status: response.status, ms: Math.round((performance.now()-started)*100)/100, sentAt, passed, retryAfter: Number(response.headers['retry-after']) || 0, userId: state.userId || null, ...(response.error ? {error: response.error} : {})}
    records.push(metric); await appendFile(join(root, `checkpoint-${runId}.ndjson`), JSON.stringify(metric)+'\n')
    return response.status
  }
  const states = data.clients.map(() => ({}))
  await Promise.all(states.map((s,i) => send('csrf',s,'GET','/auth/csrf',undefined,[200],data.clients[i])))
  assert.ok(states.every(s=>s.token&&s.cookie), 'CSRF bootstrap failed')
  await new Promise(done=>setTimeout(done,Math.max(0,data.startAt-Date.now())))
  await Promise.all(data.clients.map(async(user,i)=>{
    const state=states[i]
    for(let n=0;n<(kind==='sustained'?4:1);n++) {
      if(n) await send('csrf',state,'GET','/auth/csrf',undefined,[200],user)
      const status=await send(kind==='sustained'?'login-'+n:'login',state,'POST','/auth/login',{identifier:user.identifier,password:kind==='wrong'?'wrong password':user.password},kind==='wrong'?[401,429]:kind==='sustained'?[200,429]:[200],user)
      if(status===200) await send('me',state,'GET','/accounts/me',undefined,[200],user)
    }
    if(kind==='happy') {
      await send('logout-csrf',state,'GET','/auth/csrf',undefined,[200],user)
      await send('logout',state,'POST','/auth/logout',{},[204],user)
      await send('after-logout',state,'GET','/accounts/me',undefined,[401],user)
    }
  }))
  metrics.push(...records); runs.push({scenario,exitCode: records.some(r=>!r.passed)?1:0,metrics:records.length,runner:'Node standard HTTP client'})
  return records
}
const delay = ms => new Promise(done => setTimeout(done, ms))
const summarize = values => {
  const times = values.map(v => v.ms).sort((a, b) => a - b)
  const percentile = p => times.length ? times[Math.ceil(times.length * p) - 1] : null
  return { samples: values.length, statuses: values.reduce((a, v) => ({ ...a, [v.status]: (a[v.status] || 0) + 1 }), {}), contractFailures: values.filter(v => !v.passed).length,
    avgMs: times.length ? Math.round(times.reduce((a, b) => a + b, 0) / times.length) : null, p95Ms: percentile(.95), maxMs: times.at(-1) ?? null,
    sendSpreadMs: values.length ? Math.max(...values.map(v => v.sentAt)) - Math.min(...values.map(v => v.sentAt)) : null }
}
assert.equal(summarize([{ ms: 1, status: 200, passed: true, sentAt: 5 }, { ms: 10, status: 429, passed: false, sentAt: 7 }]).p95Ms, 10)

const mode = process.argv[2] || 'smoke'
const setup = await collection('setup', [csrf('setup-csrf'), req('signup', 'POST', '/auth/sign-up', [201], { csrf: true,
  body: '{"email":"{{email}}","nickname":"压测临时账户","userName":"{{userName}}","password":"{{password}}","confirmPassword":"{{password}}"}', extra: "pm.collectionVariables.set('userId', pm.response.json().userId);" })])
const happy = await collection('login-session', [csrf('csrf'), login('login'), me('me'), csrf('logout-csrf'), req('logout', 'POST', '/auth/logout', [204], { csrf: true }), req('after-logout', 'GET', '/accounts/me', [401])])
const users = Array.from({ length: ['full', 'scale'].includes(mode) ? 10 : 1 }, (_, i) => ({ userName: `lt_${runId}_${i}`, email: `lt_${runId}_${i}@example.invalid`, password: `Temporary test phrase ${randomUUID()}` }))
for (const user of users) user.identifier = user.email
const setupResults = await run('setup', setup, users)
assert.equal(setupResults.filter(r => r.stage === 'signup' && r.status === 201 && r.passed).length, users.length, 'Setup failed; no valid load test is possible')
await run('baseline', happy, Array.from({ length: mode === 'full' ? 5 : 1 }, () => users[0]))
if (mode === 'scale') await run('large-db-burst', 'happy', { clients: users.map((u, i) => ({ ...u, identifier: i % 2 ? u.userName : u.email })), startAt: Date.now() + 5000 })
if (mode === 'full') {
  for (let round = 1; round <= 3; round++) {
    const startAt = Date.now() + 5000
    await run(`burst-${round}`, 'happy', { clients: users.map((u, i) => ({ ...u, identifier: i % 2 ? u.userName : u.email })), startAt })
    console.log(JSON.stringify({ round, ...summarize(metrics.filter(m => m.scenario === `burst-${round}` && m.stage === 'login')) }))
    await delay(30000)
  }
  await run('ip-saturation', 'sustained', { clients: users, startAt: Date.now() + 5000 })
  await delay(65000)
  const failureResponse = "pm.expect(pm.response.json().code).to.equal('AUTHENTICATION_FAILED'); pm.expect(pm.response.json().message).to.equal('用户名或密码错误');"
  const cooldown = await collection('account-cooldown', [csrf('csrf'), ...Array.from({ length: 5 }, (_, i) => login(`wrong-${i}`, [401], { body: '{"identifier":"{{identifier}}","password":"wrong password"}', extra: failureResponse })),
    login('correct-during-cooldown', [429]), login('alias-during-cooldown', [429], { body: '{"identifier":"{{userName}}","password":"{{password}}"}' }),
    login('correct-after-cooldown', [200], { before: "setTimeout(() => pm.collectionVariables.set('sentAt', Date.now()), 31000);" }), me('me')])
  await run('account-cooldown', cooldown, users[0])
  const boundaries = await collection('boundaries', [
    req('login-without-csrf', 'POST', '/auth/login', [403], { body: '{"identifier":"{{identifier}}","password":"{{password}}"}' }), csrf('csrf'),
    login('unknown-account', [401], { body: '{"identifier":"missing_loadtest_account","password":"wrong password"}', extra: failureResponse }),
    login('malformed-json', [400], { body: '{', extra: '' }), login('null-json', [400], { body: 'null', extra: '' }),
    login('missing-password', [400], { body: '{"identifier":"test"}', extra: '' }),
    login('oversize-body', [400], { body: JSON.stringify({ identifier: 'test', password: 'a'.repeat(8200) }), extra: '' }),
    login('wrong-content-type', [400], { contentType: 'text/plain', extra: '' }),
    login('empty-password', [400], { body: '{"identifier":"{{identifier}}","password":""}', extra: '' }),
    login('valid', [200]), me('me'), csrf('fresh-csrf-for-permission'),
    req('other-account-nickname', 'PATCH', '/accounts/999999/nickname', [403], { csrf: true, body: '{"nickname":"invalid target"}', before: '' })
  ])
  await run('boundaries', boundaries, users[1])
}
if (mode === 'resilience') {
  const command = (args) => new Promise((done, reject) => {
    const child = spawn('docker', args, { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
    let output = ''
    child.stdout.on('data', chunk => { output += chunk })
    child.stderr.on('data', chunk => { output += chunk })
    child.on('error', reject)
    child.on('close', code => code ? reject(new Error('Docker command failed')) : done(output))
  })
  await run('same-account-concurrent', 'wrong', { clients: Array.from({ length: 10 }, () => users[0]), startAt: Date.now() + 5000 })
  const cooldown = await collection('same-account-cooldown', [csrf('csrf'), login('correct-login', [200]), me('me')])
  await run('same-account-cooldown', cooldown, users[0])
  // Fault injection is restricted to containers created for this test.
  for (const dependency of ['redis', 'postgres']) {
    const container = `bugnest-loadtest-${dependency}-1011`
    const fault = await collection(`${dependency}-outage`, [csrf('csrf'), login('outage-login', [503], { extra: "pm.expect(pm.response.responseTime).to.be.below(5000); pm.expect(pm.response.json().code).to.equal('AUTHENTICATION_UNAVAILABLE');" })])
    let stopping
    try {
      await run(`${dependency}-outage`, fault, { ...users[0], identifier: 'missing_fault_test', startAt: Date.now() + 15000 }, () => { stopping = command(['stop', '--time', '0', container]) })
      if (stopping) await stopping
    } finally {
      await command(['start', container])
      await delay(5000)
    }
    const recovery = await collection(`${dependency}-recovery`, [csrf('csrf'), login('unknown-login', [401], { body: '{"identifier":"missing_fault_test","password":"wrong password"}', extra: '' })])
    await run(`${dependency}-recovery`, recovery, users[0])
  }
}
const summary = Object.fromEntries([...new Set(metrics.map(m => m.scenario))].map(scenario => [scenario, summarize(metrics.filter(m => m.scenario === scenario && m.stage.startsWith('login')))]))
const resultFile = join(root, `results-${mode}.json`)
await writeFile(resultFile, JSON.stringify({ runId, baseUrl, runner: 'Postman CLI contract checks and Node standard HTTP concurrency', mode, runs, summary, metrics }, null, 2))
console.log(JSON.stringify({ resultFile, summary, runFailures: runs.filter(r => r.exitCode).length }))
process.exitCode = runs.some(r => r.exitCode !== 0) ? 1 : 0
