<script setup lang="ts">
import * as d3 from 'd3'
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import type { GraphData, GraphLink, GraphNode } from '@/types'
import { genderSymbol, graphEdge, graphGender, isDirected, layoutGraph, linkDescription, nodeDescription, relationLabel, type GraphMode } from '@/utils/graph'
import { buildRelationshipIndex, directRelationshipText, findRelationshipPaths, relationshipPathText, relationshipStatus } from '@/utils/relationships'

const props = defineProps<{ data: GraphData; mode: GraphMode; focusId?: string }>()
const emit = defineEmits<{ select: [node: GraphNode] }>()
const svg = ref<SVGSVGElement>(), view = ref('graph'), monochrome = ref(false), detail = ref('')
const markerId = `graph-arrow-${useId().replace(/[^a-zA-Z0-9-]/g, '')}`
const layout = computed(() => layoutGraph(props.data.nodes, props.mode))
const nodes = computed(() => new Map(props.data.nodes.map(node => [node.id, node])))
const relationshipSearch = ref(''), referenceId = ref('')
const relationshipIndex = computed(() => buildRelationshipIndex(props.data))
// Keep this separate from filtering: changing the name query never repeats BFS.
const referencePaths = computed(() => referenceId.value && relationshipIndex.value.members.has(referenceId.value)
  ? findRelationshipPaths(relationshipIndex.value, referenceId.value) : undefined)
const relationshipRows = computed(() => {
  const index = relationshipIndex.value, paths = referencePaths.value
  return [...index.members.values()].map(member => ({
    ...member,
    parentsText: directRelationshipText(index, member.parents),
    childrenText: directRelationshipText(index, member.children),
    spousesText: directRelationshipText(index, member.spouses),
    siblingsText: directRelationshipText(index, member.siblings),
    status: paths ? relationshipStatus(paths, member.node.id) : '',
    path: paths ? relationshipPathText(index, paths, member.node.id) : '',
  }))
})
const filteredRelationshipRows = computed(() => {
  const query = relationshipSearch.value.trim().toLocaleLowerCase()
  return relationshipRows.value.filter(row => row.node.name.toLocaleLowerCase().includes(query))
})
watch(nodes, current => { if (referenceId.value && !current.has(referenceId.value)) referenceId.value = '' })
const geometryKey = computed(() => `${props.mode}:${props.data.nodes.map(node => `${node.id}:${node.generation}`).join('|')}`)
const transform = ref(''), scale = ref(1)
let zoom: d3.ZoomBehavior<SVGSVGElement, unknown> | undefined
const edges = computed(() => props.data.links.flatMap((link, index) => {
  const from = layout.value.positions.get(link.source), to = layout.value.positions.get(link.target)
  return from && to ? [{ link, key: `${link.id || index}`, ...graphEdge(from, to) }] : []
}))
function label(link: GraphLink) { return relationLabel(link, nodes.value.get(link.source), nodes.value.get(link.target)) }
function describe(link: GraphLink) { return linkDescription(link, nodes.value) }
function nodeDetails(node: GraphNode) {
  const relations = props.data.links.filter(link => link.source === node.id || link.target === node.id).map(describe)
  return `${nodeDescription(node)}。${relations.length ? relations.join('；') : '暂无已记录关系'}`
}
function genderClass(node: GraphNode) {
  return { 男: 'male', 女: 'female', 其他: 'other', 未标注: 'unknown' }[graphGender(node.gender)]
}
function fit() {
  if (!svg.value || !zoom) return
  const factor = Math.min(1.25, 960 / layout.value.width, 580 / layout.value.height)
  d3.select(svg.value).call(zoom.transform, d3.zoomIdentity.translate((1000 - layout.value.width * factor) / 2, (640 - layout.value.height * factor) / 2).scale(factor))
}
function locate() {
  const point = layout.value.positions.get(props.focusId || '')
  if (!point || !svg.value || !zoom) return
  const factor = Math.max(.8, scale.value)
  d3.select(svg.value).call(zoom.transform, d3.zoomIdentity.translate(500 - point[0] * factor, 320 - point[1] * factor).scale(factor))
}
function magnify(factor: number) { if (svg.value && zoom) d3.select(svg.value).call(zoom.scaleBy, factor) }
function keyboard(event: KeyboardEvent) {
  if (event.target !== svg.value) return
  if (event.key === '+' || event.key === '=') magnify(1.3)
  else if (event.key === '-') magnify(1 / 1.3)
  else if (event.key === 'Home') fit()
  else {
    const delta: Record<string, [number, number]> = { ArrowLeft: [60, 0], ArrowRight: [-60, 0], ArrowUp: [0, 60], ArrowDown: [0, -60] }
    const move = delta[event.key]
    if (!move || !svg.value || !zoom) return
    d3.select(svg.value).call(zoom.translateBy, move[0] / scale.value, move[1] / scale.value)
  }
  event.preventDefault()
}
onMounted(() => {
  if (!svg.value) return
  zoom = d3.zoom<SVGSVGElement, unknown>().extent([[0, 0], [1000, 640]]).scaleExtent([.0001, 5]).on('zoom', event => {
    transform.value = event.transform.toString(); scale.value = event.transform.k
  })
  d3.select(svg.value).call(zoom).on('dblclick.zoom', null)
  fit()
  if (props.focusId) locate()
})
watch(geometryKey, fit, { flush: 'post' })
watch(() => props.focusId, locate, { flush: 'post' })
onBeforeUnmount(() => { if (svg.value) d3.select(svg.value).on('.zoom', null) })
defineExpose({ reset: fit })
</script>

