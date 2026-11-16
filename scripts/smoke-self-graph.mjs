import {chromium} from '../data/browser-tools/node_modules/playwright/index.mjs'
import {readFileSync} from 'node:fs'
import {randomBytes} from 'node:crypto'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import assert from 'node:assert/strict'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),base='http://127.0.0.1:5173'
const adminPassword=readFileSync(path.join(root,'.env.local'),'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser=await chromium.launch({executablePath:'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',headless:true})
const context=await browser.newContext({viewport:{width:1500,height:1100}}),page=await context.newPage(),graph=await context.newPage()
const memberContext=await browser.newContext({viewport:{width:1440,height:1000}}),self=await memberContext.newPage()
for(const p of [page,graph,self])p.setDefaultTimeout(45000)
let accountId,treeId,newRelationId,originalMembers=[],originalRelations=[]
const errors=[],checks=[],username='verify_self_'+Date.now(),password=randomBytes(18).toString('hex')
for(const p of [page,graph,self])p.on('pageerror',e=>errors.push(e.message))
async function api(client,url,method='GET',data){const headers={};if(method!=='GET'){const c=await client.get(base+'/api/v1/auth/csrf').then(r=>r.json());headers[c.headerName]=c.token}return client.fetch(base+'/api/v1'+url,{method,headers,data})}
async function get(client,url){const r=await api(client,url);assert.ok(r.ok(),`${url}: ${r.status()}`);return r.json()}
async function login(p,name,pass){await p.goto(base+'/login');await p.getByPlaceholder('请输入用户名').fill(name);await p.getByPlaceholder('请输入密码').fill(pass);await p.getByRole('button',{name:'登 录'}).click();await p.waitForURL('**/dashboard');await p.locator('.stat-card strong').first().waitFor()}
async function choose(label,name){await page.waitForFunction(()=>[...document.querySelectorAll('.el-loading-mask')].every(e=>getComputedStyle(e).display==='none'||e.getClientRects().length===0));const item=page.locator('.el-form-item').filter({has:page.locator('.el-form-item__label',{hasText:new RegExp(`^${label}$`)})});const input=item.getByRole('combobox');await item.locator('.el-select__wrapper').click();await input.fill(name);const option=page.locator(`[id="${await input.getAttribute('aria-controls')}"]`).getByRole('option').filter({hasText:name}).first();await option.waitFor({state:'visible'});await input.press('ArrowDown');await input.press('Enter');await page.locator('h1').click()}
try{
 await login(page,'admin',adminPassword)
 const trees=await get(context.request,'/trees'),tree=trees.find(t=>t.name==='示例家谱（演示数据）');assert.ok(tree);treeId=tree.id
 originalMembers=await get(context.request,`/trees/${treeId}/members`);originalRelations=await get(context.request,`/trees/${treeId}/relationships`)
 const person=originalMembers.find(m=>m.name==='陈晨'),other=originalMembers.find(m=>m.name==='陈悦');assert.ok(person&&other)
 const existingUsers=await get(context.request,'/admin/users');assert.ok(!existingUsers.some(u=>u.memberId===person.id),'Refuse to rebind an existing business account')
 const created=await api(context.request,'/admin/users','POST',{username,password,role:'MEMBER',treeIds:[treeId]});assert.equal(created.status(),200);accountId=(await created.json()).id
 const bound=await api(context.request,`/admin/users/${accountId}/member`,'PUT',{memberId:person.id});assert.equal(bound.status(),200)
 await login(self,username,password)
 const me=await get(memberContext.request,'/auth/me');assert.equal(me.memberId,person.id);assert.equal(me.memberTreeId,treeId)
 await self.goto(base+`/members/${person.id}`);await self.getByRole('heading',{name:person.name,exact:true}).first().waitFor();await self.getByRole('button',{name:'保存资料',exact:true}).waitFor()
 const ownSaved=self.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith(`/members/${person.id}`));await self.getByRole('button',{name:'保存资料',exact:true}).click();assert.equal((await ownSaved).status(),200)
 await self.getByText('成员资料已保存',{exact:true}).waitFor();await self.screenshot({path:path.join(root,'logs','self-profile-edit.png'),fullPage:true})
 await self.goto(base+`/members/${other.id}`);await self.getByRole('heading',{name:other.name,exact:true}).first().waitFor();assert.equal(await self.getByRole('button',{name:'保存资料',exact:true}).count(),0)
 assert.equal(await self.locator('.detail-main input').first().isDisabled(),true)
 assert.equal((await api(memberContext.request,`/trees/${treeId}/members/${other.id}`,'PUT',other)).status(),403)
 assert.equal((await api(memberContext.request,`/trees/${treeId}/members`,'POST',{name:'MUST_NOT_CREATE'})).status(),403)
 assert.equal((await api(memberContext.request,`/trees/${treeId}/relationships`,'POST',{fromMemberId:person.id,toMemberId:other.id,type:'SIBLING'})).status(),403)
 assert.equal((await api(memberContext.request,`/trees/${treeId}`,'PUT',{name:tree.name,description:tree.description})).status(),403)
 checks.push('bound account edits own basic profile only; other members read-only; server denies create/relation/tree edits even with EDITOR tree grant')
 await page.goto(base+`/trees/${treeId}`);await page.getByRole('button',{name:'编辑家谱',exact:true}).click();const savedTree=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith(`/trees/${treeId}`));await page.getByRole('dialog').getByRole('button',{name:'保存家谱',exact:true}).click();assert.equal((await savedTree).status(),200);checks.push('administrator edits family name/description via UI (saved original values)')
 await graph.goto(base+`/graph?treeId=${treeId}`);await graph.locator('.graph-node').first().waitFor()
 assert.equal(await graph.locator('.graph-node').count(),originalMembers.length)
 assert.equal(await graph.locator('.graph-link').count(),originalRelations.length)
 assert.ok((await graph.locator('body').innerText()).includes('男'));assert.ok((await graph.locator('body').innerText()).includes('女'))
 await graph.screenshot({path:path.join(root,'logs','graph-full-sex-relations.png'),fullPage:true})
 assert.ok(!originalRelations.some(r=>r.relationType==='SIBLING'&&[person.id,other.id].includes(r.fromMemberId)&&[person.id,other.id].includes(r.toMemberId)))
 await page.goto(base+'/relations');await choose('当前成员姓名',person.name);await choose('关联成员姓名',other.name);await page.getByText('兄弟姐妹',{exact:true}).first().click()
 const savedRelation=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/relationships'));await page.getByRole('button',{name:'确认保存',exact:true}).click();const added=await savedRelation;assert.equal(added.status(),200);newRelationId=(await added.json()).id
 await graph.waitForFunction(n=>document.querySelectorAll('.graph-link').length===n,originalRelations.length+1)
 const row=page.locator('.relation-list tbody tr').filter({hasText:person.name}).filter({hasText:other.name}).filter({hasText:'兄弟姐妹'});await row.getByRole('button',{name:'解除关系',exact:true}).click();await page.getByRole('dialog',{name:'解除关系',exact:true}).getByRole('button',{name:'确认解除',exact:true}).click()
 await graph.waitForFunction(n=>document.querySelectorAll('.graph-link').length===n,originalRelations.length)
 newRelationId=undefined;checks.push('all members/relationships rendered; a graph already open in another tab automatically adds/removes edited link')
 assert.deepEqual((await get(context.request,`/trees/${treeId}/members`)).map(m=>m.id).sort(),originalMembers.map(m=>m.id).sort())
 assert.deepEqual((await get(context.request,`/trees/${treeId}/relationships`)).map(r=>r.id).sort(),originalRelations.map(r=>r.id).sort())
 assert.deepEqual(errors,[]);console.log(JSON.stringify({result:'PASS',checks,pageErrors:errors},null,2))
}finally{
 if(newRelationId&&treeId)await api(context.request,`/trees/${treeId}/relationships/${newRelationId}`,'DELETE')
 if(accountId)await api(context.request,`/admin/users/${accountId}`,'DELETE')
 await browser.close()
}
