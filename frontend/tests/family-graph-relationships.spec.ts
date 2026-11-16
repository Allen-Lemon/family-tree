import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import { ElRadioButton, ElRadioGroup } from 'element-plus'
import FamilyGraph from '@/components/FamilyGraph.vue'
import type { GraphData, Member, Relationship } from '@/types'
import { buildGraphData } from '@/utils/graph'
import * as relationships from '@/utils/relationships'

const member = (id: number, name: string, gender: Member['gender']): Member => ({ id, treeId: 1, name, gender })
const edge = (id: number, from: number, to: number, relationType: Relationship['relationType'] = 'PARENT'): Relationship => ({ id, treeId: 1, fromMemberId: from, toMemberId: to, relationType, version: 0 })
function fixture() {
  return buildGraphData([
    member(1, '甲', 'MALE'), member(2, '乙', 'FEMALE'), member(3, '丙', 'MALE'), member(4, '丁', 'UNKNOWN'), member(5, '戊', 'OTHER'),
  ], [edge(1, 1, 2), edge(2, 2, 3), edge(3, 2, 5, 'SPOUSE')])
}
function render(data: GraphData = fixture()) {
  return mount(FamilyGraph, {
    props: { data, mode: 'tree' },
    global: {
      components: { ElRadioGroup, ElRadioButton },
      stubs: { ElButton: { template: '<button><slot /></button>' }, ElCheckbox: true },
    },
  })
}
type Wrapper = ReturnType<typeof render>
async function openSummary(wrapper: Wrapper) {
  await wrapper.get('input[type="radio"][value="relationships"]').setValue(true)
}
const row = (wrapper: Wrapper, id: string) => wrapper.get(`.relationship-summary tr[data-member-id="${id}"]`)
const cells = (wrapper: Wrapper, id: string) => row(wrapper, id).findAll('td').map(cell => cell.text())
afterEach(() => vi.restoreAllMocks())

