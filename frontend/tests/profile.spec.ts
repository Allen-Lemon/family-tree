import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest'
import {mount,flushPromises,type VueWrapper} from '@vue/test-utils'
import ElementPlus,{ElSelect,ElUpload} from 'element-plus'
import ProfileView from '@/views/ProfileView.vue'
import DatePicker from '@/components/DatePicker.vue'
import {profileApi} from '@/api'
vi.mock('@/api',()=>({profileApi:{get:vi.fn(),update:vi.fn(),uploadPortrait:vi.fn()}}))
vi.mock('@/api/client',()=>({http:{get:vi.fn()}}))
const profile={username:'person',displayName:'登录本人',birthDate:'2000-01-01',gender:'FEMALE' as const,age:26,hasPortrait:false}
const wrappers:VueWrapper[]=[]
beforeEach(()=>{vi.resetAllMocks();vi.mocked(profileApi.get).mockResolvedValue(profile);vi.mocked(profileApi.update).mockResolvedValue(profile)})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
async function render(){const w=mount(ProfileView,{global:{plugins:[ElementPlus]}});wrappers.push(w);await flushPromises();return w}
describe('current account profile',()=>{
 it('loads own account information without a family or member binding and saves only allowed fields',async()=>{const w=await render();expect(w.text()).toContain('我的个人信息');expect(w.text()).toContain('年龄');expect(w.getComponent(DatePicker).props('modelValue')).toBe('2000-01-01');expect(w.getComponent(ElSelect).props('modelValue')).toBe('FEMALE');await w.findAll('button').find(b=>b.text()==='保存个人信息')!.trigger('click');await flushPromises();expect(profileApi.update).toHaveBeenCalledWith({displayName:'登录本人',birthDate:'2000-01-01',gender:'FEMALE',spouseStatus:'',education:'',biography:''})})
 it('loads and saves spouse situation, education and introduction including explicit clearing',async()=>{
  vi.mocked(profileApi.get).mockResolvedValue({...profile,spouseStatus:'已婚',education:'本科',biography:'个人经历'})
  const w=await render();expect((w.get('[placeholder="例如未婚、已婚，或填写配偶情况"]').element as HTMLInputElement).value).toBe('已婚')
  await w.get('[placeholder="填写最高学历或教育经历"]').setValue('硕士');await w.get('textarea').setValue('')
  await w.findAll('button').find(b=>b.text()==='保存个人信息')!.trigger('click');await flushPromises()
  expect(profileApi.update).toHaveBeenCalledWith(expect.objectContaining({spouseStatus:'已婚',education:'硕士',biography:''}))
 })
 it('does not accept an SVG or oversized portrait through the upload callback',async()=>{const w=await render();const upload=w.getComponent(ElUpload).props('onChange')!;upload({raw:new File(['<svg/>'],'x.svg',{type:'image/svg+xml'})} as any,[]);upload({raw:new File([new Uint8Array(5*1024*1024+1)],'x.png',{type:'image/png'})} as any,[]);await flushPromises();expect(profileApi.uploadPortrait).not.toHaveBeenCalled()})
 it('keeps a missing birth date empty rather than displaying zero as age',async()=>{vi.mocked(profileApi.get).mockResolvedValue({...profile,birthDate:null,age:null});const w=await render();expect(w.findAll('input').some(input=>(input.element as HTMLInputElement).value==='未填写出生日期')).toBe(true)})
})
