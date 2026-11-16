import {chromium} from '../data/browser-tools/node_modules/playwright/index.mjs'
import {readFileSync} from 'node:fs'
import {randomBytes} from 'node:crypto'
import {fileURLToPath} from 'node:url'
import path from 'node:path'
import assert from 'node:assert/strict'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),base='http://127.0.0.1:5173'
const password=readFileSync(path.join(root,'.env.local'),'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser=await chromium.launch({executablePath:'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',headless:true})
const adminContext=await browser.newContext({viewport:{width:1500,height:1100}}),admin=await adminContext.newPage()
const managerContext=await browser.newContext({viewport:{width:1500,height:1100}}),page=await managerContext.newPage()
for(const p of [admin,page])p.setDefaultTimeout(45000)
const username='verify_family_admin_'+Date.now(),secret=randomBytes(18).toString('hex'),checks=[],errors=[]
let accountId,treeId,fieldId,originalValue,createdField=false
for(const p of [admin,page])p.on('pageerror',e=>errors.push(e.message))
async function api(c,url,method='GET',data){const headers={};if(method!=='GET'){const csrf=await c.get(base+'/api/v1/auth/csrf').then(r=>r.json());headers[csrf.headerName]=csrf.token}return c.fetch(base+'/api/v1'+url,{method,headers,data})}
async function get(c,url){const r=await api(c,url);assert.ok(r.ok(),`${url}: ${r.status()}`);return r.json()}
async function login(p,user,pass){await p.goto(base+'/login');await p.getByPlaceholder('请输入用户名').fill(user);await p.getByPlaceholder('请输入密码').fill(pass);await p.getByRole('button',{name:'登 录'}).click();await p.waitForURL('**/dashboard');await p.locator('.stat-card strong').first().waitFor()}
try{
 await login(admin,'admin',password)
 const trees=await get(adminContext.request,'/trees'),tree=trees.find(t=>t.name==='示例家谱（演示数据）');assert.ok(tree);treeId=tree.id
 await admin.goto(base+'/users');await admin.getByRole('button',{name:'新增用户',exact:true}).click();const dialog=admin.getByRole('dialog',{name:'新增用户',exact:true})
 await dialog.locator('.el-form-item').filter({hasText:'用户名'}).locator('input').fill(username);await dialog.locator('input[type=password]').fill(secret)
 await dialog.getByText('普通管理员',{exact:true}).click()
 const combo=dialog.locator('.el-form-item').filter({hasText:'分配族谱'}).getByRole('combobox');await dialog.locator('.el-select__wrapper').click();await combo.fill(tree.name)
 await admin.locator(`[id="${await combo.getAttribute('aria-controls')}"]`).getByRole('option').filter({hasText:tree.name}).first().click();await dialog.locator('.el-dialog__header').click()
 const created=admin.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/admin/users'));await dialog.getByRole('button',{name:'创建用户',exact:true}).click();const response=await created;assert.equal(response.status(),200);accountId=(await response.json()).id
 await login(page,username,secret)
 const accessible=await get(managerContext.request,'/trees');assert.deepEqual(accessible.map(t=>t.id),[treeId])
 assert.equal((await api(managerContext.request,'/admin/users')).status(),403)
 assert.equal((await api(managerContext.request,`/trees/${treeId}`,'PUT',{name:tree.name,description:tree.description})).status(),403)
 const people=await get(managerContext.request,`/trees/${treeId}/members`),person=people.find(p=>p.name==='陈晨');assert.ok(person)
 const forbidden=trees.find(t=>t.id!==treeId);if(forbidden)assert.equal((await api(managerContext.request,`/trees/${forbidden.id}/members`)).status(),403)
 await page.goto(base+'/members');await page.getByRole('button',{name:'新增成员',exact:true}).waitFor();assert.equal(await page.getByRole('link',{name:/用户与权限/}).count(),0)
 await page.goto(base+`/members/${person.id}`);await page.getByRole('button',{name:'保存资料',exact:true}).waitFor();const saved=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith(`/members/${person.id}`));await page.getByRole('button',{name:'保存资料',exact:true}).click();assert.equal((await saved).status(),200)
 checks.push('super administrator creates FAMILY_ADMIN in UI; local administrator sees only assigned family, can maintain personnel, cannot manage users/metadata/other families')
 await page.goto(base+'/member-fields');await page.getByRole('heading',{name:/个人信息.*模版/}).first().waitFor()
 // Use a reusable, explicitly marked test-only disabled field; do not alter existing business definitions.
 const fieldKey='smoke_template_text',label='验证字段（测试停用保留）'
 let fields=await get(managerContext.request,`/trees/${treeId}/custom-fields`),field=fields.find(f=>f.field_key===fieldKey)
 if(!field){
  const r=await api(managerContext.request,`/trees/${treeId}/custom-fields`,'POST',{key:fieldKey,label,type:'TEXT',sensitive:false,enabled:true,sortOrder:999});assert.equal(r.status(),200);field=await r.json();createdField=true
 }
 fieldId=field.id
 assert.equal(field.label,label,'Refuse to modify a non-test field with the same key')
 const values=await get(managerContext.request,`/trees/${treeId}/custom-fields/members/${person.id}`);originalValue=values.find(f=>f.field_key===fieldKey)?.value_text||''
 assert.equal((await api(managerContext.request,`/trees/${treeId}/custom-fields/${fieldId}`,'PUT',{label,enabled:true,sortOrder:999})).status(),200)
 await page.reload();await page.getByText(label,{exact:true}).first().waitFor()
 await page.locator(`[data-test="edit-field-${fieldId}"]`).click()
 const fieldDialog=page.getByRole('dialog',{name:'编辑字段模版',exact:true})
 await fieldDialog.locator('[data-test="field-label"]').fill(label)
 const templateSaved=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith(`/custom-fields/${fieldId}`))
 await fieldDialog.getByRole('button',{name:'保存',exact:true}).click();assert.equal((await templateSaved).status(),200)
 await page.getByText('字段模版已更新',{exact:true}).waitFor()
 await page.screenshot({path:path.join(root,'logs','personal-template-config.png'),fullPage:true})
 await page.goto(base+`/members/${person.id}`);await page.getByRole('button',{name:'保存资料',exact:true}).waitFor();await page.getByRole('tab',{name:'族谱扩展资料',exact:true}).click()
 const fieldRow=page.locator('.field-row').filter({hasText:label});await fieldRow.locator('input').fill('普通管理员模版保存验证')
 const fieldSave=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith(`/custom-fields/${fieldId}/members/${person.id}`));await page.getByRole('button',{name:'保存资料',exact:true}).click();assert.equal((await fieldSave).status(),200)
 await page.getByText('成员资料已保存',{exact:true}).waitFor();await page.reload();await page.getByRole('tab',{name:'族谱扩展资料',exact:true}).click();await fieldRow.locator('input').waitFor();assert.equal(await fieldRow.locator('input').inputValue(),'普通管理员模版保存验证')
 await page.screenshot({path:path.join(root,'logs','family-admin-template-value.png'),fullPage:true})
 const restore=await api(managerContext.request,`/trees/${treeId}/custom-fields/${fieldId}/members/${person.id}`,'PUT',{value:originalValue});assert.equal(restore.status(),200)
 const inactive=await api(managerContext.request,`/trees/${treeId}/custom-fields/${fieldId}`,'PUT',{label,enabled:false,sortOrder:999});assert.equal(inactive.status(),200)
 checks.push('template can be enabled/configured, appears in member profile, local administrator saves/reloads value; original value restored and test-only field disabled, historical records retained')
 assert.deepEqual((await get(managerContext.request,`/trees/${treeId}/members`)).map(p=>p.id).sort(),people.map(p=>p.id).sort());assert.deepEqual(errors,[])
 console.log(JSON.stringify({result:'PASS',checks,pageErrors:errors,note:createdField?'One clearly marked test-only template field is retained disabled; no business members created/deleted':'Reused test-only template field; retained disabled'},null,2))
}finally{
 if(fieldId&&treeId)await api(adminContext.request,`/trees/${treeId}/custom-fields/${fieldId}`,'PUT',{label:'验证字段（测试停用保留）',enabled:false,sortOrder:999})
 if(accountId)await api(adminContext.request,`/admin/users/${accountId}`,'DELETE')
 await browser.close()
}
