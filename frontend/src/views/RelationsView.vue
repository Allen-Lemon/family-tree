<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import MemberSelect from '@/components/MemberSelect.vue'
import { memberApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { requireTreeId } from '@/utils/backend'
import { notifyFamilyChanged } from '@/utils/data-changed'
import type { Member, Relationship, RelationType } from '@/types'

const route = useRoute(), app = useAppStore()
const memberId = ref<number | string>(''), targetId = ref<number | string>('')
const members = ref<Member[]>([]), relationships = ref<Relationship[]>([])
const loading = ref(false), saving = ref(false), removing = ref<number>()
const relationLabels: Record<RelationType, string> = { PARENT: '父母 → 子女', SPOUSE: '配偶', SIBLING: '兄弟姐妹' }
const form = reactive<{ type: RelationType }>({ type: 'PARENT' })
const memberMap = computed(() => new Map(members.value.map(m => [m.id, m])))
const targetMembers = computed(() => members.value.filter(m => m.id !== Number(memberId.value)))
const filteredRelations = computed(() => relationships.value.filter(r => !memberId.value || r.fromMemberId === Number(memberId.value) || r.toMemberId === Number(memberId.value)))
const relationHelp = computed(() => form.type === 'PARENT'
  ? '亲子方向：当前成员（父母） → 关联成员（子女）；会为当前成员的已有直接配偶同步关联该子女。'
  : form.type === 'SPOUSE'
    ? '配偶关系无方向；双方已有子女会互相关联为对方的子女，亲子方向为另一方 → 已有子女。'
    : '兄弟姐妹关系无方向；仅建立所选两位已有成员的兄弟姐妹关系。')
const selectionValid = computed(() => memberMap.value.has(Number(memberId.value)) && targetMembers.value.some(m => m.id === Number(targetId.value)))
const syncPreview = computed(() => {
  if (!selectionValid.value) return []
  const current = Number(memberId.value), target = Number(targetId.value)
  const pairs = new Map<string, { parent: number; child: number }>()
  function include(parent: number, child: number) {
    if (parent === child || !memberMap.value.has(parent) || !memberMap.value.has(child)) return
    if (relationships.value.some(r => r.relationType === 'PARENT' && r.fromMemberId === parent && r.toMemberId === child)) return
    pairs.set(`${parent}:${child}`, { parent, child })
  }
  for (const r of relationships.value) {
    if (form.type === 'PARENT' && r.relationType === 'SPOUSE') {
      const spouse = r.fromMemberId === current ? r.toMemberId : r.toMemberId === current ? r.fromMemberId : undefined
      if (spouse !== undefined && spouse !== current) include(spouse, target)
    }
    if (form.type === 'SPOUSE' && r.relationType === 'PARENT') {
      if (r.fromMemberId === current) include(target, r.toMemberId)
      if (r.fromMemberId === target) include(current, r.toMemberId)
    }
  }
  return [...pairs.values()]
})
function memberLabel(id: number) {
  const member = memberMap.value.get(id)
  return member ? `${member.name}${member.birthDate ? `（${member.birthDate}）` : ''}` : '成员已不可用'
}
let loadVersion = 0, treeVersion = 0
async function load() {
  const version = ++loadVersion, context = treeVersion
  loading.value = true
  try {
    const tree = requireTreeId(app.activeTreeId)
    const [people, links] = await Promise.all([memberApi.list(tree), memberApi.relationships(tree)])
    if (version !== loadVersion || context !== treeVersion) return
    members.value = people.filter(m => String(m.treeId) === String(tree) && !m.deleted)
    relationships.value = links.filter(r => String(r.treeId) === String(tree))
    if (!memberMap.value.has(Number(memberId.value))) memberId.value = ''
    if (!targetMembers.value.some(m => m.id === Number(targetId.value))) targetId.value = ''
  } catch {
    if (version === loadVersion && context === treeVersion) { members.value = []; relationships.value = []; ElMessage.error('关系数据加载失败，请选择家谱后重试') }
  } finally { if (version === loadVersion && context === treeVersion) loading.value = false }
}
async function save() {
  if (saving.value || loading.value) return
  const current = Number(memberId.value), target = Number(targetId.value)
  if (!memberMap.value.has(current)) return ElMessage.warning('请按姓名选择当前成员')
  if (!selectionValid.value) return ElMessage.warning('请按姓名选择另一位关联成员')
  const tree = requireTreeId(app.activeTreeId), context = treeVersion
  saving.value = true
  try {
    await memberApi.addRelation(tree, { fromMemberId: current, toMemberId: target, type: form.type })
    notifyFamilyChanged(tree)
    if (context !== treeVersion) return
    ElMessage.success('关系已保存并同步')
    targetId.value = ''
    await load()
  } finally { saving.value = false }
}
async function remove(relation: Relationship) {
  if (removing.value !== undefined || loading.value) return
  const tree = requireTreeId(app.activeTreeId), context = treeVersion
  removing.value = relation.id
  try {
    try {
      await ElMessageBox.confirm(`确定解除“${memberLabel(relation.fromMemberId)}”与“${memberLabel(relation.toMemberId)}”的${relationLabels[relation.relationType]}关系吗？仅删除此条关系，不删除成员，也不自动解除其他关系。`, '解除关系', { type: 'warning', confirmButtonText: '确认解除', cancelButtonText: '取消' })
    } catch { return }
    if (context !== treeVersion) return
    await memberApi.removeRelation(tree, relation.id)
    notifyFamilyChanged(tree)
    if (context !== treeVersion) return
    ElMessage.success('关系已解除，成员资料保留')
    await load()
  } finally { removing.value = undefined }
}
watch(memberId, () => { if (Number(memberId.value) === Number(targetId.value)) targetId.value = '' })
watch(() => app.activeTreeId, () => { treeVersion++; memberId.value = ''; targetId.value = ''; members.value = []; relationships.value = []; void load() }, { immediate: true, flush: 'sync' })
watch(() => route.query.memberId, value => { if (value && Number.isFinite(Number(value))) memberId.value = Number(value) }, { immediate: true })
onBeforeUnmount(() => { treeVersion++; loadVersion++ })
</script>

<template>
  <PageHeader title="关系编辑" description="仅选择已有成员，建立或解除亲子、配偶及兄弟姐妹关系；保存不会新增成员" />
  <div v-loading="loading" class="relation-layout">
    <section class="panel">
      <div class="panel-header"><h2>选择当前成员与关系</h2></div>
      <div class="panel-body">
        <el-form label-position="top">
          <el-form-item label="当前成员姓名">
            <MemberSelect v-model="memberId" :tree-id="app.activeTreeId" :members="members" :disabled="saving || loading" placeholder="搜索当前成员姓名" />
          </el-form-item>
          <el-alert :title="relationHelp" type="info" :closable="false" />
          <el-form-item label="关系类型" style="margin-top:18px">
            <el-radio-group v-model="form.type" :disabled="saving"><el-radio-button v-for="(label, type) in relationLabels" :key="type" :value="type">{{ label }}</el-radio-button></el-radio-group>
          </el-form-item>
        </el-form>
      </div>
    </section>
    <section class="panel">
      <div class="panel-header"><h2>选择关联成员</h2></div>
      <div class="panel-body">
        <el-form label-position="top">
          <el-form-item label="关联成员姓名"><MemberSelect v-model="targetId" :tree-id="app.activeTreeId" :members="targetMembers" :disabled="saving || loading" placeholder="搜索关联成员姓名" /></el-form-item>
        </el-form>
        <div class="sync-preview" aria-live="polite">
          <h3>本次自动同步预览</h3>
          <p>仅预览直接配偶或双方已有子女，不递归扩展；已存在的亲子关系不重复列出。保存只关联已有成员，不新增成员。</p>
          <p v-if="!selectionValid">请选择当前成员与关联成员以查看预览。</p>
          <template v-else>
            <p v-if="form.type === 'PARENT'">所选亲子方向：{{ memberLabel(Number(memberId)) }}（父母） → {{ memberLabel(Number(targetId)) }}（子女）</p>
            <ul v-if="syncPreview.length">
              <li v-for="pair in syncPreview" :key="`${pair.parent}:${pair.child}`">{{ memberLabel(pair.parent) }}（父母） → {{ memberLabel(pair.child) }}（子女）</li>
            </ul>
            <p v-else>暂无需要自动补充的亲子关系。</p>
          </template>
        </div>
        <el-button type="primary" size="large" :loading="saving" :disabled="loading || saving" style="width:100%;margin-top:16px" @click="save">确认保存</el-button>
      </div>
    </section>
  </div>
  <section v-loading="loading" class="panel relation-list">
    <div class="panel-header"><h2>现有关系{{ memberId ? '（当前成员）' : '（全部成员）' }}</h2><div><el-button v-if="memberId" :disabled="saving" @click="memberId = ''">显示全部</el-button><el-button @click="load">刷新</el-button></div></div>
    <el-alert title="解除关系仅移除所选连线，成员及其他关系保留；亲子解除会影响祖先、后代查询和图谱。" type="info" :closable="false" />
    <el-table :data="filteredRelations" row-key="id" stripe>
      <el-table-column label="成员姓名"><template #default="{ row }">{{ memberLabel(row.fromMemberId) }}</template></el-table-column>
      <el-table-column label="关系类型" width="160"><template #default="{ row }">{{ relationLabels[row.relationType as RelationType] }}</template></el-table-column>
      <el-table-column label="关联成员姓名"><template #default="{ row }">{{ memberLabel(row.toMemberId) }}</template></el-table-column>
      <el-table-column label="操作" width="130"><template #default="{ row }"><el-button v-permission="'relation:edit'" link type="danger" :loading="removing === row.id" :disabled="loading || removing !== undefined" @click="remove(row)">解除关系</el-button></template></el-table-column>
      <template #empty>暂无关系，可按姓名选择成员后建立关系</template>
    </el-table>
  </section>
</template>

<style scoped>
.relation-layout{display:grid;grid-template-columns:1fr 1fr;gap:18px}.relation-list{margin-top:18px}.sync-preview{line-height:1.7}.sync-preview h3{font-size:15px}.sync-preview p{color:var(--el-text-color-secondary)}@media(max-width:800px){.relation-layout{grid-template-columns:1fr}}
</style>
