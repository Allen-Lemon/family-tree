import type { GraphData, GraphNode } from '@/types'
import { graphGender, isDirected } from '@/utils/graph'

export type RelationshipStepLabel = '父亲' | '母亲' | '父母' | '儿子' | '女儿' | '子女' | '配偶' | '兄弟姐妹'
export interface RelationshipStep { targetId: string; label: RelationshipStepLabel }
export interface DirectRelationships {
  node: GraphNode
  parents: RelationshipStep[]
  children: RelationshipStep[]
  spouses: RelationshipStep[]
  siblings: RelationshipStep[]
}
export interface RelationshipIndex {
  members: Map<string, DirectRelationships>
  adjacency: Map<string, RelationshipStep[]>
}
export interface RelationshipVisit { previousId?: string; label?: RelationshipStepLabel; distance: number }
export interface RelationshipPaths { referenceId: string; visits: Map<string, RelationshipVisit> }

/** A step names the target relative to the starting member, never the other way round. */
export function parentRole(node: GraphNode): RelationshipStepLabel {
  const gender = graphGender(node.gender)
  return gender === '男' ? '父亲' : gender === '女' ? '母亲' : '父母'
}
export function childRole(node: GraphNode): RelationshipStepLabel {
  const gender = graphGender(node.gender)
  return gender === '男' ? '儿子' : gender === '女' ? '女儿' : '子女'
}

/** Index actual incident edges only: no inferred siblings, spouses or transitive relatives. */
export function buildRelationshipIndex(data: GraphData): RelationshipIndex {
  const members = new Map<string, DirectRelationships>()
  const adjacency = new Map<string, RelationshipStep[]>()
  for (const node of data.nodes) {
    members.set(node.id, { node, parents: [], children: [], spouses: [], siblings: [] })
    adjacency.set(node.id, [])
  }
  // Duplicate records should not repeat the same member/role in a summary.
  const seen = new Map<string, Set<string>>()
  for (const id of members.keys()) seen.set(id, new Set())
  function add(from: string, to: string, bucket: 'parents' | 'children' | 'spouses' | 'siblings', label: RelationshipStepLabel) {
    const key = `${bucket}:${to}`
    if (seen.get(from)!.has(key)) return
    seen.get(from)!.add(key)
    const step = { targetId: to, label }
    members.get(from)![bucket].push(step)
    adjacency.get(from)!.push(step)
  }
  for (const link of data.links) {
    const source = members.get(link.source), target = members.get(link.target)
    if (!source || !target || link.source === link.target) continue
    if (isDirected(link)) {
      add(link.source, link.target, 'children', childRole(target.node))
      add(link.target, link.source, 'parents', parentRole(source.node))
    } else if (link.relationType === 'SPOUSE' || link.kind === '配偶') {
      add(link.source, link.target, 'spouses', '配偶')
      add(link.target, link.source, 'spouses', '配偶')
    } else if (link.relationType === 'SIBLING' || link.kind === '兄弟姐妹') {
      add(link.source, link.target, 'siblings', '兄弟姐妹')
      add(link.target, link.source, 'siblings', '兄弟姐妹')
    }
  }
  return { members, adjacency }
}

/** One O(V+E) BFS for the selected reference. Store predecessors, not all-pairs paths. */
export function findRelationshipPaths(index: RelationshipIndex, referenceId: string): RelationshipPaths {
  const visits = new Map<string, RelationshipVisit>()
  if (!index.members.has(referenceId)) return { referenceId, visits }
  visits.set(referenceId, { distance: 0 })
  const queue = [referenceId]
  for (let cursor = 0; cursor < queue.length; cursor++) {
    const current = queue[cursor]!
    for (const step of index.adjacency.get(current) || []) {
      if (visits.has(step.targetId)) continue
      visits.set(step.targetId, { previousId: current, label: step.label, distance: visits.get(current)!.distance + 1 })
      queue.push(step.targetId)
    }
  }
  return { referenceId, visits }
}

export function relationshipStatus(paths: RelationshipPaths, targetId: string) {
  const visit = paths.visits.get(targetId)
  if (!visit) return '未记录关系'
  return visit.distance === 0 ? '本人' : visit.distance === 1 ? '直接关系' : '间接关系'
}

/** A shortest recorded path is evidence of connectivity, not a confirmed kinship title. */
export function relationshipPathText(index: RelationshipIndex, paths: RelationshipPaths, targetId: string): string {
  const visit = paths.visits.get(targetId)
  if (!visit) return '未记录关系'
  if (visit.distance === 0) return '本人'
  const steps: string[] = []
  let current = targetId
  while (current !== paths.referenceId) {
    const step = paths.visits.get(current)!
    steps.push(` --${step.label}→${index.members.get(current)!.node.name}`)
    current = step.previousId!
  }
  return index.members.get(paths.referenceId)!.node.name + steps.reverse().join('')
}

export function directRelationshipText(index: RelationshipIndex, steps: RelationshipStep[]): string {
  return steps.map(step => `${step.label}：${index.members.get(step.targetId)!.node.name}`).join('；') || '无已记录'
}
