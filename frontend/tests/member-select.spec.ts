import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ElOption, ElSelect } from 'element-plus'
import MemberSelect from '@/components/MemberSelect.vue'
import { memberApi } from '@/api'
import type { Member } from '@/types'

vi.mock('@/api', () => ({ memberApi: { list: vi.fn() } }))
const list = vi.mocked(memberApi.list)
const member = (id: number, name: string, extra: Partial<Member> = {}): Member => ({ id, name, treeId: 1, gender: 'UNKNOWN', ...extra })
const members = [member(1, '张明', { birthDate: '1950-01-01', birthPlace: '杭州' }), member(2, '张明'), member(3, '李华')]
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

beforeEach(() => { list.mockReset() })

describe('MemberSelect', () => {
  it('reuses supplied members, distinguishes duplicate names and filters by name', async () => {
    const wrapper = mount(MemberSelect, { props: { treeId: 1, members } })
    const select = wrapper.getComponent(ElSelect)
    expect(select.props('filterable')).toBe(true)
    expect(select.props('clearable')).toBe(true)
    expect(list).not.toHaveBeenCalled()
    const labels = wrapper.findAllComponents(ElOption).map(option => option.props('label'))
    expect(labels).toEqual(['张明（1950-01-01 · 杭州 · #1）', '张明（出生日期不详 · 出生地不详 · #2）', '李华'])
    const filter = select.props('filterMethod') as (query: string) => void
    filter(' 张明 ')
    await flushPromises()
    expect(wrapper.findAllComponents(ElOption).map(option => option.props('value'))).toEqual([1, 2])
    filter('不存在')
    await flushPromises()
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(0)
    wrapper.unmount()
  })

  it('accepts string IDs, emits internal numeric IDs and supports empty values', async () => {
    const wrapper = mount(MemberSelect, { props: { treeId: '1', members, modelValue: '2' } })
    const select = wrapper.getComponent(ElSelect)
    expect(select.props('modelValue')).toBe(2)
    select.vm.$emit('update:modelValue', 3)
    select.vm.$emit('update:modelValue', undefined)
    select.vm.$emit('update:modelValue', null)
    select.vm.$emit('update:modelValue', 'free text')
    expect(wrapper.emitted('update:modelValue')).toEqual([[3], [''], [''], ['']])
    await wrapper.setProps({ modelValue: null })
    expect(select.props('modelValue')).toBeUndefined()
    wrapper.unmount()
  })

  it('loads the selected tree and discards a late response from the previous tree', async () => {
    const old = deferred<Member[]>(), next = deferred<Member[]>()
    list.mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise)
    const wrapper = mount(MemberSelect, { props: { treeId: 1, modelValue: 1 } })
    expect(list).toHaveBeenCalledWith('1')
    await wrapper.setProps({ treeId: 2 })
    expect(wrapper.emitted('update:modelValue')).toEqual([['']])
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(0)
    next.resolve([member(20, '新家谱成员', { treeId: 2 })])
    await flushPromises()
    old.resolve(members)
    await flushPromises()
    expect(wrapper.findAllComponents(ElOption).map(option => option.props('label'))).toEqual(['新家谱成员'])
    expect(wrapper.getComponent(ElSelect).props('loading')).toBe(false)
    wrapper.unmount()
  })

  it('clears supplied options when switching trees and ignores other-tree/deleted members', async () => {
    const wrapper = mount(MemberSelect, { props: { treeId: 1, members: [...members, member(4, '已删除', { deleted: true }), member(5, '其他家谱', { treeId: 2 })] } })
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(3)
    await wrapper.setProps({ treeId: 3 })
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(0)
    expect(list).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('does not request without a tree and clears old options when the tree is cleared', async () => {
    list.mockResolvedValue(members)
    const wrapper = mount(MemberSelect)
    expect(list).not.toHaveBeenCalled()
    expect(wrapper.getComponent(ElSelect).props('disabled')).toBe(true)
    await wrapper.setProps({ treeId: 1 })
    await flushPromises()
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(3)
    await wrapper.setProps({ treeId: '' })
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(0)
    expect(wrapper.getComponent(ElSelect).props('modelValue')).toBeUndefined()
    expect(list).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('handles loading failures without stale options and can reload on a tree change', async () => {
    list.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([member(20, '新成员', { treeId: 2 })])
    const wrapper = mount(MemberSelect, { props: { treeId: 1 } })
    await flushPromises()
    expect(wrapper.getComponent(ElSelect).props('loading')).toBe(false)
    expect(wrapper.findAllComponents(ElOption)).toHaveLength(0)
    await wrapper.setProps({ treeId: 2 })
    await flushPromises()
    expect(wrapper.findAllComponents(ElOption).map(option => option.props('value'))).toEqual([20])
    wrapper.unmount()
  })

  it('invalidates an in-flight request when supplied members become available', async () => {
    const pending = deferred<Member[]>()
    list.mockReturnValueOnce(pending.promise)
    const wrapper = mount(MemberSelect, { props: { treeId: 1 } })
    await wrapper.setProps({ members: [member(9, '共享成员')] })
    pending.resolve(members)
    await flushPromises()
    expect(wrapper.findAllComponents(ElOption).map(option => option.props('value'))).toEqual([9])
    expect(wrapper.getComponent(ElSelect).props('loading')).toBe(false)
    wrapper.unmount()
  })
})
