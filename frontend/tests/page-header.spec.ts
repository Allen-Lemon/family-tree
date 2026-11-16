import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PageHeader from '@/components/PageHeader.vue'

describe('PageHeader', () => {
  it('renders title, description and actions', () => {
    const wrapper = mount(PageHeader, { props: { title:'成员管理', description:'维护资料' }, slots: { default:'新增成员' } })
    expect(wrapper.get('h1').text()).toBe('成员管理')
    expect(wrapper.text()).toContain('维护资料')
    expect(wrapper.text()).toContain('新增成员')
  })
})
