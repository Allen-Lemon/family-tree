import { chromium } from '../data/browser-tools/node_modules/playwright/index.mjs'
import { readFileSync } from 'node:fs'
import { randomBytes } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import assert from 'node:assert/strict'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
const adminPassword=readFileSync(path.join(root,'.env.local'),'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser=await chromium.launch({executablePath:'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',headless:true})
const context=await browser.newContext({viewport:{width:1440,height:1100}}), page=await context.newPage()
const userContext=await browser.newContext({viewport:{width:1440,height:1100}}), userPage=await userContext.newPage()
page.setDefaultTimeout(45000);userPage.setDefaultTimeout(45000)
const base='http://127.0.0.1:5173', username='verify_scope_'+Date.now(), password=randomBytes(18).toString('hex'), checks=[], errors=[]
let createdId, deleted=false
page.on('pageerror',e=>errors.push(e.message));userPage.on('pageerror',e=>errors.push(e.message))
async function api(client,url,method='GET',data){
 let headers={};if(method!=='GET'){const c=await client.get(base+'/api/v1/auth/csrf').then(r=>r.json());headers[c.headerName]=c.token}
 return client.fetch(base+'/api/v1'+url,{method,headers,data})
}
async function login(p,name,pass){await p.goto(base+'/login');await p.getByPlaceholder('请输入用户名').fill(name);await p.getByPlaceholder('请输入密码').fill(pass);await p.getByRole('button',{name:'登 录'}).click();await p.waitForURL('**/dashboard');await p.locator('.stat-card strong').first().waitFor()}
try{
 await login(page,'admin',adminPassword)
 const allTrees=await api(context.request,'/trees').then(r=>r.json());assert.ok(allTrees.length)
 const tree=allTrees.find(t=>t.name==='示例家谱（演示数据）')||allTrees[0]
 await page.goto(base+'/users');await page.getByRole('button',{name:'新增用户',exact:true}).click()
 const create=page.getByRole('dialog',{name:'新增用户',exact:true})
 await create.locator('.el-form-item').filter({hasText:'用户名'}).locator('input').fill(username)
 await create.locator('input[type=password]').fill(password)
 const created=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/admin/users'))
 await create.getByRole('button',{name:'创建用户',exact:true}).click();const creation=await created;assert.equal(creation.status(),200);createdId=(await creation.json()).id
 let row=page.locator('.el-table__body-wrapper tr').filter({hasText:username})
 await row.getByText('尚未分配（无法进入族谱）',{exact:true}).waitFor()
 await row.getByRole('button',{name:'分配族谱',exact:true}).click()
 const assign=page.getByRole('dialog').filter({hasText:`分配族谱 · ${username}`})
 const combo=assign.getByRole('combobox');await assign.locator('.el-select__wrapper').click();await combo.fill(tree.name)
 const option=page.locator(`[id="${await combo.getAttribute('aria-controls')}"]`).getByRole('option').filter({hasText:tree.name}).first();await option.waitFor({state:'visible'});await option.click();await assign.locator('.el-dialog__header').click()
 const assignment=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith(`/users/${createdId}/trees`))
 await assign.getByRole('button',{name:'保存分配',exact:true}).click();assert.equal((await assignment).status(),200)
 await page.getByText('族谱分配已更新',{exact:true}).waitFor()
 await page.screenshot({path:path.join(root,'logs','users-assignment.png'),fullPage:true})
 checks.push('administrator creates a temporary account and assigns one existing family by name')
 await userPage.goto(base+'/login');await userPage.evaluate(()=>localStorage.setItem('activeTreeId','999999'))
 await login(userPage,username,password)
 const permitted=await api(userContext.request,'/trees').then(r=>r.json());assert.deepEqual(permitted.map(t=>t.id),[tree.id]);assert.equal(permitted[0].accessRole,'EDITOR')
 assert.equal(await userPage.evaluate(()=>localStorage.getItem('activeTreeId')),String(tree.id))
 await userPage.goto(base+'/trees');await userPage.getByRole('heading',{name:tree.name,exact:true}).waitFor();assert.equal(await userPage.locator('.resource-card').count(),1)
 assert.equal(await userPage.getByRole('link',{name:/用户与权限/}).count(),0)
 assert.equal((await api(userContext.request,'/admin/users')).status(),403)
 const deniedTree=allTrees.find(t=>t.id!==tree.id)
 if(deniedTree)assert.equal((await api(userContext.request,`/trees/${deniedTree.id}/members`)).status(),403)
 await userPage.goto(base+`/trees/${deniedTree?.id||999999}`);await userPage.waitForURL('**/trees?denied=1')
 await userPage.goto(base+'/members');await userPage.getByRole('heading',{name:'成员管理',exact:true}).waitFor();assert.equal(await userPage.getByRole('button',{name:'新增成员',exact:true}).isVisible(),false)
 await userPage.screenshot({path:path.join(root,'logs','member-scoped-tree.png'),fullPage:true})
 checks.push('ordinary account sees only assigned family, stale local tree replaced, system editing hidden, admin/cross-family navigation denied')
 // Revoke the authorization while the ordinary account keeps its current session.
 const revoked=await api(context.request,`/admin/users/${createdId}/trees`,'PUT',{treeIds:[]});assert.equal(revoked.status(),200)
 assert.deepEqual(await api(userContext.request,'/trees').then(r=>r.json()),[])
 assert.equal((await api(userContext.request,`/trees/${tree.id}/members`)).status(),403)
 checks.push('revoking family assignment blocks access in an already logged-in session')
 await page.reload();row=page.locator('.el-table__body-wrapper tr').filter({hasText:username});await row.getByRole('button',{name:'删除用户',exact:true}).click()
 let confirmation=page.getByRole('dialog',{name:'删除用户',exact:true});await confirmation.getByRole('button',{name:'取消',exact:true}).click()
 assert.ok((await api(context.request,'/admin/users').then(r=>r.json())).some(u=>u.id===createdId))
 await row.getByRole('button',{name:'删除用户',exact:true}).click();confirmation=page.getByRole('dialog',{name:'删除用户',exact:true});await page.screenshot({path:path.join(root,'logs','user-delete-confirm.png'),fullPage:true})
 const deletion=page.waitForResponse(r=>r.request().method()==='DELETE'&&r.url().endsWith(`/users/${createdId}`));await confirmation.getByRole('button',{name:'确认删除',exact:true}).click();assert.equal((await deletion).status(),204);deleted=true
 assert.equal((await api(userContext.request,'/auth/me')).status(),401)
 const csrf=await userContext.request.get(base+'/api/v1/auth/csrf').then(r=>r.json())
 const relogin=await userContext.request.post(base+'/api/v1/auth/login',{headers:{[csrf.headerName]:csrf.token},data:{username,password}});assert.equal(relogin.status(),401)
 assert.ok(!(await api(context.request,'/admin/users').then(r=>r.json())).some(u=>u.id===createdId))
 assert.deepEqual((await api(context.request,'/trees').then(r=>r.json())).map(t=>t.id).sort(),allTrees.map(t=>t.id).sort())
 checks.push('cancel preserves account; confirmed deletion removes it from list and invalidates existing session/new login; family records unchanged')
 assert.deepEqual(errors,[]);console.log(JSON.stringify({result:'PASS',checks,pageErrors:errors,note:'Temporary account logically deleted; no family/member records created or deleted'},null,2))
}finally{
 if(createdId&&!deleted){const r=await api(context.request,`/admin/users/${createdId}`,'DELETE');if(!r.ok()){console.error('Temporary account cleanup failed: '+r.status());process.exitCode=1}}
 await browser.close()
}
