import { chromium } from '../data/browser-tools/node_modules/playwright/index.mjs'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import assert from 'node:assert/strict'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const password = readFileSync(path.join(root, '.env.local'), 'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser = await chromium.launch({ executablePath: 'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } })
page.setDefaultTimeout(45000)
const base = 'http://127.0.0.1:5173', errors = [], failures = [], checks = []
let treeId, createdRelationId
page.on('pageerror', e => errors.push(e.message))
page.on('response', r => { if (r.url().includes('/api/') && r.status() >= 400 && !r.url().endsWith('/auth/me')) failures.push(`${r.status()} ${r.url()}`) })
async function api(endpoint, method = 'GET') {
  const csrf = method === 'GET' ? null : await page.request.get(base + '/api/v1/auth/csrf').then(r => r.json())
  const r = await page.request.fetch(base + '/api/v1' + endpoint, { method, headers: csrf ? { [csrf.headerName]: csrf.token } : {} })
  if (!r.ok()) throw new Error(`${method} ${endpoint}: ${r.status()} ${await r.text()}`)
  return r.status() === 204 ? null : await r.json()
}
async function selectMember(formLabel, name, scope = page) {
  await page.waitForFunction(() => [...document.querySelectorAll('.el-loading-mask')].every(element => getComputedStyle(element).display === 'none' || getComputedStyle(element).visibility === 'hidden' || element.getClientRects().length === 0))
  const item = scope.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: new RegExp(`^${formLabel}$`) }) })
  const combo = item.getByRole('combobox')
  await combo.click()
  await combo.fill(name)
  const listbox = page.locator(`[id="${await combo.getAttribute('aria-controls')}"]`)
  const option = listbox.getByRole('option').filter({ hasText: name }).first()
  await option.waitFor({ state: 'visible' })
  await combo.press('ArrowDown')
  await combo.press('Enter')
  await page.keyboard.press('Escape')
  assert.ok((await item.innerText()).includes(name), `Selected name should be shown: ${name}`)
}
try {
  await page.goto(base + '/login')
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登 录' }).click()
  await page.waitForURL('**/dashboard')
  await page.locator('.stat-card strong').first().waitFor()
  const trees = await api('/trees')
  const demo = trees.find(t => t.name === '示例家谱（演示数据）')
  assert.ok(demo, 'Smoke test only operates in the explicitly marked demo tree')
  treeId = demo.id
  await page.evaluate(id => localStorage.setItem('activeTreeId', String(id)), treeId)
  const p = `/trees/${treeId}`
  const members = await api(p + '/members'), original = await api(p + '/relationships')
  const first = members.find(m => m.name === '陈晨'), second = members.find(m => m.name === '陈悦')
  assert.ok(first && second)

  await page.goto(base + '/search')
  await page.getByRole('button', { name: '祖先查询', exact: true }).click()
  await selectMember('起始成员', first.name)
  const query = page.waitForResponse(r => r.url().endsWith(`/members/${first.id}/ancestors`))
  await page.getByRole('button', { name: '开始查询', exact: true }).click()
  assert.equal((await query).status(), 200)
  await page.locator('.results').getByText('陈文山', { exact: true }).waitFor()
  checks.push('ancestor query selects a member by name')
  await page.getByRole('button', { name: '后代查询', exact: true }).click()
  await selectMember('起始成员', '陈文山')
  await page.getByRole('button', { name: '开始查询', exact: true }).click()
  await page.locator('.results').getByText(first.name, { exact: true }).waitFor()
  checks.push('descendant query selects a member by name')
  await page.getByRole('button', { name: '两人关系路径', exact: true }).click()
  await selectMember('起始成员', first.name)
  await selectMember('目标成员', '陈文山')
  await page.getByRole('button', { name: '开始查询', exact: true }).click()
  await page.locator('.el-step__title').filter({ hasText: '陈文山' }).waitFor()
  assert.ok(!/成员 #\d+/.test(await page.locator('.results').innerText()))
  await page.screenshot({ path: path.join(root, 'logs', 'name-search.png'), fullPage: true })
  checks.push('relationship path uses selected names and shows names / Chinese relation labels')

  await page.goto(base + '/stories')
  await page.getByRole('button', { name: '新增', exact: true }).click()
  await selectMember('关联成员（可选）', first.name, page.getByRole('dialog'))
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
  checks.push('story member association selects by name without creating a record')

  const relationsLoaded = page.waitForResponse(r => r.url().endsWith(p + '/relationships') && r.request().method() === 'GET')
  await page.goto(base + '/relations')
  await relationsLoaded
  await page.locator('.relation-list .el-table__body-wrapper tr').first().waitFor()
  await selectMember('当前成员姓名', first.name)
  await selectMember('关联成员姓名', second.name)
  await page.getByText('兄弟姐妹', { exact: true }).first().click()
  assert.ok(!original.some(r => r.relationType === 'SIBLING' && [first.id, second.id].includes(r.fromMemberId) && [first.id, second.id].includes(r.toMemberId)), 'Refuse to alter an existing relation')
  const added = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/relationships'))
  await page.getByRole('button', { name: '确认保存', exact: true }).click()
  const response = await added
  assert.equal(response.status(), 200)
  const created = await response.json()
  createdRelationId = created.id
  await page.getByText(/^关系已保存/).waitFor()
  const row = page.locator('.relation-list .el-table__body-wrapper tr').filter({ hasText: first.name }).filter({ hasText: second.name }).filter({ hasText: '兄弟姐妹' })
  await row.getByRole('button', { name: '解除关系', exact: true }).click()
  const confirm = page.getByRole('dialog', { name: '解除关系', exact: true })
  await confirm.getByRole('button', { name: '取消', exact: true }).click()
  assert.ok((await api(p + '/relationships')).some(r => r.id === createdRelationId))
  await row.getByRole('button', { name: '解除关系', exact: true }).click()
  await page.screenshot({ path: path.join(root, 'logs', 'relation-remove-confirm.png'), fullPage: true })
  const deleted = page.waitForResponse(r => r.request().method() === 'DELETE' && r.url().endsWith(`/relationships/${createdRelationId}`))
  await confirm.getByRole('button', { name: '确认解除', exact: true }).click()
  assert.equal((await deleted).status(), 204)
  await page.getByText('关系已解除，成员资料保留', { exact: true }).waitFor()
  assert.deepEqual((await api(p + '/relationships')).map(r => r.id).sort(), original.map(r => r.id).sort())
  assert.deepEqual((await api(p + '/members')).map(m => m.id).sort(), members.map(m => m.id).sort())
  createdRelationId = undefined
  await page.screenshot({ path: path.join(root, 'logs', 'relations-by-name.png'), fullPage: true })
  checks.push('create one temporary sibling link by names; cancel removal keeps it; confirm removes only that link; original relations and members unchanged')
  assert.deepEqual(errors, [])
  assert.deepEqual(failures, [])
  console.log(JSON.stringify({ result: 'PASS', checks, pageErrors: errors, apiFailures: failures }, null, 2))
} finally {
  if (createdRelationId && treeId) {
    try { await api(`/trees/${treeId}/relationships/${createdRelationId}`, 'DELETE'); console.log('Temporary test relation cleaned up') }
    catch (e) { console.error('Temporary relation cleanup failed:', e.message); process.exitCode = 1 }
  }
  await browser.close()
}
