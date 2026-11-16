import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick, reactive } from 'vue'
import ElementPlus, { ElCheckbox, ElDialog, ElInput, ElInputNumber, ElSelect, ElSwitch, ElTable } from 'element-plus'
import PersonalTemplateView from '@/views/PersonalTemplateView.vue'
import { customFieldApi } from '@/api'

vi.mock('@/api', () => ({ customFieldApi: { definitions: vi.fn(), create: vi.fn(), update: vi.fn() } }))
vi.mock('@/stores/app', () => ({ useAppStore: () => app }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ hasPermission }) }))
const app = reactive({ activeTreeId: '1', activeTree: { name: '张氏族谱' } })
const access = reactive({ editable: true })
const hasPermission = vi.fn(() => access.editable)
const definitions = vi.mocked(customFieldApi.definitions)
const wrappers: VueWrapper[] = []
const field = (id: number, extra = {}) => ({ id, key: `field_${id}`, label: `字段${id}`, type: 'TEXT' as const, sensitive: false, enabled: true, sortOrder: 0, ...extra })
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function render() {
  const wrapper = mount(PersonalTemplateView, {
    global: { plugins: [ElementPlus] },
  })
  wrappers.push(wrapper)
  return wrapper
}
async function openCreate(wrapper: VueWrapper) {
  await wrapper.get('[data-test="create-field"]').trigger('click')
  await flushPromises()
}
function fieldComponent(wrapper: VueWrapper, name: string): VueWrapper {
  const selector = `[data-test="field-${name}"]`
  const controls: Record<string, VueWrapper[]> = {
    key: wrapper.findAllComponents(ElInput),
    label: wrapper.findAllComponents(ElInput),
    type: wrapper.findAllComponents(ElSelect),
    order: wrapper.findAllComponents(ElInputNumber),
    enabled: wrapper.findAllComponents(ElSwitch),
    sensitive: wrapper.findAllComponents(ElCheckbox),
  }
  const control = controls[name]?.find(candidate =>
    candidate.attributes('data-test') === `field-${name}` || candidate.find(selector).exists())
  if (!control) throw new Error(`Missing field control: ${name}`)
  return control
}
function tableRows(wrapper: VueWrapper): ReturnType<typeof field>[] {
  return ((wrapper.getComponent(ElTable) as VueWrapper).props() as {data: ReturnType<typeof field>[]}).data
}
async function setField(wrapper: VueWrapper, name: string, value: unknown) {
  fieldComponent(wrapper, name).vm.$emit('update:modelValue', value)
  await nextTick()
}
async function submit(wrapper: VueWrapper) {
  await wrapper.get('[data-test="save-field"]').trigger('click')
  await flushPromises()
}

