import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessageBox, ElRadioGroup } from 'element-plus'
import UsersView from '@/views/UsersView.vue'
import { useAuthStore } from '@/stores/auth'
import { adminApi, treeApi } from '@/api'
vi.mock('@/api',()=>({authApi:{},treeApi:{list:vi.fn()},adminApi:{users:vi.fn(),removeUser:vi.fn(),assignTrees:vi.fn(),setEnabled:vi.fn(),createUser:vi.fn(),changeRole:vi.fn()}}))
const admin={id:1,username:'admin',role:'ADMIN' as const,enabled:true}
const member={id:2,username:'member',role:'MEMBER' as const,enabled:true,assignedTreeIds:[7]}
beforeEach(()=>{vi.restoreAllMocks();vi.resetAllMocks();vi.stubGlobal('localStorage',{getItem:()=>null,removeItem:vi.fn(),setItem:vi.fn()});setActivePinia(createPinia());useAuthStore().user=admin;vi.mocked(adminApi.users).mockResolvedValue([admin,member]);vi.mocked(treeApi.list).mockResolvedValue([{id:7,name:'分配的族谱',ownerId:1,version:0}])})
async function render(){const w=mount(UsersView,{global:{plugins:[ElementPlus]}});await flushPromises();return w}
describe('user management',()=>{
 it('offers administrator role selection for existing users and requires confirmation',async()=>{
  vi.spyOn(ElMessageBox,'confirm').mockImplementation(()=>Promise.resolve('confirm') as ReturnType<typeof ElMessageBox.confirm>)
  const w=await render();const rows=w.findAll('tbody tr');expect(rows[0]!.findAll('button').find(b=>b.text()==='设置管理员')!.attributes('disabled')).toBeDefined()
  await rows[1]!.findAll('button').find(b=>b.text()==='设置管理员')!.trigger('click');await flushPromises()
  w.findAllComponents(ElRadioGroup).at(-1)!.vm.$emit('update:modelValue','FAMILY_ADMIN');await flushPromises()
  await w.findAll('button').find(b=>b.text()==='保存角色')!.trigger('click');await flushPromises()
  expect(adminApi.changeRole).toHaveBeenCalledWith(2,'FAMILY_ADMIN');w.unmount()
 })
 it('shows assigned family names and protects the current administrator',async()=>{const w=await render();expect(w.text()).toContain('分配的族谱');const rows=w.findAll('tbody tr');expect(rows[0]!.findAll('button').find(b=>b.text()==='删除用户')!.attributes('disabled')).toBeDefined();w.unmount()})
 it('cancel keeps the user; confirmed deletion calls the account endpoint only',async()=>{const confirm=vi.spyOn(ElMessageBox,'confirm').mockRejectedValue('cancel');const w=await render();const remove=w.findAll('tbody tr')[1]!.findAll('button').find(b=>b.text()==='删除用户')!;await remove.trigger('click');await flushPromises();expect(adminApi.removeUser).not.toHaveBeenCalled();confirm.mockImplementation(()=>Promise.resolve('confirm') as ReturnType<typeof ElMessageBox.confirm>);vi.mocked(adminApi.users).mockResolvedValue([admin]);await remove.trigger('click');await flushPromises();expect(adminApi.removeUser).toHaveBeenCalledWith(2);expect(adminApi.users).toHaveBeenCalledTimes(2);w.unmount()})
})
