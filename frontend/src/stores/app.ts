import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { treeApi } from '@/api'
import type { FamilyTree } from '@/types'

export const useAppStore = defineStore('app', () => {
  const collapsed = ref(false), mobileMenu = ref(false)
  const activeTreeId = ref(localStorage.getItem('activeTreeId') || '')
  const trees = ref<FamilyTree[]>([]), treesLoaded = ref(false)
  const activeTree = computed(() => trees.value.find(tree => String(tree.id) === activeTreeId.value))
  let generation = 0
  let request: Promise<void> | null = null
  function setTree(id: string | number) {
    const value = String(id)
    if (treesLoaded.value && value && !trees.value.some(tree => String(tree.id) === value)) return
    activeTreeId.value = value
    if (value) localStorage.setItem('activeTreeId', value)
    else localStorage.removeItem('activeTreeId')
  }
  function resetAccount() {
    generation++; request = null
    trees.value = []; treesLoaded.value = false
    activeTreeId.value = ''; mobileMenu.value = false
    localStorage.removeItem('activeTreeId')
  }
  async function loadTrees(force = false) {
    if (request) return request
    if (treesLoaded.value && !force) return
    const version = generation
    const pending = treeApi.list().then(result => {
      if (version !== generation) return
      trees.value = result
      treesLoaded.value = true
      if (!result.some(tree => String(tree.id) === activeTreeId.value)) setTree(result[0]?.id || '')
    }).finally(() => { if (request === pending) request = null })
    request = pending
    return pending
  }
  return { collapsed, mobileMenu, activeTreeId, trees, treesLoaded, activeTree, setTree, resetAccount, loadTrees }
})
