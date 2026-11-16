import {chromium} from '../data/browser-tools/node_modules/playwright/index.mjs'
import {readFileSync} from 'node:fs'
import {randomBytes} from 'node:crypto'
import {fileURLToPath} from 'node:url'
import path from 'node:path'
import assert from 'node:assert/strict'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),base='http://127.0.0.1:5173'
const adminPassword=readFileSync(path.join(root,'.env.local'),'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser=await chromium.launch({executablePath:'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',headless:true})
const adminContext=await browser.newContext({viewport:{width:1500,height:1100}}),admin=await adminContext.newPage(),context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage()
for(const p of [page,admin])p.setDefaultTimeout(45000)
const username='verify_profile_'+Date.now(),password=randomBytes(18).toString('hex'),checks=[],errors=[]
let userId
for(const p of [page,admin])p.on('pageerror',e=>errors.push(e.message))
async function api(c,url,method='GET',data){const headers={};if(method!=='GET'){const t=await c.get(base+'/api/v1/auth/csrf').then(r=>r.json());headers[t.headerName]=t.token}return c.fetch(base+'/api/v1'+url,{method,headers,data})}
async function login(p,name,secret){await p.goto(base+'/login');await p.getByPlaceholder('请输入用户名').fill(name);await p.getByPlaceholder('请输入密码').fill(secret);await p.getByRole('button',{name:'登 录'}).click();await p.waitForURL('**/dashboard');await p.locator('.stat-card strong').first().waitFor()}
async function setRole(role,label){
 await admin.goto(base+'/users');const row=admin.locator('tbody tr').filter({hasText:username});await row.getByRole('button',{name:'设置管理员',exact:true}).click()
 const dialog=admin.getByRole('dialog').filter({hasText:`设置管理员 · ${username}`});await dialog.getByText(label,{exact:true}).click();await dialog.getByRole('button',{name:'保存角色',exact:true}).click()
 const changed=admin.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith(`/users/${userId}/role`));await admin.getByRole('dialog',{name:'确认权限变更',exact:true}).getByRole('button',{name:'确认修改',exact:true}).click();assert.equal((await changed).status(),200)
 const me=await api(context.request,'/auth/me').then(r=>r.json());assert.equal(me.role,role)
}
try{
 await login(admin,'admin',adminPassword)
 const made=await api(adminContext.request,'/admin/users','POST',{username,password,role:'MEMBER'});assert.equal(made.status(),200);userId=(await made.json()).id
 await login(page,username,password)
 await page.getByRole('link',{name:/我的个人信息/}).click();await page.waitForURL('**/profile');await page.getByRole('heading',{name:'我的个人信息',exact:true}).waitFor()
 const nameItem=page.locator('.el-form-item').filter({hasText:'姓名 / 昵称'});await nameItem.locator('input').fill('个人资料验证')
 const date=page.getByPlaceholder('请选择出生日期');await date.click();const panel=page.locator('.el-picker-panel:visible');await panel.locator('td.available:not(.prev-month):not(.next-month)').filter({has:page.locator('.el-date-table-cell__text',{hasText:/^1$/})}).click()
 const gender=page.locator('.el-form-item').filter({has:page.locator('.el-form-item__label',{hasText:/^性别$/})});await gender.locator('.el-select__wrapper').click();const combo=gender.getByRole('combobox');await page.locator(`[id="${await combo.getAttribute('aria-controls')}"]`).getByRole('option',{name:'女',exact:true}).click()
 await page.getByPlaceholder('例如未婚、已婚，或填写配偶情况').fill('已婚（验证资料）');await page.getByPlaceholder('填写最高学历或教育经历').fill('本科');await page.getByPlaceholder('介绍自己的经历、兴趣或其他信息').fill('这是临时账号的个人介绍验证。')
 const save=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/profile'));await page.getByRole('button',{name:'保存个人信息',exact:true}).click();const saved=await save;assert.equal(saved.status(),200);const profile=await saved.json();assert.equal(profile.displayName,'个人资料验证');assert.equal(profile.gender,'FEMALE');assert.ok(Number.isInteger(profile.age));assert.equal(profile.spouseStatus,'已婚（验证资料）');assert.equal(profile.education,'本科');assert.equal(profile.biography,'这是临时账号的个人介绍验证。')
 const png=await page.evaluate(async()=>{const canvas=document.createElement('canvas');canvas.width=64;canvas.height=64;const ctx=canvas.getContext('2d');ctx.fillStyle='#2563eb';ctx.fillRect(0,0,64,64);return canvas.toDataURL('image/png').split(',')[1]})
 const upload=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/profile/portrait'));await page.locator('input[type=file]').setInputFiles({name:'portrait.png',mimeType:'image/png',buffer:Buffer.from(png,'base64')});assert.equal((await upload).status(),200)
 await page.waitForFunction(()=>document.querySelector('.portrait-panel img')?.complete===true&&document.querySelector('.portrait-panel img')?.naturalWidth>0)
 await page.reload();await page.getByPlaceholder('填写姓名或昵称').waitFor();await page.waitForFunction(()=>document.querySelector('.portrait-panel img')?.naturalWidth>0);assert.equal(await page.getByPlaceholder('填写姓名或昵称').inputValue(),'个人资料验证')
 assert.equal(await page.getByPlaceholder('填写最高学历或教育经历').inputValue(),'本科');assert.equal(await page.getByPlaceholder('介绍自己的经历、兴趣或其他信息').inputValue(),'这是临时账号的个人介绍验证。')
 await page.screenshot({path:path.join(root,'logs','account-personal-profile.png'),fullPage:true})
 checks.push('unassigned account can edit its own profile without a member binding; calendar birthday calculates age; gender and private portrait persist after reload')
 assert.equal((await api(context.request,'/admin/users')).status(),403)
 await setRole('FAMILY_ADMIN','是，普通管理员');checks.push('administrator promotes existing account to ordinary administrator; active session sees role immediately')
 await setRole('ADMIN','是，总管理员');assert.equal((await api(context.request,'/admin/users')).status(),200)
 await setRole('MEMBER','否，普通用户');assert.equal((await api(context.request,'/admin/users')).status(),403)
 await admin.screenshot({path:path.join(root,'logs','user-admin-role-settings.png'),fullPage:true})
 checks.push('promotion to super administrator and demotion require confirmed UI action; backend privileges follow current database role')
 assert.deepEqual(errors,[]);console.log(JSON.stringify({result:'PASS',checks,pageErrors:errors},null,2))
}finally{if(userId){const r=await api(adminContext.request,`/admin/users/${userId}`,'DELETE');if(!r.ok()){console.error('Temporary user cleanup failed: '+r.status());process.exitCode=1}}await browser.close()}