beforeEach(() => {
  vi.resetAllMocks()
  app.activeTreeId = '1'
  app.activeTree.name = '张氏族谱'
  access.editable = true
  hasPermission.mockImplementation(() => access.editable)
  definitions.mockResolvedValue([])
  vi.mocked(customFieldApi.create).mockResolvedValue(undefined)
  vi.mocked(customFieldApi.update).mockResolvedValue(undefined)
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

describe('PersonalTemplateView', () => {
  it('loads all fields, including disabled ones, sorted by sortOrder and then id', async () => {
    definitions.mockResolvedValue([
      field(7, { sortOrder: 2 }), field(5, { enabled: false, sensitive: true }), field(3),
    ])
    const wrapper = render()
    await flushPromises()
    expect(definitions).toHaveBeenCalledWith('1')
    expect(tableRows(wrapper).map((row: { id: number }) => row.id)).toEqual([3, 5, 7])
    expect(wrapper.text()).toContain('张氏族谱')
    expect(wrapper.text()).toContain('已停用')
    expect(wrapper.text()).toContain('仅管理员可读')
    expect(wrapper.text()).toContain('保留已有值')
    expect(wrapper.findAll('button').some(button => button.text().includes('删除'))).toBe(false)
  })

  it('uses template:edit for management and closes a form when permission is lost', async () => {
    const wrapper = render()
    await flushPromises()
    expect(hasPermission).toHaveBeenCalledWith('template:edit')
    await openCreate(wrapper)
    access.editable = false
    await flushPromises()
    expect(wrapper.find('[data-test="create-field"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('只读模式')
    expect(wrapper.getComponent(ElDialog).props('modelValue')).toBe(false)
    expect(customFieldApi.create).not.toHaveBeenCalled()
  })

  it('does not request without a current tree and prompts the user to select one', async () => {
    app.activeTreeId = ''
    const wrapper = render()
    await flushPromises()
    expect(definitions).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请先在顶部选择族谱')
    expect(wrapper.get('[data-test="create-field"]').attributes('disabled')).toBeDefined()
  })

  it.each(['TEXT', 'NUMBER', 'DATE', 'BOOLEAN'] as const)('creates a %s definition with explicit defaults', async type => {
    const wrapper = render()
    await flushPromises()
    await openCreate(wrapper)
    expect(wrapper.getComponent(ElSwitch).props().modelValue).toBe(true)
    expect(wrapper.getComponent(ElCheckbox).props().modelValue).toBe(false)
    await setField(wrapper, 'key', 'Generation_1')
    await setField(wrapper, 'label', ' 字辈 ')
    await setField(wrapper, 'type', type)
    await submit(wrapper)
    expect(customFieldApi.create).toHaveBeenCalledWith('1', {
      key: 'Generation_1', label: '字辈', type, sensitive: false, enabled: true, sortOrder: 0,
    })
    expect(definitions).toHaveBeenCalledTimes(2)
  })

  it('allows opting in to sensitive fields at creation', async () => {
    const wrapper = render()
    await flushPromises()
    await openCreate(wrapper)
    await setField(wrapper, 'key', 'private_note')
    await setField(wrapper, 'label', '私人备注')
    await setField(wrapper, 'sensitive', true)
    await setField(wrapper, 'order', 9)
    await submit(wrapper)
    expect(customFieldApi.create).toHaveBeenCalledWith('1', expect.objectContaining({ sensitive: true, sortOrder: 9 }))
  })

  it.each(['', '1name', '_name', 'a-b', '中文', 'a'.repeat(81), ' has_space'])('rejects invalid key %j', async key => {
    const wrapper = render()
    await flushPromises()
    await openCreate(wrapper)
    await setField(wrapper, 'key', key)
    await setField(wrapper, 'label', '名称')
    await submit(wrapper)
    expect(wrapper.get('[data-test="form-error"]').text()).toContain('字段 key')
    expect(customFieldApi.create).not.toHaveBeenCalled()
  })

  it.each([
    { label: '  ', order: 0, message: '请填写字段名称' },
    { label: '名称', order: 1.5, message: '排序必须' },
    { label: '名称', order: undefined, message: '排序必须' },
  ])('validates required label and integer order: $label / $order', async ({ label, order, message }) => {
    const wrapper = render()
    await flushPromises()
    await openCreate(wrapper)
    await setField(wrapper, 'key', 'a'.repeat(80))
    await setField(wrapper, 'label', label)
    await setField(wrapper, 'order', order)
    await submit(wrapper)
    expect(wrapper.get('[data-test="form-error"]').text()).toContain(message)
    expect(customFieldApi.create).not.toHaveBeenCalled()
  })

  it('clamps negative order to zero through the real number control before saving', async () => {
    const wrapper = render()
    await flushPromises()
    await openCreate(wrapper)
    await setField(wrapper, 'key', 'order_check')
    await setField(wrapper, 'label', '排序验证')
    await fieldComponent(wrapper, 'order').get('input').setValue('-1')
    await fieldComponent(wrapper, 'order').get('input').trigger('change')
    await flushPromises()
    expect((fieldComponent(wrapper, 'order').props() as {modelValue:number}).modelValue).toBe(0)
    await submit(wrapper)
    expect(customFieldApi.create).toHaveBeenCalledWith('1', expect.objectContaining({ sortOrder: 0 }))
  })

  it('edits only label, enabled and order, keeping key, type and sensitive immutable', async () => {
    definitions.mockResolvedValue([field(8, { sensitive: true })])
    const wrapper = render()
    await flushPromises()
    await wrapper.get('[data-test="edit-field-8"]').trigger('click')
    await flushPromises()
    expect((fieldComponent(wrapper, 'key').props() as {disabled:boolean}).disabled).toBe(true)
    expect(wrapper.getComponent(ElSelect).props('disabled')).toBe(true)
    expect(wrapper.getComponent(ElCheckbox).props('disabled')).toBe(true)
    expect(wrapper.getComponent(ElInputNumber).props('min')).toBe(0)
    await setField(wrapper, 'label', '新名称')
    await setField(wrapper, 'order', 4)
    await setField(wrapper, 'enabled', false)
    await submit(wrapper)
    expect(customFieldApi.update).toHaveBeenCalledWith('1', 8, { label: '新名称', enabled: false, sortOrder: 4 })
    expect(customFieldApi.create).not.toHaveBeenCalled()
  })

  it('shows load failure and supports retry', async () => {
    definitions.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([field(4)])
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('.load-error[role="alert"]').text()).toContain('字段模版加载失败')
    await wrapper.get('[data-test="retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('字段模版加载失败')
    expect(tableRows(wrapper)).toEqual([field(4)])
  })

  it('clears rows and closes forms on tree changes, ignoring late loads and stale failures', async () => {
    const old = deferred<ReturnType<typeof field>[]>()
    const next = deferred<ReturnType<typeof field>[]>()
    definitions.mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise)
    const wrapper = render()
    app.activeTreeId = '2'
    await nextTick()
    expect(tableRows(wrapper)).toEqual([])
    next.resolve([field(20)])
    await flushPromises()
    old.reject(new Error('old tree offline'))
    await flushPromises()
    expect(wrapper.text()).not.toContain('字段模版加载失败')
    expect(tableRows(wrapper)).toEqual([field(20)])
    await openCreate(wrapper)
    app.activeTreeId = ''
    await flushPromises()
    expect(wrapper.getComponent(ElDialog).props('modelValue')).toBe(false)
    expect(wrapper.findComponent(ElTable).exists()).toBe(false)
  })

  it('ignores a successful late load from the previous tree', async () => {
    const old = deferred<ReturnType<typeof field>[]>()
    definitions.mockReturnValueOnce(old.promise).mockResolvedValueOnce([field(20)])
    const wrapper = render()
    app.activeTreeId = '2'
    await flushPromises()
    old.resolve([field(1)])
    await flushPromises()
    expect(tableRows(wrapper)).toEqual([field(20)])
  })

  it('keeps save errors in the form and prevents duplicate submission', async () => {
    const pending = deferred<void>()
    vi.mocked(customFieldApi.create).mockReturnValueOnce(pending.promise)
    const wrapper = render()
    await flushPromises()
    await openCreate(wrapper)
    await setField(wrapper, 'key', 'note')
    await setField(wrapper, 'label', '备注')
    await submit(wrapper)
    await submit(wrapper)
    expect(customFieldApi.create).toHaveBeenCalledTimes(1)
    pending.reject(new Error('duplicate'))
    await flushPromises()
    expect(wrapper.get('[data-test="form-error"]').text()).toContain('保存失败')
  })

  it('does not refresh or alter the new tree after an old-tree save resolves', async () => {
    const pending = deferred<void>()
    vi.mocked(customFieldApi.create).mockReturnValueOnce(pending.promise)
    const wrapper = render()
    await flushPromises()
    await openCreate(wrapper)
    await setField(wrapper, 'key', 'note')
    await setField(wrapper, 'label', '备注')
    await submit(wrapper)
    app.activeTreeId = '2'
    await flushPromises()
    await openCreate(wrapper)
    await setField(wrapper, 'label', '新族谱未保存表单')
    pending.resolve(undefined)
    await flushPromises()
    expect(definitions).toHaveBeenCalledTimes(2)
    expect((fieldComponent(wrapper, 'label').props() as {modelValue:string}).modelValue).toBe('新族谱未保存表单')
    expect(wrapper.find('[data-test="save-field"]').exists()).toBe(true)
  })
})