<template>
  <div class="family-graph" :class="{ monochrome }">
    <div class="graph-controls no-print">
      <el-radio-group v-model="view" aria-label="图谱展示方式"><el-radio-button value="graph">关系图</el-radio-button><el-radio-button value="table">成员 / 关系表格</el-radio-button><el-radio-button value="relationships">所有人关系情况</el-radio-button></el-radio-group>
      <el-button @click="fit">适合全图</el-button><el-button @click="locate" :disabled="!focusId">定位焦点</el-button>
      <el-button aria-label="放大图谱" @click="magnify(1.3)">放大 +</el-button><el-button aria-label="缩小图谱" @click="magnify(1 / 1.3)">缩小 −</el-button>
      <el-checkbox v-model="monochrome">高辨识 / 黑白模式</el-checkbox>
    </div>
    <div class="graph-legend" aria-label="图例">
      <span class="legend-item"><b class="gender-icon male">□</b> 男</span><span class="legend-item"><b class="gender-icon female">○</b> 女</span><span class="legend-item"><b class="gender-icon other">◇</b> 其他</span><span class="legend-item"><b class="gender-icon unknown">?</b> 未标注</span>
      <span>实线箭头：父母 → 子女</span><span>长虚线：配偶（无向）</span><span>点线：兄弟姐妹（无向）</span>
    </div>
    <p class="graph-help">显示全部 {{ data.nodes.length }} 位成员、{{ data.links.length }} 条真实关系（含不连通成员），无深度筛选。焦点只定位 / 高亮，不隐藏旁支。布局代序仅用于排布，不代表已确认的族谱辈分。</p>
    <div v-show="view === 'graph'" class="plot-wrap">
      <p class="graph-help no-print">滚轮缩放、拖动平移；Tab 访问节点 / 关系，Enter 或空格查看成员。画布聚焦后用方向键平移、+/− 缩放、Home 适合全图。</p>
      <svg ref="svg" class="family-svg" viewBox="0 0 1000 640" tabindex="0" role="group" aria-label="全部成员家谱关系图" @keydown="keyboard">
        <title>家谱关系图：全部成员与真实关系；箭头由父母指向子女</title>
        <defs><marker :id="markerId" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" class="arrow-head" /></marker></defs>
        <g :transform="transform">
          <g v-for="edge in edges" :key="edge.key" class="graph-link" :data-link-id="edge.link.id" :data-source="edge.link.source" :data-target="edge.link.target" :data-relation-type="edge.link.relationType" tabindex="0" role="img" :aria-label="describe(edge.link)" @mouseenter="detail = describe(edge.link)" @mouseleave="detail = ''" @focus="detail = describe(edge.link)" @blur="detail = ''">
            <title>{{ describe(edge.link) }}</title>
            <line v-bind="{ x1: edge.x1, y1: edge.y1, x2: edge.x2, y2: edge.y2 }" class="edge-hit" />
            <line v-bind="{ x1: edge.x1, y1: edge.y1, x2: edge.x2, y2: edge.y2 }" class="edge-line" :class="{ spouse: edge.link.kind === '配偶', sibling: edge.link.kind === '兄弟姐妹' }" :marker-end="isDirected(edge.link) ? `url(#${markerId})` : undefined" />
            <text :x="(edge.x1 + edge.x2) / 2" :y="(edge.y1 + edge.y2) / 2 - 8" class="edge-label" text-anchor="middle">{{ label(edge.link) }}</text>
          </g>
          <g v-for="node in data.nodes" :key="node.id" class="graph-node" :data-node-id="node.id" :data-gender="graphGender(node.gender)" :class="[genderClass(node), { selected: node.id === focusId }]" :transform="`translate(${layout.positions.get(node.id)?.join(',')})`" tabindex="0" role="button" :aria-label="nodeDetails(node)" @click="emit('select', node)" @keydown.enter.stop.prevent="emit('select', node)" @keydown.space.stop.prevent="emit('select', node)" @mouseenter="detail = nodeDetails(node)" @mouseleave="detail = ''" @focus="detail = nodeDetails(node)" @blur="detail = ''">
            <title>{{ nodeDetails(node) }}</title>
            <rect class="node-card" x="-84" y="-38" width="168" height="76" rx="8" />
            <rect v-if="graphGender(node.gender) === '男'" class="gender-mark" x="-72" y="-25" width="14" height="14" />
            <circle v-else-if="graphGender(node.gender) === '女'" class="gender-mark" cx="-65" cy="-18" r="8" />
            <path v-else-if="graphGender(node.gender) === '其他'" class="gender-mark" d="M-65,-27 -56,-18 -65,-9 -74,-18Z" />
            <text v-else x="-65" y="-12" text-anchor="middle" class="unknown-mark">?</text>
            <text x="-49" y="-12" class="node-name">{{ Array.from(node.name).length > 8 ? Array.from(node.name).slice(0, 7).join('') + '…' : node.name }}</text>
            <text x="0" y="9" text-anchor="middle" class="node-meta">{{ graphGender(node.gender) }} · 布局代序 {{ node.generation }}</text>
            <text x="0" y="27" text-anchor="middle" class="node-meta">{{ (node.life || '生卒未标注').slice(0, 24) }}</text>
          </g>
        </g>
      </svg>
      <p class="graph-detail" role="status" aria-live="polite">{{ detail || '悬停或键盘聚焦成员 / 关系可读完整说明；全部信息也可在表格中查看。' }}</p>
    </div>
    <div v-show="view === 'table'" class="graph-tables">
      <h3>全部成员（{{ data.nodes.length }}）</h3>
      <div class="table-scroll"><table><caption>成员列表，包含无连接关系的成员</caption><thead><tr><th scope="col">姓名</th><th scope="col">性别</th><th scope="col">布局代序</th><th scope="col">生卒</th><th scope="col">操作</th></tr></thead><tbody><tr v-for="node in data.nodes" :key="node.id"><td>{{ genderSymbol(node.gender) }} {{ node.name }}</td><td>{{ graphGender(node.gender) }}</td><td>{{ node.generation }}</td><td>{{ node.life || '未标注' }}</td><td><el-button link @click="emit('select', node)">查看成员</el-button></td></tr></tbody></table></div>
      <h3>全部真实关系（{{ data.links.length }}）</h3>
      <div class="table-scroll"><table><caption>亲子由父母指向子女；配偶及兄弟姐妹没有方向。不推断未记录的关系。</caption><thead><tr><th scope="col">成员一（性别）</th><th scope="col">关系 / 方向</th><th scope="col">成员二（性别）</th></tr></thead><tbody><tr v-for="(link, index) in data.links" :key="link.id || index"><td>{{ nodes.get(link.source)?.name }}（{{ graphGender(nodes.get(link.source)?.gender) }}）</td><td>{{ label(link) }}{{ isDirected(link) ? '' : '（无向）' }}</td><td>{{ nodes.get(link.target)?.name }}（{{ graphGender(nodes.get(link.target)?.gender) }}）</td></tr><tr v-if="!data.links.length"><td colspan="3">暂无已记录关系</td></tr></tbody></table></div>
    </div>
    <section v-if="view === 'relationships'" class="graph-tables relationship-summary" aria-label="所有人关系情况">
      <h3>所有人关系情况（{{ filteredRelationshipRows.length }} / {{ data.nodes.length }}）</h3>
      <div class="relationship-filters no-print">
        <label>按姓名筛选 <input v-model="relationshipSearch" type="search" aria-label="按姓名筛选所有人关系" placeholder="输入成员姓名" /></label>
        <label>参照成员
          <select v-model="referenceId" aria-label="选择参照成员姓名">
            <option value="">不选择（仅查看直接关系汇总）</option>
            <option v-for="node in data.nodes" :key="node.id" :value="node.id">{{ node.name }}（{{ graphGender(node.gender) }}，ID {{ node.id }}）</option>
          </select>
        </label>
      </div>
      <p class="relationship-note">列出全部成员（含零关系成员）的已记录直接父母、子女、配偶与手足；不根据共同父母推断手足。选择参照成员后，展示其到每位成员的一条最短记录路径。每步称谓指箭头目标相对于起点的关系；间接路径仅表示记录相连，不推断或确认叔伯等具体亲属称谓。“未记录关系”不表示不存在亲属关系。</p>
      <div class="table-scroll relationship-scroll" tabindex="0" role="region" aria-label="所有人关系情况列表，可滚动">
        <table>
          <caption>所有人关系情况{{ referencePaths ? `：以 ${nodes.get(referenceId)?.name} 为参照` : '：直接关系汇总' }}</caption>
          <thead><tr><th scope="col">姓名</th><th scope="col">性别</th><th scope="col">直接父母</th><th scope="col">直接子女</th><th scope="col">直接配偶</th><th scope="col">直接手足</th><th v-if="referencePaths" scope="col">与参照成员的关系</th><th v-if="referencePaths" scope="col">已记录关系路径</th><th scope="col">操作</th></tr></thead>
          <tbody>
            <tr v-for="row in filteredRelationshipRows" :key="row.node.id" :data-member-id="row.node.id">
              <th scope="row">{{ row.node.name }}</th><td>{{ graphGender(row.node.gender) }}</td><td>{{ row.parentsText }}</td><td>{{ row.childrenText }}</td><td>{{ row.spousesText }}</td><td>{{ row.siblingsText }}</td>
              <td v-if="referencePaths">{{ row.status }}</td><td v-if="referencePaths" class="relationship-path">{{ row.path }}</td>
              <td><el-button link @click="emit('select', row.node)">查看成员</el-button></td>
            </tr>
            <tr v-if="!filteredRelationshipRows.length"><td :colspan="referencePaths ? 9 : 7">{{ data.nodes.length ? '没有匹配姓名的成员' : '暂无成员' }}</td></tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.family-graph{--surface:#fcfcfb;--ink:#0b0b0b;--secondary:#52514e;--rule:#c3c2b7;--male:#2a78d6;--female:#eb6834;--other:#1baf7a;color-scheme:light;background:var(--surface);color:var(--ink);border-radius:0 0 12px 12px}
.graph-controls,.graph-legend{display:flex;align-items:center;flex-wrap:wrap;gap:12px;padding:12px 18px;border-bottom:1px solid var(--rule)}
.graph-legend,.graph-help,.graph-detail{font-size:13px;color:var(--secondary)}.graph-help{padding:8px 18px;margin:0;line-height:1.7}.legend-item{display:inline-flex;align-items:center;gap:5px}.gender-icon{border:2px solid var(--category);color:var(--ink);width:22px;text-align:center}.male{--category:var(--male)}.female{--category:var(--female)}.other{--category:var(--other)}.unknown{--category:var(--secondary)}
.family-svg{display:block;width:100%;height:auto;min-height:360px;max-height:720px;touch-action:none}.family-svg:focus-visible{outline:3px solid var(--ink);outline-offset:-3px}.edge-line{stroke:var(--secondary);stroke-width:2;fill:none}.arrow-head{fill:var(--secondary)}.edge-hit{stroke:transparent;stroke-width:24;pointer-events:stroke}.spouse{stroke-dasharray:10 5}.sibling{stroke-dasharray:2 5}.edge-label{font-size:12px;fill:var(--ink);paint-order:stroke;stroke:var(--surface);stroke-width:5;stroke-linejoin:round;pointer-events:none}.graph-link:focus .edge-line,.graph-link:hover .edge-line{stroke:var(--ink);stroke-width:4}.graph-link:focus{outline:none}.graph-node{cursor:pointer}.node-card{fill:var(--surface);stroke:var(--rule);stroke-width:1.5}.gender-mark{fill:var(--category);stroke:var(--surface);stroke-width:2}.node-name{font-size:14px;font-weight:700;fill:var(--ink)}.node-meta{font-size:11px;fill:var(--secondary)}.unknown-mark{fill:var(--ink);font-weight:700;font-size:20px}.graph-node.selected .node-card,.graph-node:focus .node-card,.graph-node:hover .node-card{stroke:var(--ink);stroke-width:3}.graph-node.selected .node-card{stroke-dasharray:6 3}.graph-node:focus{outline:none}.graph-detail{margin:0;min-height:48px;padding:12px 18px;border-top:1px solid var(--rule);line-height:1.6;overflow-wrap:anywhere}.graph-tables{padding:14px 18px}.table-scroll{overflow-x:auto}table{width:100%;border-collapse:collapse;font-size:14px;text-align:left}caption{text-align:left;color:var(--secondary);padding:8px 0}th,td{padding:10px;border-bottom:1px solid var(--rule);overflow-wrap:anywhere}th{font-weight:600}.monochrome{--male:var(--ink);--female:var(--ink);--other:var(--ink)}
.relationship-filters{display:flex;align-items:center;flex-wrap:wrap;gap:12px}.relationship-filters label{display:flex;align-items:center;flex-wrap:wrap;gap:8px}.relationship-filters input,.relationship-filters select{font:inherit;max-width:100%;min-height:36px;border:1px solid var(--rule);border-radius:4px;padding:4px 8px;background:var(--surface);color:var(--ink)}.relationship-filters select{max-width:min(100%,360px)}.relationship-note{font-size:13px;color:var(--secondary);line-height:1.7}.relationship-scroll{max-height:560px;overflow:auto}.relationship-scroll table{min-width:920px}.relationship-scroll thead th{position:sticky;top:0;background:var(--surface);z-index:1}.relationship-path{min-width:240px}.relationship-filters input:focus-visible,.relationship-filters select:focus-visible,.relationship-scroll:focus-visible{outline:2px solid var(--ink);outline-offset:2px}
@media(prefers-color-scheme:dark){:global(:root:not([data-theme="light"])) .family-graph{--surface:#1a1a19;--ink:#f0efec;--secondary:#c3c2b7;--rule:#383835;--male:#3987e5;--female:#d95926;--other:#199e70;color-scheme:dark}}
:global(:root[data-theme="dark"]) .family-graph{--surface:#1a1a19;--ink:#f0efec;--secondary:#c3c2b7;--rule:#383835;--male:#3987e5;--female:#d95926;--other:#199e70;color-scheme:dark}
@media print,(forced-colors:active){.family-graph{--male:currentColor;--female:currentColor;--other:currentColor}.gender-mark{fill:none;stroke:var(--ink)}.no-print{display:none!important}.family-svg{min-height:0;max-height:none}}
</style>
