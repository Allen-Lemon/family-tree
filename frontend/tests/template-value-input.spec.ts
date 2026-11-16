import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElDatePicker, ElInput, ElInputNumber, ElOption, ElSelect } from 'element-plus'
import TemplateValueInput from '@/components/TemplateValueInput.vue'
import DatePicker from '@/components/DatePicker.vue'

const wrappers: VueWrapper[] = []
function render(type: 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN', modelValue?: string | null, extra = {}) {
  const wrapper = mount(TemplateValueInput, {
    props: { type, modelValue, ...extra },
    global: { plugins: [ElementPlus] },
  })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()) })

describe('TemplateValueInput', () => {
  it('passes text values, placeholder, disabled and clearable through', () => {
    const wrapper = render('TEXT', '字辈', { placeholder: '填写字辈', disabled: true, clearable: false })
    const input = wrapper.getComponent(ElInput)
    expect(input.props('modelValue')).toBe('字辈')
    expect(input.props('placeholder')).toBe('填写字辈')
    expect(input.props('disabled')).toBe(true)
    expect(input.props('clearable')).toBe(false)
    input.vm.$emit('update:modelValue', '新字辈')
    input.vm.$emit('update:modelValue', '')
    expect(wrapper.emitted('update:modelValue')).toEqual([['新字辈'], ['']])
  })

  it.each([null, undefined, ''])('renders empty text %j without emitting on mount', value => {
    const wrapper = render('TEXT', value)
    expect(wrapper.getComponent(ElInput).props('modelValue')).toBe('')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('preserves numeric zero and serializes number changes as strings, clearing to an empty string', () => {
    const wrapper = render('NUMBER', '0', { placeholder: '填写数字', disabled: true })
    const input = wrapper.getComponent(ElInputNumber)
    expect(input.props('modelValue')).toBe(0)
    expect(input.props('disabled')).toBe(true)
    expect(input.props('placeholder')).toBe('填写数字')
    input.vm.$emit('update:modelValue', 0)
    input.vm.$emit('update:modelValue', -2.5)
    input.vm.$emit('update:modelValue', undefined)
    input.vm.$emit('update:modelValue', null)
    input.vm.$emit('update:modelValue', NaN)
    expect(wrapper.emitted('update:modelValue')).toEqual([['0'], ['-2.5'], [''], [''], ['']])
  })

  it.each([null, undefined, '', '  ', 'not a number', 'Infinity'])('does not coerce empty or invalid numeric %j to zero', value => {
    const wrapper = render('NUMBER', value)
    expect(wrapper.getComponent(ElInputNumber).props('modelValue')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('updates the numeric view when the parent model changes', async () => {
    const wrapper = render('NUMBER', '12.5')
    expect(wrapper.getComponent(ElInputNumber).props('modelValue')).toBe(12.5)
    await wrapper.setProps({ modelValue: '0' })
    expect(wrapper.getComponent(ElInputNumber).props('modelValue')).toBe(0)
    await wrapper.setProps({ modelValue: '' })
    expect(wrapper.getComponent(ElInputNumber).props('modelValue')).toBeUndefined()
  })

  it('reuses DatePicker and forwards disabled, placeholder and clearable to its underlying control', () => {
    const wrapper = render('DATE', '2001-02-03', { disabled: true, placeholder: '选择日期', clearable: false })
    expect(wrapper.getComponent(DatePicker).props('modelValue')).toBe('2001-02-03')
    const picker = wrapper.getComponent(ElDatePicker)
    expect(picker.props('disabled')).toBe(true)
    expect(picker.props('placeholder')).toBe('选择日期')
    expect(picker.props('clearable')).toBe(false)
    expect(picker.props('valueFormat')).toBe('YYYY-MM-DD')
    picker.vm.$emit('update:modelValue', '2026-11-11')
    picker.vm.$emit('update:modelValue', null)
    expect(wrapper.emitted('update:modelValue')).toEqual([['2026-11-11'], ['']])
  })

  it('represents booleans with string options and distinguishes false from empty', async () => {
    const wrapper = render('BOOLEAN', 'false', { disabled: true, placeholder: '请选择是或否' })
    const select = wrapper.getComponent(ElSelect)
    expect(select.props('modelValue')).toBe('false')
    expect(select.props('clearable')).toBe(true)
    expect(select.props('disabled')).toBe(true)
    expect(select.props('placeholder')).toBe('请选择是或否')
    expect(wrapper.findAllComponents(ElOption).map(option => option.props('value'))).toEqual(['true', 'false'])
    select.vm.$emit('update:modelValue', 'true')
    select.vm.$emit('update:modelValue', 'false')
    select.vm.$emit('update:modelValue', '')
    select.vm.$emit('update:modelValue', undefined)
    expect(wrapper.emitted('update:modelValue')).toEqual([['true'], ['false'], [''], ['']])
    await wrapper.setProps({ modelValue: null, clearable: false })
    expect(select.props('modelValue')).toBe('')
    expect(select.props('clearable')).toBe(false)
  })
})
