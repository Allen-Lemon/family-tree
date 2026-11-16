import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { ElDatePicker } from 'element-plus'
import DatePicker from '@/components/DatePicker.vue'

function mountPicker(modelValue?: string) {
  return mount(DatePicker, {
    props: { modelValue },
    global: { components: { ElDatePicker } },
  })
}

describe('DatePicker', () => {
  it('shows saved dates and disables manual typing without disabling the picker', () => {
    const wrapper = mountPicker('1995-04-18')
    const picker = wrapper.getComponent(ElDatePicker)
    expect(picker.props('type')).toBe('date')
    expect(picker.props('valueFormat')).toBe('YYYY-MM-DD')
    expect(picker.props('editable')).toBe(false)
    expect((wrapper.get('input').element as HTMLInputElement).disabled).toBe(false)
    expect(wrapper.get('input').attributes('readonly')).toBeDefined()
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('1995-04-18')
    wrapper.unmount()
  })

  it.each(['1997.10.24', '1997/10/24'])('displays legacy date %s without modifying stored data on load', (date) => {
    const wrapper = mountPicker(date)
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('1997-10-24')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })

  it('emits calendar selections as API-compatible strings and normalizes clearing', async () => {
    const wrapper = mountPicker()
    const picker = wrapper.getComponent(ElDatePicker)
    picker.vm.$emit('update:modelValue', '2026-11-11')
    picker.vm.$emit('update:modelValue', null)
    expect(wrapper.emitted('update:modelValue')).toEqual([['2026-11-11'], ['']])
    await wrapper.setProps({ modelValue: '' })
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('')
    wrapper.unmount()
  })
})
