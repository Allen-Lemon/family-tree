import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessage, ElMessageBox, ElRadioGroup, ElSelect } from 'element-plus'
import RelationsView from '@/views/RelationsView.vue'
import MemberSelect from '@/components/MemberSelect.vue'
import { memberApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { Member, Relationship, RelationType } from '@/types'

const creationCalls = vi.hoisted(() => ({ create: vi.fn(), addChild: vi.fn(), addSibling: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
// Sentinels ensure the relationship page never calls a member-creation API, including the removed helpers.
vi.mock('@/api', () => ({ memberApi: { list: vi.fn(), relationships: vi.fn(), removeRelation: vi.fn(), addRelation: vi.fn(), ...creationCalls } }))
const people: Member[] = [
  { id: 1, treeId: 1, name: '测试甲', gender: 'MALE', birthDate: '1980-01-01' },
  { id: 2, treeId: 1, name: '测试乙', gender: 'FEMALE', birthDate: '1982-02-02' },
]
const relation: Relationship = { id: 7, treeId: 1, fromMemberId: 1, toMemberId: 2, relationType: 'SPOUSE', version: 0 }
function link(id: number, fromMemberId: number, toMemberId: number, relationType: RelationType): Relationship {
  return { id, treeId: 1, fromMemberId, toMemberId, relationType, version: 0 }
}
function person(id: number, name: string): Member { return { id, treeId: 1, name, gender: 'UNKNOWN' } }
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}
async function render() {
  const wrapper = mount(RelationsView, { global: { plugins: [ElementPlus], directives: { permission: {} } } })
  await flushPromises()
  return wrapper
}
type View = Awaited<ReturnType<typeof render>>
async function select(wrapper: View, current: number, target: number) {
  const selectors = wrapper.findAllComponents(MemberSelect)
  selectors[0]!.vm.$emit('update:modelValue', current)
  selectors[1]!.vm.$emit('update:modelValue', target)
  await flushPromises()
}
async function setType(wrapper: View, type: RelationType) {
  wrapper.findComponent(ElRadioGroup).vm.$emit('update:modelValue', type)
  await flushPromises()
}
function saveButton(wrapper: View) { return wrapper.findAll('button').find(b => b.text() === '确认保存')! }
function removeButton(wrapper: View) { return wrapper.findAll('button').find(b => b.text() === '解除关系')! }

beforeEach(() => {
  vi.restoreAllMocks()
  vi.resetAllMocks()
  const storage = new Map<string, string>()
  vi.stubGlobal('localStorage', { getItem: (key: string) => storage.get(key) ?? null, setItem: (key: string, value: string) => storage.set(key, value), clear: () => storage.clear() })
  setActivePinia(createPinia())
  useAppStore().setTree(1)
  vi.mocked(memberApi.list).mockResolvedValue(people)
  vi.mocked(memberApi.relationships).mockResolvedValue([relation])
  vi.mocked(memberApi.addRelation).mockResolvedValue(relation)
  vi.mocked(memberApi.removeRelation).mockResolvedValue(undefined)
})

describe('existing-member relationships', () => {
  it('only offers existing member selectors and three relationship types, without new-member fields', async () => {
    const wrapper = await render()
    const selectors = wrapper.findAllComponents(MemberSelect)
    expect(selectors).toHaveLength(2)
    expect(selectors[0]!.props('members')).toEqual(people)
    for (const selector of selectors) {
      expect(selector.findComponent(ElSelect).props('allowCreate')).toBe(false)
    }
    expect(wrapper.findAllComponents({ name: 'ElRadioButton' })).toHaveLength(3)
    expect(wrapper.text()).toContain('父母 → 子女')
    expect(wrapper.text()).toContain('配偶')
    expect(wrapper.text()).toContain('兄弟姐妹')
    for (const text of ['添加孩子', '添加兄弟姐妹', '填写新成员', '性别', '出生日期']) expect(wrapper.text()).not.toContain(text)
    expect(wrapper.findComponent({ name: 'ElSegmented' }).exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'DatePicker' }).exists()).toBe(false)
    expect(wrapper.text()).toContain('保存不会新增成员')
    await select(wrapper, 1, 2)
    expect(selectors[1]!.props('members')).toEqual([people[1]])
    wrapper.unmount()
  })

  it('rejects missing, unknown, or identical target members', async () => {
    const warning = vi.spyOn(ElMessage, 'warning')
    const wrapper = await render()
    await saveButton(wrapper).trigger('click')
    await select(wrapper, 1, 999)
    await saveButton(wrapper).trigger('click')
    await select(wrapper, 1, 1)
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(warning).toHaveBeenCalledTimes(3)
    expect(memberApi.addRelation).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it.each(['PARENT', 'SPOUSE', 'SIBLING'] as const)('saves %s through addRelation only, retains current member, clears target and reloads', async type => {
    const success = vi.spyOn(ElMessage, 'success')
    const wrapper = await render()
    await select(wrapper, 1, 2)
    await setType(wrapper, type)
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    expect(memberApi.addRelation).toHaveBeenCalledExactlyOnceWith(1, { fromMemberId: 1, toMemberId: 2, type })
    expect(creationCalls.create).not.toHaveBeenCalled()
    expect(creationCalls.addChild).not.toHaveBeenCalled()
    expect(creationCalls.addSibling).not.toHaveBeenCalled()
    expect(memberApi.list).toHaveBeenCalledTimes(2)
    expect(memberApi.relationships).toHaveBeenCalledTimes(2)
    const selectors = wrapper.findAllComponents(MemberSelect)
    expect(selectors[0]!.props('modelValue')).toBe(1)
    expect(selectors[1]!.props('modelValue')).toBe('')
    expect(success).toHaveBeenCalledWith('关系已保存并同步')
    wrapper.unmount()
  })

  it('prevents repeated saves while a request is pending', async () => {
    const pending = deferred<Relationship>()
    vi.mocked(memberApi.addRelation).mockReturnValue(pending.promise)
    const wrapper = await render()
    await select(wrapper, 1, 2)
    // Two clicks in the same tick also exercise the handler guard before disabled is rendered.
    saveButton(wrapper).element.click()
    saveButton(wrapper).element.click()
    await flushPromises()
    expect(memberApi.addRelation).toHaveBeenCalledTimes(1)
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
    pending.resolve(relation)
    await flushPromises()
    expect(saveButton(wrapper).attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('previews only the current parent’s direct spouses, with parent-to-child direction and no duplicates', async () => {
    vi.mocked(memberApi.list).mockResolvedValue([...people, person(3, '直接配偶'), person(4, '间接配偶'), person(5, '另一配偶')])
    vi.mocked(memberApi.relationships).mockResolvedValue([
      link(1, 3, 1, 'SPOUSE'), link(2, 1, 3, 'SPOUSE'), link(3, 3, 4, 'SPOUSE'),
      link(4, 1, 5, 'SPOUSE'), link(5, 5, 2, 'PARENT'),
    ])
    const wrapper = await render()
    await select(wrapper, 1, 2)
    const preview = wrapper.find('.sync-preview')
    expect(wrapper.text()).toContain('当前成员（父母） → 关联成员（子女）')
    expect(wrapper.text()).toContain('已有直接配偶')
    expect(preview.text()).toContain('测试甲（1980-01-01）（父母） → 测试乙（1982-02-02）（子女）')
    expect(preview.findAll('li').map(item => item.text())).toEqual(['直接配偶（父母） → 测试乙（1982-02-02）（子女）'])
    expect(preview.text()).not.toContain('间接配偶')
    expect(preview.text()).not.toContain('另一配偶')
    expect(preview.text()).toContain('不递归扩展')
    wrapper.unmount()
  })

  it('previews both spouses’ existing children without grandchildren or spouses of either side', async () => {
    vi.mocked(memberApi.list).mockResolvedValue([...people, person(3, '甲的孩子'), person(4, '乙的孩子'), person(5, '孙辈'), person(6, '其他配偶'), person(7, '共同孩子')])
    vi.mocked(memberApi.relationships).mockResolvedValue([
      link(1, 1, 3, 'PARENT'), link(2, 2, 4, 'PARENT'), link(3, 3, 5, 'PARENT'),
      link(4, 1, 6, 'SPOUSE'), link(5, 6, 5, 'PARENT'), link(6, 1, 7, 'PARENT'), link(7, 2, 7, 'PARENT'),
    ])
    const wrapper = await render()
    await select(wrapper, 1, 2)
    await setType(wrapper, 'SPOUSE')
    const preview = wrapper.find('.sync-preview')
    expect(wrapper.text()).toContain('双方已有子女会互相关联为对方的子女')
    expect(preview.findAll('li').map(item => item.text())).toEqual([
      '测试乙（1982-02-02）（父母） → 甲的孩子（子女）',
      '测试甲（1980-01-01）（父母） → 乙的孩子（子女）',
    ])
    for (const name of ['孙辈', '其他配偶', '共同孩子']) expect(preview.text()).not.toContain(name)
    await setType(wrapper, 'SIBLING')
    expect(wrapper.find('.sync-preview').findAll('li')).toHaveLength(0)
    expect(wrapper.find('.sync-preview').text()).toContain('暂无需要自动补充的亲子关系')
    wrapper.unmount()
  })
})

describe('tree switching', () => {
  it('ignores late member and relationship responses from an earlier tree', async () => {
    const pendingPeople = deferred<Member[]>(), pendingLinks = deferred<Relationship[]>()
    vi.mocked(memberApi.list).mockReturnValueOnce(pendingPeople.promise)
    vi.mocked(memberApi.relationships).mockReturnValueOnce(pendingLinks.promise)
    const wrapper = await render()
    const otherPeople = people.map(m => ({ ...m, treeId: 2, name: `新家谱${m.name}` }))
    vi.mocked(memberApi.list).mockResolvedValue(otherPeople)
    vi.mocked(memberApi.relationships).mockResolvedValue([])
    useAppStore().setTree(2)
    await flushPromises()
    pendingPeople.resolve(people)
    pendingLinks.resolve([relation])
    await flushPromises()
    expect(wrapper.findAllComponents(MemberSelect)[0]!.props('members')).toEqual(otherPeople)
    expect(wrapper.text()).not.toContain('测试甲（1980-01-01）')
    expect(removeButton(wrapper)).toBeUndefined()
    wrapper.unmount()
  })

  it('does not let a stale save clear new selections, refresh another tree, or show success', async () => {
    const pending = deferred<Relationship>()
    const success = vi.spyOn(ElMessage, 'success')
    vi.mocked(memberApi.addRelation).mockReturnValue(pending.promise)
    const wrapper = await render()
    await select(wrapper, 1, 2)
    await saveButton(wrapper).trigger('click')
    // Switch away and back: checking only the tree ID would miss this stale operation.
    vi.mocked(memberApi.list).mockResolvedValue(people.map(m => ({ ...m, treeId: 2 })))
    vi.mocked(memberApi.relationships).mockResolvedValue([])
    useAppStore().setTree(2)
    await flushPromises()
    vi.mocked(memberApi.list).mockResolvedValue(people)
    useAppStore().setTree(1)
    await flushPromises()
    await select(wrapper, 2, 1)
    const listCalls = vi.mocked(memberApi.list).mock.calls.length
    pending.resolve(relation)
    await flushPromises()
    expect(wrapper.findAllComponents(MemberSelect)[0]!.props('modelValue')).toBe(2)
    expect(wrapper.findAllComponents(MemberSelect)[1]!.props('modelValue')).toBe(1)
    expect(memberApi.list).toHaveBeenCalledTimes(listCalls)
    expect(success).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('does not remove a relation after switching trees while confirmation is open', async () => {
    const pending = deferred<'confirm'>()
    vi.spyOn(ElMessageBox, 'confirm').mockImplementation(() => pending.promise as ReturnType<typeof ElMessageBox.confirm>)
    const wrapper = await render()
    await removeButton(wrapper).trigger('click')
    useAppStore().setTree(2)
    await flushPromises()
    pending.resolve('confirm')
    await flushPromises()
    expect(memberApi.removeRelation).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('relation removal', () => {
  it('shows member names and does not delete a relation when confirmation is cancelled', async () => {
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    const wrapper = await render()
    expect(wrapper.text()).toContain('测试甲')
    expect(wrapper.text()).toContain('测试乙')
    await removeButton(wrapper).trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('不删除成员，也不自动解除其他关系'), '解除关系', expect.objectContaining({ cancelButtonText: '取消', confirmButtonText: '确认解除' }))
    expect(memberApi.removeRelation).not.toHaveBeenCalled()
    expect(memberApi.relationships).toHaveBeenCalledTimes(1)
    expect(removeButton(wrapper).attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('deletes only the confirmed relation and reloads the table', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockImplementation(() => Promise.resolve('confirm') as ReturnType<typeof ElMessageBox.confirm>)
    const wrapper = await render()
    vi.mocked(memberApi.relationships).mockResolvedValue([])
    await removeButton(wrapper).trigger('click')
    await flushPromises()
    expect(memberApi.removeRelation).toHaveBeenCalledExactlyOnceWith(1, 7)
    expect(memberApi.relationships).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).not.toContain('测试甲（1980-01-01）')
    expect(wrapper.text()).toContain('暂无关系')
    wrapper.unmount()
  })
})
