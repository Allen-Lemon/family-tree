import { chromium } from '../data/browser-tools/node_modules/playwright/index.mjs'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
const password=readFileSync(path.join(root,'.env.local'),'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser=await chromium.launch({executablePath:'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',headless:true})
const page=await browser.newPage({viewport:{width:1440,height:1000}})
const base='http://127.0.0.1:5173/api/v1'
const names=new Set(['verify_scope_1794375951212','verify_scope_1794376045773','verify_scope_1794376132766'])
try{
 const csrf=()=>page.request.get(base+'/auth/csrf').then(r=>r.json())
 const token=await csrf();const login=await page.request.post(base+'/auth/login',{headers:{[token.headerName]:token.token},data:{username:'admin',password}});if(!login.ok())throw new Error('Admin login failed')
 const list=await page.request.get(base+'/admin/users').then(r=>r.json())
 for(const user of list.filter(u=>names.has(u.username))){const c=await csrf();const res=await page.request.delete(base+`/admin/users/${user.id}`,{headers:{[c.headerName]:c.token}});if(!res.ok())throw new Error(`Cleanup failed ${user.username}: ${res.status()}`);console.log('Deleted own temporary test account: '+user.username)}
 const remaining=await page.request.get(base+'/admin/users').then(r=>r.json());if(remaining.some(u=>names.has(u.username)))throw new Error('Test account still active')
 await page.goto('http://127.0.0.1:5173/users');await page.getByRole('heading',{name:'用户与权限',exact:true}).waitFor();await page.getByText('普通用户',{exact:true}).first().waitFor();await page.screenshot({path:path.join(root,'logs','users-final.png'),fullPage:true});console.log('PASS: own temporary accounts removed; existing accounts preserved')
}finally{await browser.close()}
