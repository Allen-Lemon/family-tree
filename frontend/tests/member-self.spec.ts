import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import MemberDetailView from '@/views/MemberDetailView.vue'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { memberApi, customFieldApi } from '@/api'
vi.mock('vue-router',()=>({useRoute:()=>({params:{id:'42'}})}))
vi.mock('@/api',()=>({authApi:{},treeApi:{},memberApi:{get:vi.fn(),update:vi.fn()},customFieldApi:{values:vi.fn(),definitions:vi.fn(),setValue:vi.fn()},featureApi:{mediaUrl:vi.fn()}}))
const member={id:42,treeId:1,name:'本人甲',gender:'MALE' as const,avatarMediaId:9}
beforeEach(()=>{vi.restoreAllMocks();vi.resetAllMocks();vi.stubGlobal('localStorage',{getItem:()=>null,setItem:vi.fn(),removeItem:vi.fn()});setActivePinia(createPinia());const app=useAppStore();app.trees=[{id:1,name:'家谱',ownerId:1,version:0}];app.setTree(1);vi.mocked(memberApi.get).mockResolvedValue(member);vi.mocked(memberApi.update).mockResolvedValue(member);vi.mocked(customFieldApi.values).mockResolvedValue([{field_key:'test',label:'系统字段',field_type:'TEXT',sensitive:false,value_text:'值'}]);vi.mocked(customFieldApi.definitions).mockResolvedValue([{id:1,key:'test',label:'系统字段',type:'TEXT',sensitive:false}])})
async function render(self:boolean){const auth=useAuthStore();auth.user={id:2,username:'member',role:'MEMBER',memberId:self?42:43,memberTreeId:1};vi.spyOn(auth,'fetchMe').mockResolvedValue();const w=mount(MemberDetailView,{global:{plugins:[ElementPlus]}});await flushPromises();return w}
describe('self-only member editing',()=>{
 it('saves own basic information without media mutation or system custom field updates',async()=>{const w=await render(true);const button=w.findAll('button').find(b=>b.text()==='保存资料')!;expect(button).toBeDefined();await button.trigger('click');await flushPromises();expect(memberApi.update).toHaveBeenCalledWith(1,'42',expect.objectContaining({name:'本人甲'}));expect(vi.mocked(memberApi.update).mock.calls[0]![2]).not.toHaveProperty('avatarMediaId');expect(customFieldApi.setValue).not.toHaveBeenCalled();w.unmount()})
 it('shows other members read-only with no save button',async()=>{const w=await render(false);expect(w.findAll('button').some(b=>b.text()==='保存资料')).toBe(false);expect((w.find('input').element as HTMLInputElement).disabled).toBe(true);w.unmount()})
})
