import {chromium} from '../data/browser-tools/node_modules/playwright/index.mjs'
import {readFileSync} from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import assert from 'node:assert/strict'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
const password=readFileSync(path.join(root,'.env.local'),'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser=await chromium.launch({executablePath:'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',headless:true})
const page=await browser.newPage({viewport:{width:1440,height:1000}})
page.setDefaultTimeout(45000)
const errors=[]
page.on('pageerror',e=>errors.push(e.message))
try {
  await page.goto('http://127.0.0.1:5173/login')
  await page.getByRole('heading',{name:'登录冯氏',exact:true}).waitFor()
  assert.equal(await page.title(),'登录 · 冯氏')
  assert.equal(await page.locator('.logo').innerText(),'冯')
  assert.ok(!/华谱|华普/.test(await page.locator('body').innerText()))
  await page.screenshot({path:path.join(root,'logs','feng-login.png'),fullPage:true})
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill(password)
  await page.getByRole('button',{name:'登 录'}).click()
  await page.waitForURL('**/dashboard')
  await page.locator('.stat-card strong').first().waitFor()
  assert.equal(await page.title(),'总览统计 · 冯氏')
  assert.equal(await page.locator('.brand strong').innerText(),'冯氏')
  assert.equal(await page.locator('.brand-mark').innerText(),'冯')
  assert.equal(await page.locator('.breadcrumb span').innerText(),'冯氏')
  assert.ok(!/华谱|华普/.test(await page.locator('body').innerText()))
  await page.screenshot({path:path.join(root,'logs','feng-dashboard.png'),fullPage:true})
  const health=await fetch('http://127.0.0.1:8090/actuator/health').then(r=>r.json())
  assert.equal(health.status,'UP')
  assert.deepEqual(errors,[])
  console.log(JSON.stringify({result:'PASS',brand:'冯氏',checks:['login heading and logo','browser titles','real administrator login','sidebar brand and logo','breadcrumb','no old brand','backend health UP'],pageErrors:errors,screenshots:['logs/feng-login.png','logs/feng-dashboard.png']},null,2))
} finally {await browser.close()}
