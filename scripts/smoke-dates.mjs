import { chromium } from '../data/browser-tools/node_modules/playwright/index.mjs'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const password = readFileSync(path.join(root, '.env.local'), 'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser = await chromium.launch({ executablePath: 'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe', headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
page.setDefaultTimeout(45000)
const errors = [], failures = [], checks = []
page.on('pageerror', e => errors.push(e.message))
page.on('response', r => { if (r.url().includes('/api/') && r.status() >= 400 && !r.url().endsWith('/auth/me')) failures.push(`${r.status()} ${r.url()}`) })
const base = 'http://127.0.0.1:5173'
async function openPicker(placeholder, scope = page) {
  const input = scope.getByPlaceholder(placeholder, { exact: true })
  await input.waitFor()
  assert.equal(await input.getAttribute('readonly'), '')
  assert.equal(await input.isDisabled(), false)
  await input.click()
  const panel = page.locator('.el-picker-panel:visible')
  await panel.waitFor()
  return { input, panel }
}
async function chooseDay(panel, day) {
  await panel.locator('td.available:not(.prev-month):not(.next-month)').filter({ has: page.locator('.el-date-table-cell__text', { hasText: new RegExp(`^${day}$`) }) }).click()
}
try {
  await page.goto(base + '/login')
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button', { name: '登 录' }).click()
  await page.waitForURL('**/dashboard')
  await page.locator('.stat-card strong').first().waitFor()
  const treeId = await page.evaluate(() => localStorage.getItem('activeTreeId'))
  const members = await page.request.get(`${base}/api/v1/trees/${treeId}/members`).then(r => r.json())
  const member = members.find(m => /^\d{4}-\d{2}-\d{2}$/.test(m.birthDate || ''))
  assert.ok(member, 'A member with a complete birth date is needed for round-trip verification')

  await page.goto(base + '/members')
  await page.getByRole('button', { name: '新增成员', exact: true }).click()
  const dialog = page.getByRole('dialog')
  let { input, panel } = await openPicker('请选择出生日期', dialog)
  await chooseDay(panel, 15)
  assert.match(await input.inputValue(), /^\d{4}-\d{2}-15$/)
  await input.hover()
  await dialog.locator('.el-date-editor .clear-icon').click()
  assert.equal(await input.inputValue(), '')
  await page.screenshot({ path: path.join(root, 'logs', 'date-member-create.png'), fullPage: true })
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  checks.push('new member: calendar selection, readonly input, clearing')

  await page.goto(`${base}/members/${member.id}`)
  ;({ input, panel } = await openPicker('请选择出生日期'))
  assert.equal(await input.inputValue(), member.birthDate)
  await chooseDay(panel, Number(member.birthDate.slice(-2)))
  const write = page.waitForRequest(r => r.method() === 'PUT' && r.url().endsWith(`/members/${member.id}`))
  await page.getByRole('button', { name: '保存资料', exact: true }).click()
  assert.equal((await write).postDataJSON().birthDate, member.birthDate)
  await page.getByText('成员资料已保存', { exact: true }).waitFor()
  await page.reload()
  await page.getByRole('heading', { name: member.name, exact: true }).first().waitFor()
  assert.equal(await page.getByPlaceholder('请选择出生日期').inputValue(), member.birthDate)
  ;({ panel } = await openPicker('请选择逝世日期'))
  await page.screenshot({ path: path.join(root, 'logs', 'date-member-detail.png'), fullPage: true })
  await page.keyboard.press('Escape')
  checks.push('member detail: birth/death calendars; existing birth date saved as YYYY-MM-DD and reloaded unchanged')

  // Fixture-only DATE fields verify dynamic rendering without creating fields or records in the remote database.
  const fieldBase = `**/api/v1/trees/${treeId}/custom-fields`
  await page.route(fieldBase, async route => {
    const response = await route.fetch()
    const fields = await response.json()
    await route.fulfill({ response, json: [...fields, { id: 999999, field_key: 'date_picker_fixture', label: '日期控件测试', field_type: 'DATE', sensitive: false }] })
  })
  await page.route(fieldBase + `/members/${member.id}`, async route => {
    const response = await route.fetch()
    const fields = await response.json()
    await route.fulfill({ response, json: [...fields, { field_key: 'date_picker_fixture', label: '日期控件测试', field_type: 'DATE', sensitive: false, value_text: '2000-01-02' }] })
  })
  await page.reload()
  await page.getByRole('tab', { name: '族谱扩展资料' }).click()
  ;({ input, panel } = await openPicker('请选择日期控件测试'))
  assert.equal(await input.inputValue(), '2000-01-02')
  await chooseDay(panel, 3)
  assert.equal(await input.inputValue(), '2000-01-03')
  await page.unrouteAll({ behavior: 'wait' })
  checks.push('DATE custom fields use calendar controls (browser fixture only; no database writes)')

  await page.goto(`${base}/relations?memberId=${member.id}`)
  await page.getByRole('heading', { name: '关系编辑', exact: true }).waitFor()
  assert.equal(await page.getByPlaceholder('请选择出生日期').count(), 0)
  assert.equal(await page.getByText('添加孩子', { exact: true }).count(), 0)
  checks.push('relation editing has no member creation or birth date input; select existing members only')

  await page.goto(base + '/events')
  await page.getByRole('button', { name: '新增', exact: true }).click()
  ;({ input, panel } = await openPicker('请选择事件日期', page.getByRole('dialog')))
  await chooseDay(panel, 15)
  assert.match(await input.inputValue(), /^\d{4}-\d{2}-15$/)
  await input.click()
  await page.screenshot({ path: path.join(root, 'logs', 'date-event-create.png'), fullPage: true })
  await page.keyboard.press('Escape')
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
  checks.push('family event: calendar selection; no test event created')

  assert.deepEqual(errors, [])
  assert.deepEqual(failures, [])
  assert.equal((await fetch('http://127.0.0.1:8090/actuator/health').then(r => r.json())).status, 'UP')
  console.log(JSON.stringify({ result: 'PASS', checks, pageErrors: errors, apiFailures: failures }, null, 2))
} finally { await browser.close() }
