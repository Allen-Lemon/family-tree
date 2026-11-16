<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElOption, ElSelect } from 'element-plus'
import { memberApi } from '@/api'
import type { Member } from '@/types'

const props = withDefaults(defineProps<{
  modelValue?: number | string | null
  treeId?: number | string | null
  members?: Member[]
  placeholder?: string
  disabled?: boolean
}>(), { placeholder: '按姓名搜索并选择成员', disabled: false })
const emit = defineEmits<{ 'update:modelValue': [value: number | ''] }>()
const loaded = ref<Member[]>([])
const loading = ref(false)
const failed = ref(false)
const keyword = ref('')
let requestId = 0
const treeKey = computed(() => {
  const id = Number(props.treeId)
  return Number.isInteger(id) && id > 0 ? String(id) : ''
})
const options = computed(() => (props.members ?? loaded.value)
  .filter(member => String(member.treeId) === treeKey.value && !member.deleted))
const nameCounts = computed(() => {
  const counts = new Map<string, number>()
  options.value.forEach(member => counts.set(member.name, (counts.get(member.name) ?? 0) + 1))
  return counts
})
const filtered = computed(() => options.value.filter(member => member.name.toLocaleLowerCase().includes(keyword.value.toLocaleLowerCase().trim())))
const selection = computed(() => options.value.some(member => String(member.id) === String(props.modelValue)) ? Number(props.modelValue) : undefined)
function label(member: Member) {
  if ((nameCounts.value.get(member.name) ?? 0) < 2) return member.name
  return `${member.name}（${member.birthDate || '出生日期不详'} · ${member.birthPlace || '出生地不详'} · #${member.id}）`
}
function filterByName(value: string) { keyword.value = value }
function select(value: unknown) {
  const member = options.value.find(item => String(item.id) === String(value))
  emit('update:modelValue', member?.id ?? '')
}
async function loadOptions() {
  const current = ++requestId
  const tree = treeKey.value
  loaded.value = []
  failed.value = false
  loading.value = false
  if (!tree || props.members !== undefined) return
  loading.value = true
  try {
    const members = await memberApi.list(tree)
    if (current === requestId) loaded.value = members
  } catch {
    if (current === requestId) failed.value = true
  } finally {
    if (current === requestId) loading.value = false
  }
}
watch([treeKey, () => props.members], ([tree], previous) => {
  keyword.value = ''
  if (previous?.length && tree !== previous[0]) emit('update:modelValue', '')
  void loadOptions()
}, { immediate: true })
onBeforeUnmount(() => { requestId++ })
</script>

<template>
  <ElSelect
    :model-value="selection"
    filterable
    clearable
    :disabled="disabled || !treeKey"
    :loading="loading"
    :placeholder="treeKey ? placeholder : '请先选择家谱'"
    :filter-method="filterByName"
    style="width: 100%"
    @update:model-value="select"
    @visible-change="keyword = ''"
  >
    <ElOption v-for="member in filtered" :key="member.id" :value="member.id" :label="label(member)" />
    <template #empty>
      <div class="member-select-empty">
        <template v-if="failed">成员加载失败 <ElButton link type="primary" @click.stop="loadOptions">重试</ElButton></template>
        <template v-else>{{ loading ? '正在加载成员…' : keyword ? '未找到该姓名的成员' : '当前家谱暂无成员' }}</template>
      </div>
    </template>
  </ElSelect>
</template>

<style scoped>
.member-select-empty { padding: 12px; color: var(--el-text-color-secondary); text-align: center; }
</style>
