import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import GraphView from '@/views/GraphView.vue'
import { memberApi, treeApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { FamilyTree, Member, Relationship } from '@/types'

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/api', () => ({ memberApi: { list: vi.fn(), relationships: vi.fn() }, treeApi: { list: vi.fn() } }))
const trees: FamilyTree[] = [1, 2].map(id => ({ id, name: `家谱${id}`, ownerId: 1, version: 0 }))
const members: Member[] = [1, 2, 3].map(id => ({ id, treeId: 1, name: `成员${id}`, gender: 'UNKNOWN' }))
const links: Relationship[] = [{ id: 1, treeId: 1, fromMemberId: 1, toMemberId: 2, relationType: 'PARENT', version: 0 }]
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done }); return { promise, resolve } }
function render() {
  return mount(GraphView, { global: { stubs: {
    FamilyGraph: { name: 'FamilyGraph', props: ['data', 'mode', 'focusId'], template: '<div class="graph-stub" />' },
    PageHeader: { template: '<header><slot /></header>' },
    ElDrawer: { template: '<div><slot /></div>' }, ElSelect: true, ElSegmented: true, ElOption: true,
    ElButton: { template: '<button><slot /></button>' }, ElEmpty: true, ElAlert: true,
  } } })
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers()
  const storage = new Map<string, string>()
  vi.stubGlobal('localStorage', { getItem: (key: string) => storage.get(key) || null, setItem: (key: string, value: string) => storage.set(key, value), removeItem: (key: string) => storage.delete(key), clear: () => storage.clear() })
  setActivePinia(createPinia())
  useAppStore().setTree(1)
  vi.mocked(treeApi.list).mockResolvedValue(trees)
  vi.mocked(memberApi.list).mockResolvedValue(members)
  vi.mocked(memberApi.relationships).mockResolvedValue(links)
})
afterEach(() => { vi.useRealTimers() })

describe('graph read refresh', () => {
  it('loads complete members/relationships, reloads only matching event/storage and cleans up', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.findComponent({ name: 'FamilyGraph' }).props('data').nodes).toHaveLength(3)
    expect(memberApi.list).toHaveBeenCalledTimes(1)
    window.dispatchEvent(new CustomEvent('family-tree-data-changed', { detail: { treeId: 999 } }))
    await flushPromises()
    expect(memberApi.list).toHaveBeenCalledTimes(1)
    window.dispatchEvent(new CustomEvent('family-tree-data-changed', { detail: { treeId: '1' } }))
    await flushPromises()
    expect(memberApi.list).toHaveBeenCalledTimes(2)
    window.dispatchEvent(new StorageEvent('storage', { key: 'family-tree-data-changed', newValue: JSON.stringify({ treeId: 1, nonce: 'changed' }) }))
    await flushPromises()
    expect(memberApi.relationships).toHaveBeenCalledTimes(3)
    wrapper.unmount()
    window.dispatchEvent(new CustomEvent('family-tree-data-changed', { detail: { treeId: 1 } }))
    window.dispatchEvent(new Event('focus'))
    vi.advanceTimersByTime(30_000)
    await flushPromises()
    expect(memberApi.list).toHaveBeenCalledTimes(3)
  })
  it('queues burst updates, discards stale tree data, and never overlaps request batches', async () => {
    const firstMembers = deferred<Member[]>(), firstLinks = deferred<Relationship[]>()
    vi.mocked(memberApi.list).mockReturnValueOnce(firstMembers.promise)
    vi.mocked(memberApi.relationships).mockReturnValueOnce(firstLinks.promise)
    const wrapper = render()
    await flushPromises()
    for (let i = 0; i < 5; i++) window.dispatchEvent(new CustomEvent('family-tree-data-changed', { detail: { treeId: '1' } }))
    useAppStore().setTree(2)
    await flushPromises()
    expect(memberApi.list).toHaveBeenCalledTimes(1)
    firstMembers.resolve(members)
    await flushPromises()
    expect(memberApi.list).toHaveBeenCalledTimes(1)
    const nextMembers = [{ ...members[0]!, id: 9, treeId: 2, name: '新树成员' }]
    vi.mocked(memberApi.list).mockResolvedValue(nextMembers)
    vi.mocked(memberApi.relationships).mockResolvedValue([])
    firstLinks.resolve(links)
    await flushPromises()
    expect(memberApi.list).toHaveBeenCalledTimes(2)
    expect(memberApi.list).toHaveBeenLastCalledWith('2')
    expect(wrapper.findComponent({ name: 'FamilyGraph' }).props('data').nodes.map((node: { name: string }) => node.name)).toEqual(['新树成员'])
    wrapper.unmount()
  })
  it.each([401, 403, 404])('clears stale graph on %s, pauses background retries and re-lists trees on manual refresh', async status => {
    const wrapper = render()
    await flushPromises()
    const graph = wrapper.findComponent({ name: 'FamilyGraph' })
    graph.vm.$emit('select', graph.props('data').nodes[0])
    await flushPromises()
    vi.mocked(memberApi.relationships).mockRejectedValueOnce({ response: { status } })
    window.dispatchEvent(new CustomEvent('family-tree-data-changed', { detail: { treeId: '1' } }))
    await flushPromises()
    expect(wrapper.findComponent({ name: 'FamilyGraph' }).exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'ElAlert' }).attributes('title')).toContain('无访问权限')
    const calls = vi.mocked(memberApi.list).mock.calls.length
    window.dispatchEvent(new Event('focus'))
    vi.advanceTimersByTime(30_000)
    window.dispatchEvent(new CustomEvent('family-tree-data-changed', { detail: { treeId: '1' } }))
    await flushPromises()
    expect(memberApi.list).toHaveBeenCalledTimes(calls)
    vi.mocked(treeApi.list).mockResolvedValue([])
    await wrapper.findAll('button').find(button => button.text() === '刷新')!.trigger('click')
    await flushPromises()
    expect(treeApi.list).toHaveBeenCalledTimes(2)
    expect(wrapper.findComponent({ name: 'FamilyGraph' }).exists()).toBe(false)
    wrapper.unmount()
  })
  it('retains previous graph for a temporary 500 response', async () => {
    const wrapper = render()
    await flushPromises()
    vi.mocked(memberApi.relationships).mockRejectedValueOnce({ response: { status: 500 } })
    window.dispatchEvent(new CustomEvent('family-tree-data-changed', { detail: { treeId: '1' } }))
    await flushPromises()
    expect(wrapper.findComponent({ name: 'FamilyGraph' }).props('data').nodes).toHaveLength(3)
    wrapper.unmount()
  })
  it('refreshes on focus/visibility and every 15 seconds only while visible', async () => {
    const wrapper = render()
    await flushPromises()
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    vi.advanceTimersByTime(15_000)
    await flushPromises()
    expect(memberApi.list).toHaveBeenCalledTimes(1)
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    vi.advanceTimersByTime(15_000)
    await flushPromises()
    expect(memberApi.list).toHaveBeenCalledTimes(4)
    wrapper.unmount()
  })
})