describe('all-member relationships panel', () => {
  it('preserves the graph and existing tables; includes every member, gender and incident relationships by default', async () => {
    const wrapper = render()
    expect(wrapper.findAll('.graph-node')).toHaveLength(5)
    expect(wrapper.findAll('.graph-link')).toHaveLength(3)
    expect(wrapper.findAll('.graph-tables table')).toHaveLength(2)
    await openSummary(wrapper)
    expect(wrapper.get('.relationship-summary').isVisible()).toBe(true)
    expect(wrapper.findAll('.relationship-summary tbody tr')).toHaveLength(5)
    expect(cells(wrapper, '1').slice(0, 5)).toEqual(['男', '无已记录', '女儿：乙', '无已记录', '无已记录'])
    expect(cells(wrapper, '2').slice(0, 5)).toEqual(['女', '父亲：甲', '儿子：丙', '配偶：戊', '无已记录'])
    expect(cells(wrapper, '4').slice(0, 5)).toEqual(['未标注', '无已记录', '无已记录', '无已记录', '无已记录'])
    expect(cells(wrapper, '5').slice(0, 5)).toEqual(['其他', '无已记录', '无已记录', '配偶：乙', '无已记录'])
    expect(wrapper.get('.graph-legend').text()).toContain('父母 → 子女')
    expect(wrapper.get('.graph-legend').text()).toContain('男')
    expect(wrapper.get('.graph-legend').text()).toContain('女')
    await row(wrapper, '4').get('button').trigger('click')
    expect(wrapper.emitted('select')![0]![0]).toMatchObject({ id: '4', name: '丁' })
    wrapper.unmount()
  })

  it('shows self/direct/indirect/unrecorded paths, uses one BFS per reference, and filters names without recomputing BFS', async () => {
    const bfs = vi.spyOn(relationships, 'findRelationshipPaths')
    const wrapper = render()
    await openSummary(wrapper)
    expect(bfs).not.toHaveBeenCalled()
    const reference = wrapper.get('select[aria-label="选择参照成员姓名"]')
    await reference.setValue('3')
    expect(bfs).toHaveBeenCalledTimes(1)
    expect(cells(wrapper, '3').slice(5, 7)).toEqual(['本人', '本人'])
    expect(cells(wrapper, '2').slice(5, 7)).toEqual(['直接关系', '丙 --母亲→乙'])
    expect(cells(wrapper, '1').slice(5, 7)).toEqual(['间接关系', '丙 --母亲→乙 --父亲→甲'])
    expect(cells(wrapper, '5').slice(5, 7)).toEqual(['间接关系', '丙 --母亲→乙 --配偶→戊'])
    expect(cells(wrapper, '4').slice(5, 7)).toEqual(['未记录关系', '未记录关系'])
    expect(wrapper.get('.relationship-note').text()).toContain('不推断或确认叔伯')
    const search = wrapper.get('input[type="search"]')
    await search.setValue(' 甲 ')
    expect(wrapper.findAll('.relationship-summary tbody tr[data-member-id]')).toHaveLength(1)
    expect(row(wrapper, '1').text()).toContain('丙 --母亲→乙 --父亲→甲')
    expect(reference.findAll('option')).toHaveLength(6)
    await search.setValue('不存在')
    expect(wrapper.get('.relationship-summary tbody').text()).toContain('没有匹配姓名的成员')
    await search.setValue('')
    expect(wrapper.findAll('.relationship-summary tbody tr[data-member-id]')).toHaveLength(5)
    expect(bfs).toHaveBeenCalledTimes(1)
    await reference.setValue('4')
    expect(cells(wrapper, '4').slice(5, 7)).toEqual(['本人', '本人'])
    expect(cells(wrapper, '1')[5]).toBe('未记录关系')
    expect(bfs).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('reacts to replacement and in-place data additions, removals and updates, and clears a deleted reference', async () => {
    const data = reactive(fixture())
    const wrapper = render(data)
    await openSummary(wrapper)
    const reference = wrapper.get('select[aria-label="选择参照成员姓名"]')
    await reference.setValue('3')
    data.nodes[0]!.name = '新甲'
    data.nodes[0]!.gender = '女'
    data.links = data.links.filter(link => link.id !== '3')
    data.links.push({ source: '3', target: '4', kind: '兄弟姐妹', relationType: 'SIBLING' })
    data.nodes.push({ id: '6', name: '新增成员', gender: '男', generation: 1 })
    await nextTick()
    expect(cells(wrapper, '1')[0]).toBe('女')
    expect(cells(wrapper, '2')[1]).toBe('母亲：新甲')
    expect(cells(wrapper, '1')[6]).toBe('丙 --母亲→乙 --母亲→新甲')
    expect(cells(wrapper, '2')[3]).toBe('无已记录')
    expect(cells(wrapper, '5')[5]).toBe('未记录关系')
    expect(cells(wrapper, '4').slice(5, 7)).toEqual(['直接关系', '丙 --兄弟姐妹→丁'])
    expect(row(wrapper, '6').text()).toContain('新增成员')
    // Remove only the edge; both surviving members immediately become disconnected.
    data.links = data.links.filter(link => link.kind !== '兄弟姐妹')
    await nextTick()
    expect(cells(wrapper, '4')[5]).toBe('未记录关系')
    data.nodes = data.nodes.filter(node => node.id !== '3')
    await nextTick()
    expect(wrapper.find('.relationship-summary tr[data-member-id="3"]').exists()).toBe(false)
    expect((reference.element as HTMLSelectElement).value).toBe('')
    expect(wrapper.get('.relationship-summary thead').text()).not.toContain('已记录关系路径')
    await wrapper.setProps({ data: buildGraphData([member(9, '替换成员', 'FEMALE')], []) })
    expect(wrapper.findAll('.relationship-summary tr[data-member-id]')).toHaveLength(1)
    expect(row(wrapper, '9').text()).toContain('替换成员')
    expect(wrapper.find('.relationship-summary tr[data-member-id="1"]').exists()).toBe(false)
    await wrapper.setProps({ data: { nodes: [], links: [] } })
    expect(wrapper.get('.relationship-summary tbody').text()).toContain('暂无成员')
    wrapper.unmount()
  })

  it('escapes names and path content rather than interpreting markup', async () => {
    const data = fixture()
    data.nodes[0]!.name = '<img src=x onerror=alert(1)>'
    const wrapper = render(data)
    await openSummary(wrapper)
    await wrapper.get('select').setValue('2')
    expect(row(wrapper, '1').text()).toContain('<img src=x onerror=alert(1)>')
    expect(wrapper.find('img').exists()).toBe(false)
    wrapper.unmount()
  })
})
