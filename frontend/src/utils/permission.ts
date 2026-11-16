import { watchEffect, type App, type DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { Permission } from '@/types'

export function installPermissionDirective(app: App) {
  const stops = new WeakMap<HTMLElement, () => void>()
  app.directive('permission', {
    mounted(el: HTMLElement, binding: DirectiveBinding<Permission | Permission[]>) {
      const display = el.style.display
      stops.set(el, watchEffect(() => { el.style.display = useAuthStore().hasPermission(binding.value) ? display : 'none' }))
    },
    unmounted(el: HTMLElement) { stops.get(el)?.(); stops.delete(el) },
  })
}
