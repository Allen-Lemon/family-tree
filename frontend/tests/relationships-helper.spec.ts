import { describe, expect, it } from 'vitest'
import type { GraphData, Member, Relationship } from '@/types'
import { buildGraphData } from '@/utils/graph'
import { buildRelationshipIndex, directRelationshipText, findRelationshipPaths, relationshipPathText, relationshipStatus } from '@/utils/relationships'

const member = (id: number, gender: Member['gender'] = 'UNKNOWN'): Member => ({ id, treeId: 1, name: `成员${id}`, gender })
const edge = (id: number, from: number, to: number, relationType: Relationship['relationType'] = 'PARENT'): Relationship => ({ id, treeId: 1, fromMemberId: from, toMemberId: to, relationType, version: 0 })
const members = [member(1, 'MALE'), member(2, 'FEMALE'), member(3, 'MALE'), member(4, 'FEMALE'), member(5), member(6, 'OTHER'), member(7)]
const links = [edge(1, 1, 2), edge(2, 2, 3), edge(3, 2, 4, 'SPOUSE'), edge(4, 3, 5, 'SIBLING'), edge(5, 6, 5)]

function fixture() { return buildGraphData(members, links) }

describe('actual incident relationship summaries', () => {
  it('includes zero-edge members and never mixes another member’s relatives into a row', () => {
    const index = buildRelationshipIndex(fixture())
    expect(index.members.size).toBe(7)
    expect(index.members.get('1')!.children).toEqual([{ targetId: '2', label: '女儿' }])
    expect(index.members.get('1')!.parents).toEqual([])
    expect(index.members.get('1')!.spouses).toEqual([])
    expect(index.members.get('1')!.siblings).toEqual([])
    expect(index.members.get('2')!.parents).toEqual([{ targetId: '1', label: '父亲' }])
    expect(index.members.get('2')!.children).toEqual([{ targetId: '3', label: '儿子' }])
    expect(index.members.get('2')!.spouses).toEqual([{ targetId: '4', label: '配偶' }])
    expect(index.members.get('4')!.spouses).toEqual([{ targetId: '2', label: '配偶' }])
    expect(index.members.get('3')!.siblings).toEqual([{ targetId: '5', label: '兄弟姐妹' }])
    expect(index.members.get('5')!.siblings).toEqual([{ targetId: '3', label: '兄弟姐妹' }])
    for (const key of ['parents', 'children', 'spouses', 'siblings'] as const) expect(index.members.get('7')![key]).toEqual([])
    expect(directRelationshipText(index, [])).toBe('无已记录')
  })

  it.each([
    ['MALE', '父亲', '儿子'], ['FEMALE', '母亲', '女儿'], ['OTHER', '父母', '子女'], ['UNKNOWN', '父母', '子女'],
  ] as const)('uses target gender for both directions: %s', (gender, parent, child) => {
    const index = buildRelationshipIndex(buildGraphData([member(1, gender), member(2, gender)], [edge(1, 1, 2)]))
    expect(index.members.get('2')!.parents[0]!.label).toBe(parent)
    expect(index.members.get('1')!.children[0]!.label).toBe(child)
    expect(relationshipPathText(index, findRelationshipPaths(index, '2'), '1')).toBe(`成员2 --${parent}→成员1`)
    expect(relationshipPathText(index, findRelationshipPaths(index, '1'), '2')).toBe(`成员1 --${child}→成员2`)
  })

  it('does not infer sibling edges from shared parents; ignores dangling/self edges and deduplicates real edges', () => {
    const graph = buildGraphData(members, [edge(1, 1, 2), edge(2, 1, 3), edge(3, 1, 2), edge(4, 2, 4, 'SPOUSE'), edge(5, 4, 2, 'SPOUSE')])
    graph.links.push({ source: '1', target: 'missing', kind: '亲子' }, { source: '1', target: '1', kind: '亲子' })
    const index = buildRelationshipIndex(graph)
    expect(index.members.get('1')!.children).toHaveLength(2)
    expect(index.members.get('2')!.spouses).toHaveLength(1)
    expect(index.members.get('2')!.siblings).toEqual([])
    expect(index.members.get('3')!.siblings).toEqual([])
  })
})

describe('single-reference recorded paths', () => {
  it('distinguishes self, direct, indirect and unrecorded relationships with directional step labels', () => {
    const index = buildRelationshipIndex(fixture()), paths = findRelationshipPaths(index, '3')
    expect(relationshipStatus(paths, '3')).toBe('本人')
    expect(relationshipPathText(index, paths, '3')).toBe('本人')
    expect(relationshipStatus(paths, '2')).toBe('直接关系')
    expect(relationshipPathText(index, paths, '2')).toBe('成员3 --母亲→成员2')
    expect(relationshipStatus(paths, '1')).toBe('间接关系')
    expect(relationshipPathText(index, paths, '1')).toBe('成员3 --母亲→成员2 --父亲→成员1')
    expect(relationshipPathText(index, paths, '4')).toBe('成员3 --母亲→成员2 --配偶→成员4')
    expect(relationshipPathText(index, paths, '6')).toBe('成员3 --兄弟姐妹→成员5 --父母→成员6')
    expect(relationshipStatus(paths, '7')).toBe('未记录关系')
    expect(relationshipPathText(index, paths, '7')).toBe('未记录关系')
    expect(paths.visits.size).toBe(6)
  })

  it('bounds cycles, picks a shortest actual-edge path, and supports a disconnected reference', () => {
    const graph = fixture()
    graph.links.push({ source: '3', target: '1', relationType: 'SPOUSE', kind: '配偶' })
    const index = buildRelationshipIndex(graph), paths = findRelationshipPaths(index, '3')
    expect(paths.visits.get('1')!.distance).toBe(1)
    expect(relationshipPathText(index, paths, '1')).toBe('成员3 --配偶→成员1')
    const isolated = findRelationshipPaths(index, '7')
    expect(isolated.visits.size).toBe(1)
    expect(relationshipStatus(isolated, '7')).toBe('本人')
    expect(relationshipStatus(isolated, '1')).toBe('未记录关系')
    expect(findRelationshipPaths(index, 'deleted').visits.size).toBe(0)
    expect(buildRelationshipIndex({ nodes: [], links: [] }).members.size).toBe(0)
  })

  it('rebuilds from additions, removals, names, gender and edge direction changes without mutating graph data', () => {
    const graph: GraphData = fixture(), before = JSON.stringify(graph)
    buildRelationshipIndex(graph)
    expect(JSON.stringify(graph)).toBe(before)
    graph.nodes[0]!.name = '更新姓名'
    graph.nodes[0]!.gender = '女'
    graph.links[0]!.source = '2'
    graph.links[0]!.target = '1'
    graph.links = graph.links.filter(link => link.id !== '3')
    graph.links.push({ source: '1', target: '7', kind: '配偶', relationType: 'SPOUSE' })
    let index = buildRelationshipIndex(graph)
    expect(directRelationshipText(index, index.members.get('2')!.children)).toContain('女儿：更新姓名')
    expect(index.members.get('2')!.spouses).toEqual([])
    expect(relationshipPathText(index, findRelationshipPaths(index, '7'), '1')).toBe('成员7 --配偶→更新姓名')
    graph.nodes = graph.nodes.filter(node => node.id !== '1')
    index = buildRelationshipIndex(graph)
    expect(index.members.has('1')).toBe(false)
    expect(index.members.get('7')!.spouses).toEqual([])
  })
})
