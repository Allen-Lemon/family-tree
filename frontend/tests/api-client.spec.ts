import { describe, expect, it } from 'vitest'
import { http } from '@/api/client'

describe('api client', () => {
  it('uses unified API prefix and session cookies', () => {
    expect(http.defaults.baseURL).toBe('/api/v1')
    expect(http.defaults.withCredentials).toBe(true)
  })
})
