export function notifyFamilyChanged(treeId: number | string) {
  const detail = { treeId: String(treeId), changedAt: Date.now(), nonce: Math.random().toString(36) }
  window.dispatchEvent(new CustomEvent('family-tree-data-changed', { detail }))
  try { localStorage.setItem('family-tree-data-changed', JSON.stringify(detail)) } catch { /* Same-page event and graph polling still work if storage is unavailable. */ }
}
