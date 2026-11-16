import { beforeEach, describe, expect, it, vi } from 'vitest'
vi.mock('@/api/client', () => ({ request: vi.fn() }))
import { request } from '@/api/client'
import { customFieldApi } from '@/api'

describe('custom field response mapping', () => {
  beforeEach(() => vi.resetAllMocks())
  it('maps SQL-style definition keys to the frontend contract, preserving the definition id', async () => {
    vi.mocked(request).mockResolvedValue([{ id: 7, field_key: 'generation_name', label: '字辈', field_type: 'TEXT', sensitive: false }])
    expect(await customFieldApi.definitions(1)).toEqual([{ id: 7, key: 'generation_name', label: '字辈', type: 'TEXT', sensitive: false }])
    expect(request).toHaveBeenCalledWith({ url: '/trees/1/custom-fields' })
  })
})
