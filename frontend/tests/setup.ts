import { vi } from 'vitest'

Object.defineProperty(window, 'matchMedia', { writable: true, value: vi.fn().mockImplementation(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })) })
Object.defineProperty(globalThis, 'ResizeObserver', { value: class { observe() {} unobserve() {} disconnect() {} } })
