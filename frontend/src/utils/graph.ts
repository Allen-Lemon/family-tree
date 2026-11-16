import type { GraphData, GraphLink, GraphNode, Member, Relationship } from '@/types'

export type GraphMode = 'tree' | 'radial' | 'fan' | 'hourglass'
export type GraphGender = '男' | '女' | '其他' | '未标注'
export function graphGender(value?: string): GraphGender {
  if (value === 'MALE' || value === '男') return '男'
  if (value === 'FEMALE' || value === '女') return '女'
  if (value === 'OTHER' || value === '其他') return '其他'
  return '未标注'
}
export function genderSymbol(value?: string) {
  return { 男: '□', 女: '○', 其他: '◇', 未标注: '?' }[graphGender(value)]
}
export function isDirected(link: GraphLink) {
  return link.relationType === 'PARENT' || ['亲子', '父子', '母子', '收养'].includes(link.kind)
}
export function relationLabel(link: GraphLink, source?: GraphNode, target?: GraphNode) {
  if (!isDirected(link)) return link.kind
  const parent = graphGender(source?.gender), child = graphGender(target?.gender)
  return `${parent === '男' ? '父亲' : parent === '女' ? '母亲' : '父母'} → ${child === '男' ? '儿子' : child === '女' ? '女儿' : '子女'}`
}
export function nodeDescription(node: GraphNode) {
  return `${node.name}（${graphGender(node.gender)}），布局代序 ${node.generation}${node.life ? `，${node.life}` : ''}`
}
export function linkDescription(link: GraphLink, nodes: Map<string, GraphNode>) {
  const source = nodes.get(link.source), target = nodes.get(link.target)
  return `${source?.name || link.source}（${graphGender(source?.gender)}）${isDirected(link) ? ' → ' : ' — '}${target?.name || link.target}（${graphGender(target?.gender)}）：${relationLabel(link, source, target)}`
}

/** All members and all actual edges; never infer spouse/sibling edges or trim branches. */
export function buildGraphData(members: Member[], relationships: Relationship[], focusId?: string): GraphData {
  const ids = new Set(members.map(member => String(member.id)))
  const actual = relationships.filter(edge => ids.has(String(edge.fromMemberId)) && ids.has(String(edge.toMemberId)))
  const adjacency = new Map<string, { id: string; step: number }[]>()
  ids.forEach(id => adjacency.set(id, []))
  actual.forEach(edge => {
    const from = String(edge.fromMemberId), to = String(edge.toMemberId), step = edge.relationType === 'PARENT' ? 1 : 0
    adjacency.get(from)!.push({ id: to, step })
    adjacency.get(to)!.push({ id: from, step: -step })
  })
  // Relative layout ranks only. A visited set bounds cyclic/inconsistent historic data.
  const ranks = new Map<string, number>()
  for (const seed of ids) {
    if (ranks.has(seed)) continue
    const queue = [seed]
    ranks.set(seed, 0)
    for (let i = 0; i < queue.length; i++) {
      const current = queue[i]!
      for (const next of adjacency.get(current) || []) {
        if (ranks.has(next.id)) continue
        ranks.set(next.id, ranks.get(current)! + next.step)
        queue.push(next.id)
      }
    }
    const min = Math.min(...queue.map(id => ranks.get(id)!))
    queue.forEach(id => ranks.set(id, ranks.get(id)! - min + 1))
  }
  const nodes = members.map(member => ({
    id: String(member.id), name: member.name, gender: graphGender(member.gender), generation: ranks.get(String(member.id)) || 1,
    life: [member.birthDate, member.deathDate].filter(Boolean).join('—'),
  }))
  const byId = new Map(nodes.map(node => [node.id, node]))
  const links: GraphLink[] = actual.map(edge => {
    const link: GraphLink = { id: String(edge.id), source: String(edge.fromMemberId), target: String(edge.toMemberId), relationType: edge.relationType,
      kind: edge.relationType === 'PARENT' ? '亲子' : edge.relationType === 'SPOUSE' ? '配偶' : '兄弟姐妹' }
    link.label = relationLabel(link, byId.get(link.source), byId.get(link.target))
    return link
  })
  return { nodes, links, rootId: focusId && ids.has(focusId) ? focusId : nodes[0]?.id }
}

export interface GraphLayout { positions: Map<string, [number, number]>; width: number; height: number }
export const NODE_WIDTH = 168, NODE_HEIGHT = 76
const GAP_X = 232, GAP_Y = 156, PAD = 110
/** Collision-free node placement and content-derived bounds in all four layouts. */
export function layoutGraph(nodes: GraphNode[], mode: GraphMode): GraphLayout {
  const positions = new Map<string, [number, number]>()
  const groups = new Map<number, GraphNode[]>()
  nodes.forEach(node => { const group = groups.get(node.generation) || []; group.push(node); groups.set(node.generation, group) })
  const rows = [...groups.entries()].sort(([a], [b]) => a - b).map(([, row]) => row)
  let radius = 0
  rows.forEach((row, rank) => {
    if (mode === 'tree' || mode === 'hourglass') {
      row.forEach((node, i) => positions.set(node.id, [(i - (mode === 'hourglass' ? (row.length - 1) / 2 : 0)) * GAP_X, rank * GAP_Y]))
    } else {
      // Circumference/arc grows with row size, including the first generation.
      const sweep = mode === 'radial' ? 2 * Math.PI : Math.PI * .84
      radius = Math.max(radius + GAP_X, row.length * GAP_X / sweep)
      row.forEach((node, i) => {
        const angle = mode === 'radial' ? -Math.PI / 2 + i * sweep / row.length : -Math.PI / 2 - sweep / 2 + (i + .5) * sweep / row.length
        positions.set(node.id, [Math.cos(angle) * radius, Math.sin(angle) * radius])
      })
    }
  })
  const points = [...positions.values()]
  const minX = Math.min(0, ...points.map(p => p[0])), maxX = Math.max(0, ...points.map(p => p[0]))
  const minY = Math.min(0, ...points.map(p => p[1])), maxY = Math.max(0, ...points.map(p => p[1]))
  positions.forEach((point, id) => positions.set(id, [point[0] - minX + PAD, point[1] - minY + PAD]))
  return { positions, width: maxX - minX + PAD * 2, height: maxY - minY + PAD * 2 }
}

/** Clip straight edges at rectangular cards so arrowheads remain visible. */
export function graphEdge(from: [number, number], to: [number, number]) {
  const dx = to[0] - from[0], dy = to[1] - from[1]
  const ratio = Math.min(dx ? NODE_WIDTH / 2 / Math.abs(dx) : Infinity, dy ? NODE_HEIGHT / 2 / Math.abs(dy) : Infinity, .45)
  return { x1: from[0] + dx * ratio, y1: from[1] + dy * ratio, x2: to[0] - dx * ratio, y2: to[1] - dy * ratio }
}
