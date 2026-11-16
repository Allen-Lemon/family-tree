import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import assert from 'node:assert/strict'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const env = Object.fromEntries(readFileSync(path.join(root, '.env.local'), 'utf8').split(/\r?\n/).filter(l=>l.trim()&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return [l.slice(0,i),l.slice(i+1)]}))
const base = `http://127.0.0.1:${env.SERVER_PORT}/api/v1`
const cookies = new Map()
async function call(endpoint, method='GET', data) {
  const headers = {Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join('; ')}
  if(method!=='GET') {
    const csrf=await call('/auth/csrf')
    headers.Cookie=[...cookies].map(([k,v])=>`${k}=${v}`).join('; ')
    headers[csrf.headerName]=csrf.token
  }
  if(data!==undefined)headers['Content-Type']='application/json'
  const res=await fetch(base+endpoint,{method,headers,body:data===undefined?undefined:JSON.stringify(data),signal:AbortSignal.timeout(60000)})
  for(const c of res.headers.getSetCookie()){const pair=c.split(';')[0],i=pair.indexOf('=');cookies.set(pair.slice(0,i),pair.slice(i+1))}
  const text=await res.text()
  if(!res.ok)throw new Error(`${method} ${endpoint}: ${res.status} ${text}`)
  return text?JSON.parse(text):null
}
const me=await call('/auth/login','POST',{username:env.BOOTSTRAP_ADMIN_USERNAME,password:env.BOOTSTRAP_ADMIN_PASSWORD})
assert.equal(me.role,'ADMIN')
console.log('PASS: administrator login and session/CSRF')
const treeName='示例家谱（演示数据）'
let tree=(await call('/trees')).find(t=>t.name===treeName)
if(!tree)tree=await call('/trees','POST',{name:treeName,description:'系统初始化演示家谱，所有人物及事件均为虚构，可用于验证成员、关系、检索、故事和备份功能。'})
const p=`/trees/${tree.id}`
const definitions=[
  {name:'陈文山',gender:'MALE',birthDate:'1940-03-12',occupation:'教师'},
  {name:'李淑兰',gender:'FEMALE',birthDate:'1942-08-20',occupation:'医生'},
  {name:'陈建国',gender:'MALE',birthDate:'1968-05-06',occupation:'工程师'},
  {name:'王秀英',gender:'FEMALE',birthDate:'1970-09-15',occupation:'会计'},
  {name:'陈晨',gender:'MALE',birthDate:'1995-04-18',occupation:'设计师'},
  {name:'陈悦',gender:'FEMALE',birthDate:'1998-11-22',occupation:'教师'},
]
const members=await call(p+'/members')
for(const input of definitions){if(!members.some(m=>m.name===input.name))members.push(await call(p+'/members','POST',{...input,birthPrecision:'DAY',birthPlace:'示例城市',notes:'虚构演示人物，非真实个人信息。'}))}
const member=name=>members.find(m=>m.name===name)
const relations=await call(p+'/relationships')
for(const [from,to,type] of [['陈文山','李淑兰','SPOUSE'],['陈文山','陈建国','PARENT'],['李淑兰','陈建国','PARENT'],['陈建国','王秀英','SPOUSE'],['陈建国','陈晨','PARENT'],['王秀英','陈晨','PARENT'],['陈建国','陈悦','PARENT'],['王秀英','陈悦','PARENT']]){
  const fromMemberId=member(from).id,toMemberId=member(to).id
  if(!relations.some(r=>r.fromMemberId===fromMemberId&&r.toMemberId===toMemberId&&r.relationType===type))relations.push(await call(p+'/relationships','POST',{fromMemberId,toMemberId,type}))
}
if(!(await call(p+'/stories')).some(s=>s.title==='家风传承（演示）'))await call(p+'/stories','POST',{title:'家风传承（演示）',content:'这是一则虚构的初始化故事：勤学、诚信、互助，将家族记忆整理并传递给下一代。',memberId:member('陈文山').id})
if(!(await call(p+'/events')).some(e=>e.title==='家谱整理启动（演示）'))await call(p+'/events','POST',{title:'家谱整理启动（演示）',eventType:'OTHER',eventDate:'2026-11-11',datePrecision:'DAY',place:'示例城市',description:'初始化演示事件，不包含真实家族资料。'})
let fields=await call(p+'/custom-fields')
if(!fields.some(f=>f.field_key==='generation_name')){await call(p+'/custom-fields','POST',{key:'generation_name',label:'字辈',type:'TEXT',sensitive:false});fields=await call(p+'/custom-fields')}
const field=fields.find(f=>f.field_key==='generation_name')
const values=await call(`${p}/custom-fields/members/${member('陈晨').id}`)
if(!values.find(f=>f.field_key==='generation_name')?.value_text)await call(`${p}/custom-fields/${field.id}/members/${member('陈晨').id}`,'PUT',{value:'晨（演示）'})
const stats=await call(p+'/statistics')
assert.ok(stats.members>=6);assert.ok(stats.relationships>=8)
assert.ok((await call(`${p}/members/${member('陈晨').id}/ancestors`)).some(m=>m.name==='陈文山'))
assert.ok((await call(`${p}/search?q=${encodeURIComponent('陈晨')}`)).count>=1)
const exported=await call(p+'/exports/sync?format=JSON')
assert.ok(exported)
if(!(await call(p+'/backups')).length)await call(p+'/backups','POST')
console.log(JSON.stringify({result:'PASS',treeId:tree.id,members:stats.members,relationships:stats.relationships,checks:['members','relationships','ancestors','search','custom fields','stories','events','JSON export','backup']},null,2))
