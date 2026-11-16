<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import FamilyGraph from '@/components/FamilyGraph.vue'
import { memberApi, treeApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { FamilyTree, GraphData, GraphNode } from '@/types'
import { buildGraphData, graphGender, type GraphMode } from '@/utils/graph'

const route = useRoute(), app = useAppStore()
const mode = ref<GraphMode>('tree'), loading = ref(false), drawerOpen = ref(false), selected = ref<GraphNode>()
const data = ref<GraphData>({ nodes: [], links: [] }), trees = ref<FamilyTree[]>([]), treesLoading = ref(false)
const treeId = ref(String(route.query.treeId || app.activeTreeId || '')), rootId = ref(String(route.query.focus || ''))
const error = ref(''), updatedAt = ref('')
const focusId = computed(() => data.value.nodes.some(node => node.id === rootId.value) ? rootId.value : '')
let initialized = false, disposed = false, version = 0, inFlight = false, pending = false
let timer: number | undefined

function responseStatus(reason: unknown): number | undefined {
  if (!reason || typeof reason !== 'object' || !('response' in reason)) return undefined
  const response = reason.response
  return response && typeof response === 'object' && 'status' in response && typeof response.status === 'number' ? response.status : undefined
}
function accessDenied(reason: unknown) { return [401, 403, 404].includes(responseStatus(reason) || 0) }

/** Single-flight queue: changes during a fetch trigger one follow-up read, never overlapping reads. */
async function refresh() {
  if (disposed || !initialized) return
  pending = true
  if (inFlight) return
  inFlight = true
  try {
    while (pending && !disposed) {
      pending = false
      const currentVersion = version, currentTree = treeId.value
      if (!currentTree) { data.value = { nodes: [], links: [] }; loading.value = false; continue }
      loading.value = true
      try {
        // Wait for both reads even when one fails, so follow-up refreshes cannot overlap.
        const [members, relationships] = await Promise.allSettled([memberApi.list(currentTree), memberApi.relationships(currentTree)])
        if (disposed || currentVersion !== version || currentTree !== treeId.value) continue
        const failures = [members, relationships].flatMap(result => result.status === 'rejected' ? [result.reason as unknown] : [])
        // Preserve response status, giving authorization/deletion failures priority over transient failures.
        if (failures.length) throw failures.find(accessDenied) || failures[0]
        if (members.status !== 'fulfilled' || relationships.status !== 'fulfilled') continue
        data.value = buildGraphData(members.value, relationships.value, rootId.value)
        if (selected.value) {
          selected.value = data.value.nodes.find(node => node.id === selected.value?.id)
          if (!selected.value) drawerOpen.value = false
        }
        error.value = ''
        updatedAt.value = new Date().toLocaleTimeString()
      } catch (reason) {
        if (!disposed && currentVersion === version) {
          const status = responseStatus(reason)
          if (accessDenied(reason)) {
            data.value = { nodes: [], links: [] }; selected.value = undefined; drawerOpen.value = false
            rootId.value = ''; updatedAt.value = ''; trees.value = []
            initialized = false; pending = false
            error.value = '当前家谱已无访问权限、登录已失效或家谱已删除。已清除图谱；点击刷新重新读取可访问的家谱。'
          } else if (status === undefined || status >= 500) {
            error.value = '图谱刷新失败。保留上次数据，请稍后重试；可见成员与关系以服务器授权为准。'
          } else {
            data.value = { nodes: [], links: [] }; selected.value = undefined; drawerOpen.value = false; updatedAt.value = ''
            error.value = '图谱读取失败，请检查所选家谱后重试。'
          }
        }
      } finally {
        if (!disposed && currentVersion === version) loading.value = false
      }
    }
  } finally { inFlight = false }
}
function switchTree() {
  version++
  rootId.value = ''; selected.value = undefined; drawerOpen.value = false
  data.value = { nodes: [], links: [] }; error.value = ''; updatedAt.value = ''
  if (treeId.value !== String(app.activeTreeId || '')) app.setTree(treeId.value)
  void refresh()
}
async function initialize() {
  treesLoading.value = true
  try {
    const available = await treeApi.list()
    if (disposed) return
    trees.value = available
    if (!available.some(tree => String(tree.id) === treeId.value)) treeId.value = available[0] ? String(available[0].id) : ''
    initialized = true
    // AppLayout may remount on this change. The old instance is invalidated on unmount;
    // the new instance always initializes from the selected tree and reads fresh data.
    if (treeId.value !== String(app.activeTreeId || '')) app.setTree(treeId.value)
    await refresh()
  } catch {
    if (!disposed) error.value = '无法读取可访问的家谱，请重试。'
  } finally { if (!disposed) treesLoading.value = false }
}
function selectNode(node: GraphNode) { rootId.value = node.id; selected.value = node; drawerOpen.value = true }
function printGraph() { window.print() }
function dataChanged(event: Event) {
  const detail = (event as CustomEvent<{ treeId?: string | number }>).detail
  if (detail && String(detail.treeId) === treeId.value) void refresh()
}
function storageChanged(event: StorageEvent) {
  if (event.key !== 'family-tree-data-changed' || !event.newValue) return
  try {
    const detail: unknown = JSON.parse(event.newValue)
    if (detail && typeof detail === 'object' && 'treeId' in detail && String(detail.treeId) === treeId.value) void refresh()
  } catch { /* Ignore unrelated or malformed storage payloads. */ }
}
function resumed() { if (document.visibilityState === 'visible') void refresh() }
watch(treeId, () => { if (initialized) switchTree() }, { flush: 'sync' })
watch(() => app.activeTreeId, id => {
  const next = String(id || '')
  if (initialized && next !== treeId.value) treeId.value = next
})
watch(() => route.query.treeId, id => {
  if (id && String(id) !== treeId.value && trees.value.some(tree => String(tree.id) === String(id))) treeId.value = String(id)
})
watch(() => route.query.focus, id => { rootId.value = String(id || '') })
onMounted(() => {
  window.addEventListener('family-tree-data-changed', dataChanged)
  window.addEventListener('storage', storageChanged)
  window.addEventListener('focus', resumed)
  document.addEventListener('visibilitychange', resumed)
  timer = window.setInterval(resumed, 15_000)
  void initialize()
})
onBeforeUnmount(() => {
  disposed = true; version++; pending = false
  if (timer !== undefined) window.clearInterval(timer)
  window.removeEventListener('family-tree-data-changed', dataChanged)
  window.removeEventListener('storage', storageChanged)
  window.removeEventListener('focus', resumed)
  document.removeEventListener('visibilitychange', resumed)
})
</script>

<template>
  <PageHeader title="家谱图谱" description="完整展示家谱成员与真实关系；性别以形状、颜色和文字共同区分"><el-button class="no-print" @click="printGraph">打印图谱</el-button></PageHeader>
  <section class="panel">
    <div class="graph-toolbar no-print">
      <el-select v-model="treeId" filterable placeholder="请选择家谱" aria-label="家谱" :loading="treesLoading" style="width:220px"><el-option v-for="tree in trees" :key="tree.id" :value="String(tree.id)" :label="trees.filter(item => item.name === tree.name).length > 1 ? `${tree.name} (#${tree.id})` : tree.name" /></el-select>
      <el-segmented v-model="mode" aria-label="图谱布局" :options="[{label:'树状',value:'tree'},{label:'环形',value:'radial'},{label:'扇形',value:'fan'},{label:'沙漏',value:'hourglass'}]" />
      <el-button :loading="loading || treesLoading" @click="initialized ? refresh() : initialize()">刷新</el-button>
      <span class="muted" role="status">{{ loading ? '正在更新全部成员和关系…' : updatedAt ? `更新于 ${updatedAt}` : '默认显示全部' }}</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div :aria-busy="loading" :class="{ refreshing: loading && data.nodes.length > 0 }">
      <FamilyGraph v-if="data.nodes.length" :data="data" :mode="mode" :focus-id="focusId" @select="selectNode" />
      <el-empty v-else :description="loading || treesLoading ? '正在读取全部成员和关系…' : '请选择家谱；所选家谱暂无成员时不显示图谱'" />
    </div>
  </section>
  <el-drawer v-model="drawerOpen" title="成员速览" size="360px">
    <template v-if="selected"><el-descriptions :column="1" border><el-descriptions-item label="姓名">{{ selected.name }}</el-descriptions-item><el-descriptions-item label="性别">{{ graphGender(selected.gender) }}</el-descriptions-item><el-descriptions-item label="布局代序">{{ selected.generation }}（非确认辈分）</el-descriptions-item><el-descriptions-item label="生卒">{{ selected.life || '未标注' }}</el-descriptions-item></el-descriptions><el-button type="primary" style="width:100%;margin-top:18px" @click="$router.push(`/members/${selected.id}`)">查看完整资料</el-button></template>
  </el-drawer>
</template>

<style scoped>
.graph-toolbar{display:flex;align-items:center;gap:14px;flex-wrap:wrap;padding:14px 18px;border-bottom:1px solid #e2e8f0}.refreshing{opacity:.7;transition:opacity .15s}
</style>
