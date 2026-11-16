import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FamilyGraph from '@/components/FamilyGraph.vue'
import { buildGraphData, graphGender, layoutGraph, linkDescription, NODE_HEIGHT, NODE_WIDTH, relationLabel, type GraphMode } from '@/utils/graph'
import type { Member, Relationship } from '@/types'

const member = (id: number, gender: Member['gender'] = 'UNKNOWN'): Member => ({ id, treeId: 1, name: `成员${id}`, gender })
const relation = (id: number, from: number, to: number, relationType: Relationship['relationType'] = 'PARENT'): Relationship => ({ id, treeId: 1, fromMemberId: from, toMemberId: to, relationType, version: 0 })
const members = [member(1, 'MALE'), member(2, 'FEMALE'), member(3, 'FEMALE'), member(4), member(5, 'OTHER'), member(6), member(7)]
const relationships = [relation(1, 1, 2), relation(2, 1, 3), relation(3, 3, 4), relation(4, 2, 5, 'SPOUSE'), relation(5, 2, 3, 'SIBLING')]

describe('full family graph data', () => {
  it('keeps collateral branches, spouses, unconnected members and only real edges, regardless of focus', () => {
    const graph = buildGraphData(members, relationships, '4')
    expect(graph.nodes.map(node => node.id)).toEqual(['1', '2', '3', '4', '5', '6', '7'])
    expect(graph.links).toHaveLength(5)
    expect(graph.links.map(link => link.id)).toEqual(['1', '2', '3', '4', '5'])
    expect(graph.nodes).toEqual(buildGraphData(members, relationships, '1').nodes)
    expect(graph.links[0]?.label).toBe('父亲 → 女儿')
    expect(graph.nodes.find(node => node.id === '6')?.gender).toBe('未标注')
  })
  it.each([
    ['MALE', '男'], ['FEMALE', '女'], ['OTHER', '其他'], ['UNKNOWN', '未标注'], [undefined, '未标注'], ['unexpected', '未标注'],
  ])('distinguishes gender %s as %s', (input, output) => expect(graphGender(input)).toBe(output))
  it('updates both endpoint gender labels on a rebuilt parent relation', () => {
    const graph = buildGraphData([member(1, 'FEMALE'), member(2, 'MALE')], [relation(1, 1, 2)])
    expect(graph.links[0]?.label).toBe('母亲 → 儿子')
    expect(buildGraphData([member(1), member(2, 'OTHER')], [relation(1, 1, 2)]).links[0]?.label).toBe('父母 → 子女')
  })
  it('keeps spouse/sibling undirected full names, excludes dangling edges and bounds cycles', () => {
    const graph = buildGraphData(members, [...relationships, relation(6, 4, 1), relation(7, 1, 999)])
    expect(graph.links).toHaveLength(6)
    expect(graph.links[3]?.label).toBe('配偶')
    expect(graph.links[4]?.label).toBe('兄弟姐妹')
    const nodes = new Map(graph.nodes.map(node => [node.id, node]))
    expect(linkDescription(graph.links[0]!, nodes)).toContain('成员1（男） → 成员2（女）：父亲 → 女儿')
    expect(relationLabel(graph.links[4]!)).toBe('兄弟姐妹')
  })
})

describe.each<GraphMode>(['tree', 'radial', 'fan', 'hourglass'])('%s layout', mode => {
  it('has finite bounded coordinates and nonoverlapping cards, even for a large first generation', () => {
    const nodes = Array.from({ length: 60 }, (_, i) => ({ id: String(i), name: `成员${i}`, generation: i < 35 ? 1 : i < 50 ? 2 : 3 }))
    const layout = layoutGraph(nodes, mode)
    expect(layout.positions.size).toBe(nodes.length)
    const points = [...layout.positions.values()]
    for (let i = 0; i < points.length; i++) {
      const [x, y] = points[i]!
      expect(Number.isFinite(x) && Number.isFinite(y)).toBe(true)
      expect(x - NODE_WIDTH / 2).toBeGreaterThanOrEqual(0)
      expect(y - NODE_HEIGHT / 2).toBeGreaterThanOrEqual(0)
      expect(x + NODE_WIDTH / 2).toBeLessThanOrEqual(layout.width)
      expect(y + NODE_HEIGHT / 2).toBeLessThanOrEqual(layout.height)
      for (let j = i + 1; j < points.length; j++) {
        expect(Math.abs(x - points[j]![0]) >= NODE_WIDTH || Math.abs(y - points[j]![1]) >= NODE_HEIGHT).toBe(true)
      }
    }
  })
})

it('renders safe SVG text, keyboard nodes, titles, arrows and a full table alternative', async () => {
  const hostile = '<img src=x onerror=alert(1)>'
  const graph = buildGraphData([{ ...member(1), name: hostile }, member(2, 'FEMALE')], [relation(1, 1, 2)])
  const wrapper = mount(FamilyGraph, { props: { data: graph, mode: 'tree', focusId: '1' }, global: { stubs: { ElButton: { template: '<button><slot /></button>' } } } })
  expect(wrapper.findAll('.graph-node')).toHaveLength(2)
  expect(wrapper.findAll('.graph-link')).toHaveLength(1)
  expect(wrapper.find('.graph-node title').text()).toContain(hostile)
  expect(wrapper.find('img').exists()).toBe(false)
  expect(wrapper.find('.graph-node').attributes('tabindex')).toBe('0')
  expect(wrapper.find('.graph-link .edge-line').attributes('marker-end')).toContain('url(#graph-arrow-')
  expect(wrapper.findAll('table')).toHaveLength(2)
  await wrapper.find('.graph-node').trigger('keydown', { key: ' ' })
  expect(wrapper.emitted('select')).toHaveLength(1)
  await wrapper.setProps({ focusId: '2' })
  expect(wrapper.findAll('.graph-node')).toHaveLength(2)
  wrapper.unmount()
})
