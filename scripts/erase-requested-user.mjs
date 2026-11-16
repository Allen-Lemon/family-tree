import {chromium} from '../data/browser-tools/node_modules/playwright/index.mjs'
import {readFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import path from 'node:path'
import assert from 'node:assert/strict'
// Explicit one-off user-requested target, verified read-only before invocation.
const targetId=5
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),base='http://127.0.0.1:5173/api/v1'
const password=readFileSync(path.join(root,'.env.local'),'utf8').match(/^BOOTSTRAP_ADMIN_PASSWORD=(.+)$/m)[1].trim()
const browser=await chromium.launch({executablePath:'C:\\Users\\Administrator\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe',headless:true})
try{
 const context=await browser.newContext(),q=context.request
 const csrf=()=>q.get(base+'/auth/csrf').then(r=>r.json())
 const token=await csrf();assert.equal((await q.post(base+'/auth/login',{headers:{[token.headerName]:token.token},data:{username:'admin',password}})).status(),200)
 const before=await q.get(base+'/trees').then(r=>r.json())
 const snapshots=[]
 for(const tree of before){const members=await q.get(base+`/trees/${tree.id}/members`).then(r=>r.json()),relations=await q.get(base+`/trees/${tree.id}/relationships`).then(r=>r.json());snapshots.push({treeId:tree.id,members:members.map(m=>m.id).sort(),relations:relations.map(r=>r.id).sort()})}
 const c=await csrf(),result=await q.delete(base+`/admin/users/${targetId}`,{headers:{[c.headerName]:c.token}})
 if(!result.ok())throw Error(`Erase failed ${result.status()}: ${await result.text()}`)
 for(const snapshot of snapshots){assert.deepEqual((await q.get(base+`/trees/${snapshot.treeId}/members`).then(r=>r.json())).map(m=>m.id).sort(),snapshot.members);assert.deepEqual((await q.get(base+`/trees/${snapshot.treeId}/relationships`).then(r=>r.json())).map(r=>r.id).sort(),snapshot.relations)}
 console.log(JSON.stringify({result:'PASS',requestedUserId:targetId,status:result.status(),checks:['private account data erasure requested through protected endpoint','all original family member and relationship ID sets unchanged']},null,2))
}finally{await browser.close()}
