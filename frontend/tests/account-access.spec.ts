import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { authApi, treeApi } from '@/api'
vi.mock('@/api',()=>({authApi:{login:vi.fn(),me:vi.fn(),logout:vi.fn()},treeApi:{list:vi.fn()}}))
vi.mock('@/api/client',()=>({clearCsrf:vi.fn()}))
const first={id:1,name:'甲族谱',ownerId:10,version:0,accessRole:'EDITOR' as const}
const second={id:2,name:'乙族谱',ownerId:20,version:0,accessRole:'VIEWER' as const}
beforeEach(()=>{const data=new Map<string,string>();vi.stubGlobal('localStorage',{getItem:(k:string)=>data.get(k)||null,setItem:(k:string,v:string)=>data.set(k,v),removeItem:(k:string)=>data.delete(k)});setActivePinia(createPinia());vi.resetAllMocks()})
describe('account tree scope',()=>{
 it('clears previous account selection and selects only a permitted tree on login',async()=>{
  const app=useAppStore(),auth=useAuthStore();app.setTree(999)
  vi.mocked(authApi.login).mockResolvedValue({id:5,username:'member',role:'MEMBER'})
  vi.mocked(treeApi.list).mockResolvedValue([first])
  await auth.login({username:'member',password:'unused'})
  expect(app.activeTreeId).toBe('1');expect(app.trees).toEqual([first]);expect(auth.hasPermission('relation:edit')).toBe(false);expect(auth.hasPermission('user:manage')).toBe(false)
  app.setTree(999);expect(app.activeTreeId).toBe('1')
 })
 it('does not grant system editing to either EDITOR or VIEWER',async()=>{
  const auth=useAuthStore(),app=useAppStore();auth.user={id:5,username:'viewer',role:'MEMBER'}
  vi.mocked(treeApi.list).mockResolvedValue([first,second]);await app.loadTrees();expect(auth.hasPermission('member:edit')).toBe(false)
  app.setTree(2);expect(auth.hasPermission('member:edit')).toBe(false);expect(auth.hasPermission('tree:view')).toBe(true)
 })
 it('only permits editing the explicitly bound self within an authorized tree',async()=>{
  const auth=useAuthStore(),app=useAppStore();auth.user={id:5,username:'self',role:'MEMBER',memberId:42,memberTreeId:1}
  vi.mocked(treeApi.list).mockResolvedValue([first]);await app.loadTrees()
  expect(auth.canEditMember(1,42)).toBe(true);expect(auth.canEditMember(1,43)).toBe(false);expect(auth.canEditMember(2,42)).toBe(false)
  app.trees=[];expect(auth.canEditMember(1,42)).toBe(false)
 })
 it('limits family administrators to assigned-tree personnel and templates, not system or relationship administration',async()=>{
  const app=useAppStore(),auth=useAuthStore();auth.user={id:7,username:'family-admin',role:'FAMILY_ADMIN'}
  vi.mocked(treeApi.list).mockResolvedValue([first]);await app.loadTrees()
  expect(auth.hasPermission('member:edit')).toBe(true);expect(auth.hasPermission('template:edit')).toBe(true)
  expect(auth.canEditMember(1,999)).toBe(true);expect(auth.canEditMember(2,999)).toBe(false)
  for(const permission of ['user:manage','relation:edit','tree:edit','data:manage'] as const)expect(auth.hasPermission(permission)).toBe(false)
 })
 it('clears local trees on logout and discards an old pending tree response',async()=>{
  const app=useAppStore(),auth=useAuthStore();let resolve!:(trees:typeof first[])=>void
  vi.mocked(treeApi.list).mockReturnValue(new Promise(done=>{resolve=done}))
  const pending=app.loadTrees();await auth.logout();resolve([first]);await pending
  expect(app.trees).toEqual([]);expect(app.activeTreeId).toBe('');expect(auth.user).toBeNull()
 })
 it('handles no assigned trees without falling back to another account tree',async()=>{
  const app=useAppStore(),auth=useAuthStore();app.setTree(123)
  vi.mocked(authApi.login).mockResolvedValue({id:6,username:'unassigned',role:'MEMBER'});vi.mocked(treeApi.list).mockResolvedValue([])
  await auth.login({username:'unassigned',password:'unused'});expect(app.activeTreeId).toBe('');expect(app.trees).toEqual([]);expect(auth.hasPermission('member:edit')).toBe(false)
 })
})
