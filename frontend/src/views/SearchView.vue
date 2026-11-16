<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import MemberSelect from '@/components/MemberSelect.vue'
import { featureApi, memberApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { genderLabel, requireTreeId } from '@/utils/backend'
import type { Gender, Member, PathNode } from '@/types'

type SearchType = 'name' | 'ancestors' | 'descendants' | 'generation' | 'relation'
const app = useAppStore()
const type = ref<SearchType>('name')
const loading = ref(false), membersLoading = ref(false), searched = ref(false)
const rows = ref<Member[]>([]), members = ref<Member[]>([]), path = ref<PathNode[]>([])
const form = reactive({ q: '', memberId: '' as number | '', otherMemberId: '' as number | '', generation: 1 })
const types: { value: SearchType; label: string }[] = [{value:'name',label:'姓名/职业/出生地'},{value:'ancestors',label:'祖先查询'},{value:'descendants',label:'后代查询'},{value:'generation',label:'按后代代数'},{value:'relation',label:'两人关系路径'}]
const memberNames = computed(() => new Map(members.value.map(member => [member.id, member.name])))
const relationLabels: Record<string, string> = { START: '起始成员', PARENT: '父母', CHILD: '子女', SPOUSE: '配偶', SIBLING: '兄弟姐妹' }
let memberRequest = 0, searchRequest = 0
watch(() => app.activeTreeId, async tree => {
  const current = ++memberRequest
  searchRequest++
  members.value = []; rows.value = []; path.value = []
  form.memberId = ''; form.otherMemberId = ''; searched.value = false; loading.value = false
  membersLoading.value = false
  if (!tree) return
  membersLoading.value = true
  try {
    const result = await memberApi.list(tree)
    if (current === memberRequest) members.value = result
  } catch {
    if (current === memberRequest) ElMessage.error('成员加载失败，请切换家谱或刷新后重试')
  } finally {
    if (current === memberRequest) membersLoading.value = false
  }
}, { immediate: true })
watch(type, () => { searchRequest++; rows.value = []; path.value = []; searched.value = false; loading.value = false })
async function run() {
  if (!app.activeTreeId) { ElMessage.warning('请先选择家谱'); return }
  const searchType = type.value, from = form.memberId, to = form.otherMemberId
  if (searchType !== 'name' && !members.value.some(member => member.id === from)) {
    ElMessage.warning('请选择起始成员'); return
  }
  if (searchType === 'relation' && !members.value.some(member => member.id === to)) {
    ElMessage.warning('请选择目标成员'); return
  }
  const current = ++searchRequest, tree = requireTreeId(app.activeTreeId)
  loading.value = true; searched.value = true; rows.value = []; path.value = []
  try {
    let result: Member[] = [], resultPath: PathNode[] = []
    if (searchType === 'name') result = (await featureApi.search(tree, form.q)).members
    else if (searchType === 'ancestors') result = await memberApi.ancestors(tree, from)
    else if (searchType === 'descendants') result = await memberApi.descendants(tree, from)
    else if (searchType === 'generation') result = (await memberApi.generations(tree, from))[String(form.generation)] || []
    else {
      const steps = await memberApi.relationshipPath(tree, from, to)
      if (steps.length || from === to) resultPath = [{ memberId: Number(from), relation: 'START' }, ...steps]
    }
    if (current === searchRequest) { rows.value = result; path.value = resultPath }
  } finally {
    if (current === searchRequest) loading.value = false
  }
}
</script>
<template>
  <PageHeader title="谱系查询" :description="`查询当前家谱 #${app.activeTreeId}`"/>
  <div class="search-layout">
    <aside class="panel search-types"><button v-for="item in types" :key="item.value" :class="{active:type===item.value}" @click="type=item.value">{{item.label}}</button></aside>
    <section class="panel">
      <div class="panel-header"><h2>{{types.find(i=>i.value===type)?.label}}</h2></div>
      <div class="panel-body">
        <el-form label-position="top">
          <el-form-item v-if="type==='name'" label="关键词"><el-input v-model="form.q"/></el-form-item>
          <el-form-item v-if="type!=='name'" label="起始成员"><MemberSelect v-model="form.memberId" :tree-id="app.activeTreeId" :members="members" :disabled="membersLoading"/></el-form-item>
          <el-form-item v-if="type==='relation'" label="目标成员"><MemberSelect v-model="form.otherMemberId" :tree-id="app.activeTreeId" :members="members" :disabled="membersLoading"/></el-form-item>
          <el-form-item v-if="type==='generation'" label="后代代数"><el-input-number v-model="form.generation" :min="1"/></el-form-item>
          <el-button type="primary" :loading="loading" :disabled="type!=='name'&&membersLoading" @click="run">开始查询</el-button>
        </el-form>
      </div>
      <div v-if="searched" class="results">
        <el-steps v-if="path.length" direction="vertical" :active="path.length"><el-step v-for="(node,index) in path" :key="`${index}-${node.memberId}`" :title="memberNames.get(node.memberId)||'未知成员'" :description="relationLabels[node.relation]||'亲属关系'"/></el-steps>
        <el-table v-else :data="rows" stripe v-loading="loading">
          <el-table-column prop="name" label="姓名"><template #default="{row}"><el-link type="primary" @click="$router.push(`/members/${row.id}`)">{{row.name}}</el-link></template></el-table-column>
          <el-table-column label="性别"><template #default="{row}">{{genderLabel[row.gender as Gender]}}</template></el-table-column>
          <el-table-column prop="birthDate" label="出生日期"/><el-table-column prop="birthPlace" label="出生地"/><el-table-column prop="occupation" label="职业"/>
        </el-table>
        <el-empty v-if="!loading&&!rows.length&&!path.length" description="未找到结果"/>
      </div>
    </section>
  </div>
</template>
<style scoped>.search-layout{display:grid;grid-template-columns:220px 1fr;gap:18px}.search-types{padding:8px}.search-types button{width:100%;border:0;background:transparent;text-align:left;padding:13px;border-radius:8px}.search-types button.active{background:#eff6ff;color:#1d4ed8;font-weight:700}.results{border-top:1px solid #e2e8f0;padding:18px}@media(max-width:700px){.search-layout{grid-template-columns:1fr}}</style>
