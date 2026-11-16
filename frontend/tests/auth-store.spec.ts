import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

describe('auth store backend role mapping',()=>{
  beforeEach(()=>{vi.stubGlobal('localStorage',{getItem:()=>null,setItem:vi.fn(),removeItem:vi.fn()});setActivePinia(createPinia())})
  it('limits MEMBER to read permissions',()=>{const store=useAuthStore();store.user={id:1,username:'member',role:'MEMBER'};expect(store.hasPermission('tree:view')).toBe(true);expect(store.hasPermission('tree:edit')).toBe(false)})
  it('maps ADMIN to management permissions',()=>{const store=useAuthStore();store.user={id:1,username:'admin',role:'ADMIN'};expect(store.hasPermission('user:manage')).toBe(true);expect(store.hasPermission('relation:edit')).toBe(true)})
})
