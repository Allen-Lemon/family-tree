import {chromium} from '../data/browser-tools/node_modules/playwright/index.mjs'
import {readFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import path from 'node:path'
import assert from 'node:assert/strict'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),base='http://127.0.0.1:5173'
const password=readFileSync(path.join(root,'.env.local'),'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser=await chromium.launch({executablePath:'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',headless:true})
const page=await browser.newPage({viewport:{width:1600,height:1100}});page.setDefaultTimeout(45000)
const errors=[];page.on('pageerror',e=>errors.push(e.message))
try{
 await page.goto(base+'/login');await page.getByPlaceholder('请输入用户名').fill('admin');await page.getByPlaceholder('请输入密码').fill(password);await page.getByRole('button',{name:'登 录'}).click();await page.waitForURL('**/dashboard');await page.locator('.stat-card strong').first().waitFor()
 const trees=await page.request.get(base+'/api/v1/trees').then(r=>r.json()),tree=trees.find(t=>t.name==='示例家谱（演示数据）')||trees[0];assert.ok(tree)
 const people=await page.request.get(base+`/api/v1/trees/${tree.id}/members`).then(r=>r.json())
 await page.goto(base+`/graph?treeId=${tree.id}`);await page.locator('.graph-node').first().waitFor();await page.getByText('所有人关系情况',{exact:true}).click()
 const rows=page.locator('.relationship-summary tbody tr[data-member-id]');assert.equal(await rows.count(),people.length)
 const reference=people.find(p=>p.name==='陈晨')||people[0];assert.ok(reference)
 await page.getByLabel('选择参照成员姓名',{exact:true}).selectOption(String(reference.id))
 const self=page.locator(`.relationship-summary tr[data-member-id="${reference.id}"]`);assert.ok((await self.innerText()).includes('本人'))
 await page.getByLabel('按姓名筛选所有人关系',{exact:true}).fill(reference.name);assert.equal(await rows.count(),people.filter(p=>p.name.includes(reference.name)).length)
 await page.getByLabel('按姓名筛选所有人关系',{exact:true}).fill('');await page.screenshot({path:path.join(root,'logs','all-people-relationships.png'),fullPage:true})
 assert.deepEqual(errors,[]);console.log(JSON.stringify({result:'PASS',members:people.length,checks:['all people listed including disconnected members','reference self and indirect recorded paths','name filter','existing graph preserved'],pageErrors:errors},null,2))
}finally{await browser.close()}
