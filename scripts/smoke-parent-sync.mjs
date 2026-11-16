import { chromium } from '../data/browser-tools/node_modules/playwright/index.mjs'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import assert from 'node:assert/strict'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
const password=readFileSync(path.join(root,'.env.local'),'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser=await chromium.launch({executablePath:'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',headless:true})
const page=await browser.newPage({viewport:{width:1440,height:1100}})
page.setDefaultTimeout(45000)
const base='http://127.0.0.1:5173', errors=[], checks=[]
let tree, original=[], expectedNew=[], cleanupNeeded=false
page.on('pageerror',e=>errors.push(e.message))
async function api(url,method='GET'){
 const csrf=method==='GET'?null:await page.request.get(base+'/api/v1/auth/csrf').then(r=>r.json())
 const r=await page.request.fetch(base+'/api/v1'+url,{method,headers:csrf?{[csrf.headerName]:csrf.token}:{}})
 if(!r.ok())throw new Error(`${method} ${url}: ${r.status()} ${await r.text()}`)
 return r.status()===204?null:r.json()
}
async function choose(label,name){
 await page.waitForFunction(()=>[...document.querySelectorAll('.el-loading-mask')].every(e=>getComputedStyle(e).display==='none'||e.getClientRects().length===0))
 const item=page.locator('.el-form-item').filter({has:page.locator('.el-form-item__label',{hasText:new RegExp(`^${label}$`)})})
 const combo=item.getByRole('combobox');await combo.click();await combo.fill(name)
 await page.locator(`[id="${await combo.getAttribute('aria-controls')}"]`).getByRole('option').filter({hasText:name}).first().waitFor({state:'visible'})
 await combo.press('ArrowDown');await combo.press('Enter');await page.keyboard.press('Escape')
 assert.ok((await item.innerText()).includes(name))
}
async function cleanup(){
 if(!cleanupNeeded)return
 const links=await api(`/trees/${tree}/relationships`)
 for(const r of links){if(!original.some(old=>old.id===r.id)&&r.relationType==='PARENT'&&expectedNew.some(([f,t])=>r.fromMemberId===f&&r.toMemberId===t))await api(`/trees/${tree}/relationships/${r.id}`,'DELETE')}
 cleanupNeeded=false
}
try{
 await page.goto(base+'/login');await page.getByPlaceholder('请输入用户名').fill('admin');await page.getByPlaceholder('请输入密码').fill(password);await page.getByRole('button',{name:'登 录'}).click();await page.waitForURL('**/dashboard');await page.locator('.stat-card strong').first().waitFor()
 const demo=(await api('/trees')).find(t=>t.name==='示例家谱（演示数据）');assert.ok(demo);tree=demo.id
 const people=await api(`/trees/${tree}/members`);original=await api(`/trees/${tree}/relationships`)
 const parent=people.find(m=>m.name==='陈文山'),spouse=people.find(m=>m.name==='李淑兰'),child=people.find(m=>m.name==='陈晨')
 assert.ok(parent&&spouse&&child)
 assert.ok(original.some(r=>r.relationType==='SPOUSE'&&[r.fromMemberId,r.toMemberId].includes(parent.id)&&[r.fromMemberId,r.toMemberId].includes(spouse.id)))
 const spouses=original.filter(r=>r.relationType==='SPOUSE'&&(r.fromMemberId===parent.id||r.toMemberId===parent.id))
 assert.equal(spouses.length,1,'Refuse to test families with multiple spouses')
 expectedNew=[[parent.id,child.id],[spouse.id,child.id]]
 assert.ok(expectedNew.every(([f,t])=>!original.some(r=>r.relationType==='PARENT'&&r.fromMemberId===f&&r.toMemberId===t)),'Refuse to alter existing links')
 await page.evaluate(id=>localStorage.setItem('activeTreeId',String(id)),tree)
 const loaded=page.waitForResponse(r=>r.url().endsWith(`/trees/${tree}/relationships`));await page.goto(base+'/relations');await loaded
 assert.equal(await page.getByText('添加孩子',{exact:true}).count(),0)
 assert.equal(await page.getByText('添加兄弟姐妹',{exact:true}).count(),0)
 assert.equal(await page.getByPlaceholder('请选择出生日期').count(),0)
 await choose('当前成员姓名',parent.name);await choose('关联成员姓名',child.name)
 await page.getByText('父母 → 子女',{exact:true}).first().click()
 await page.getByText(new RegExp(spouse.name)).first().waitFor()
 await page.screenshot({path:path.join(root,'logs','parent-sync-preview.png'),fullPage:true})
 const mutations=[]
 page.on('request',r=>{if(['POST','PUT','PATCH'].includes(r.method())&&r.url().includes('/api/v1/'))mutations.push(r.url())})
 const saved=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/relationships'))
 cleanupNeeded=true;await page.getByRole('button',{name:'确认保存',exact:true}).click();assert.equal((await saved).status(),200)
 await page.getByText(/^关系已保存/).waitFor()
 const after=await api(`/trees/${tree}/relationships`)
 for(const [f,t] of expectedNew)assert.equal(after.filter(r=>r.relationType==='PARENT'&&r.fromMemberId===f&&r.toMemberId===t).length,1)
 assert.deepEqual((await api(`/trees/${tree}/members`)).map(m=>m.id).sort(),people.map(m=>m.id).sort())
 assert.ok(mutations.every(url=>url.endsWith('/relationships')),'Saving relations must not create/update members')
 checks.push('relation editor selects existing members only; no creation form or member mutation request','adding parent link automatically creates spouse-child link exactly once','member IDs unchanged after save')
 await page.getByRole('button',{name:'显示全部',exact:true}).click();await page.screenshot({path:path.join(root,'logs','parent-sync-saved.png'),fullPage:true})
 await cleanup();assert.deepEqual((await api(`/trees/${tree}/relationships`)).map(r=>r.id).sort(),original.map(r=>r.id).sort())
 checks.push('temporary demo links cleaned up; original relationships preserved')
 const docs=await page.request.get('http://127.0.0.1:8090/v3/api-docs').then(r=>r.json())
 assert.ok(!Object.keys(docs.paths).some(p=>p.endsWith('/children')||p.endsWith('/siblings')))
 checks.push('legacy relation-side member creation endpoints removed')
 assert.deepEqual(errors,[]);console.log(JSON.stringify({result:'PASS',checks,pageErrors:errors},null,2))
}finally{try{await cleanup()}finally{await browser.close()}}
