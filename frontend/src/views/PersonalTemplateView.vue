<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { customFieldApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import type { CustomFieldDefinition } from '@/types'

type Definition = CustomFieldDefinition & { enabled?: boolean; sortOrder?: number }
const app = useAppStore()
const auth = useAuthStore()
const canEdit = computed(() => auth.hasPermission('template:edit'))
const items = ref<Definition[]>([])
const sortedItems = computed(() => [...items.value].sort((a, b) =>
  (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.id ?? 0) - (b.id ?? 0)))
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const formError = ref('')
const dialog = ref(false)
const editingId = ref<number>()
const types = [
  { value: 'TEXT', label: '文字' },
  { value: 'NUMBER', label: '数字' },
  { value: 'DATE', label: '日期' },
  { value: 'BOOLEAN', label: '布尔值（是 / 否）' },
] as const
const typeLabels = Object.fromEntries(types.map(type => [type.value, type.label]))
const defaults = () => ({ key: '', label: '', type: 'TEXT' as Definition['type'], sensitive: false, enabled: true, sortOrder: 0 as number | undefined })
const form = reactive(defaults())
let loadVersion = 0
let treeVersion = 0

async function load() {
  const version = ++loadVersion
  const tree = app.activeTreeId
  items.value = []
  loadError.value = ''
  loading.value = !!tree
  if (!tree) return
  try {
    const result = await customFieldApi.definitions(tree)
    if (version === loadVersion) items.value = result
  } catch {
    if (version === loadVersion) loadError.value = '字段模版加载失败，请重试。'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function openForm(item?: Definition) {
  if (!canEdit.value || !app.activeTreeId || loading.value || saving.value || (item && item.id == null)) return
  editingId.value = item?.id
  Object.assign(form, item ? {
    key: item.key, label: item.label, type: item.type, sensitive: item.sensitive,
    enabled: item.enabled !== false, sortOrder: item.sortOrder ?? 0,
  } : defaults())
  formError.value = ''
  dialog.value = true
}

async function save() {
  if (!canEdit.value || !app.activeTreeId || loading.value || saving.value || !dialog.value) return
  formError.value = ''
  const label = form.label.trim()
  if (editingId.value == null && !/^[A-Za-z][A-Za-z0-9_]{0,79}$/.test(form.key)) {
    formError.value = '字段 key 必须以英文字母开头，仅含字母、数字和下划线，长度为 1–80。'
    return
  }
  if (!label) { formError.value = '请填写字段名称。'; return }
  if (!types.some(type => type.value === form.type)) { formError.value = '请选择有效的字段类型。'; return }
  if (form.sortOrder == null || !Number.isInteger(form.sortOrder) || form.sortOrder < 0) {
    formError.value = '排序必须为大于或等于 0 的整数。'
    return
  }
  const tree = app.activeTreeId
  const version = treeVersion
  const id = editingId.value
  const data = { label, enabled: form.enabled, sortOrder: form.sortOrder }
  saving.value = true
  try {
    if (id == null) {
      await customFieldApi.create(tree, { ...data, key: form.key, type: form.type, sensitive: form.sensitive })
    } else {
      await customFieldApi.update(tree, id, data)
    }
    if (version !== treeVersion) return
    dialog.value = false
    ElMessage.success(id == null ? '字段模版已创建' : '字段模版已更新')
    await load()
  } catch {
    if (version === treeVersion) formError.value = '保存失败，请检查字段 key 是否重复及当前权限后重试。'
  } finally {
    if (version === treeVersion) saving.value = false
  }
}

watch(() => app.activeTreeId, () => {
  treeVersion++
  dialog.value = false
  saving.value = false
  editingId.value = undefined
  Object.assign(form, defaults())
  formError.value = ''
  void load()
}, { immediate: true, flush: 'sync' })
watch(canEdit, allowed => { if (!allowed) dialog.value = false })
onBeforeUnmount(() => { loadVersion++; treeVersion++ })
</script>

<template>
  <PageHeader title="个人信息字段模版" description="按当前族谱配置成员资料字段；停用仅停止录入，不删除已有值。">
    <el-button v-if="canEdit" data-test="create-field" type="primary" :disabled="!app.activeTreeId || loading || saving" @click="openForm()">新增字段</el-button>
  </PageHeader>
  <section class="panel template-panel">
    <p data-test="current-tree">当前族谱：{{ app.activeTree?.name || (app.activeTreeId ? `#${app.activeTreeId}` : '未选择') }}</p>
    <el-alert v-if="!app.activeTreeId" title="请先在顶部选择族谱，再配置个人信息字段模版。" type="info" :closable="false" />
    <template v-else>
      <el-alert v-if="!canEdit" title="当前为只读模式；仅有字段模版管理权限的管理员可新增或编辑。" type="info" :closable="false" />
      <el-alert title="敏感字段仅管理员可读。字段 key、类型及敏感标记创建后不可修改；停用字段仍会列出并保留已有值。" type="info" :closable="false" />
      <div v-if="loadError" role="alert" class="load-error">
        <span>{{ loadError }}</span>
        <el-button data-test="retry" :disabled="loading || saving" @click="load">重试</el-button>
      </div>
      <el-table v-loading="loading" :data="sortedItems" row-key="id" stripe aria-label="个人信息字段模版列表">
        <el-table-column prop="label" label="字段名称" min-width="140" />
        <el-table-column prop="key" label="Key（不可修改）" min-width="160" />
        <el-table-column label="类型" min-width="120"><template #default="{ row }">{{ typeLabels[row.type] || row.type }}</template></el-table-column>
        <el-table-column label="可见范围" min-width="150"><template #default="{ row }"><el-tag :type="row.sensitive ? 'warning' : 'info'">{{ row.sensitive ? '敏感 · 仅管理员可读' : '普通字段' }}</el-tag></template></el-table-column>
        <el-table-column label="排序" width="90"><template #default="{ row }">{{ row.sortOrder ?? 0 }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled === false ? 'info' : 'success'">{{ row.enabled === false ? '已停用' : '已启用' }}</el-tag></template></el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="100"><template #default="{ row }"><el-button :data-test="`edit-field-${row.id}`" link type="primary" :disabled="loading || saving || row.id == null" @click="openForm(row)">编辑</el-button></template></el-table-column>
        <template #empty>{{ loading ? '正在加载字段模版…' : loadError ? '字段模版未加载' : '尚未配置字段，请新增字段模版' }}</template>
      </el-table>
    </template>
  </section>

  <el-dialog v-model="dialog" :title="editingId == null ? '新增字段模版' : '编辑字段模版'" width="min(560px,94vw)" :show-close="!saving" :close-on-click-modal="!saving" :close-on-press-escape="!saving" destroy-on-close>
    <el-alert v-if="formError" :title="formError" type="error" :closable="false" data-test="form-error" />
    <el-form label-position="top" @submit.prevent="save">
      <el-form-item label="字段 Key" required>
        <el-input v-model="form.key" data-test="field-key" :disabled="editingId != null || saving" :maxlength="80" placeholder="例如 generation_name" />
        <small>英文字母开头，允许字母、数字和下划线；创建后不可修改。</small>
      </el-form-item>
      <el-form-item label="字段名称" required><el-input v-model="form.label" data-test="field-label" :disabled="saving" placeholder="例如 字辈" /></el-form-item>
      <el-form-item label="字段类型" required><el-select v-model="form.type" data-test="field-type" :disabled="editingId != null || saving"><el-option v-for="type in types" :key="type.value" :value="type.value" :label="type.label" /></el-select></el-form-item>
      <el-form-item label="排序（非负整数）" required><el-input-number v-model="form.sortOrder" data-test="field-order" :disabled="saving" :min="0" :step="1" :controls="false" /></el-form-item>
      <el-form-item label="启用"><el-switch v-model="form.enabled" data-test="field-enabled" :disabled="saving" /><small>停用后保留已有字段值，不永久删除。</small></el-form-item>
      <el-form-item label="敏感字段"><el-checkbox v-model="form.sensitive" data-test="field-sensitive" :disabled="editingId != null || saving">仅管理员可读（创建后不可修改）</el-checkbox></el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="dialog = false">取消</el-button>
      <el-button data-test="save-field" type="primary" :loading="saving" :disabled="!canEdit || loading" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.template-panel { display: grid; gap: 16px; }
.template-panel p { margin: 0; }
.load-error { display: flex; align-items: center; gap: 12px; color: var(--el-color-danger); }
small { display: block; width: 100%; line-height: 1.6; color: var(--el-text-color-secondary); }
</style>
