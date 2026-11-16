import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import type { Permission } from '@/types'

const views = import.meta.glob('../views/*.vue')
const page = (name: string) => views[`../views/${name}.vue`]
const children: RouteRecordRaw[] = [
  { path: '', redirect: '/dashboard' },
  { path: 'dashboard', name: 'dashboard', component: page('DashboardView'), meta: { title: '总览统计', permission: 'dashboard:view' } },
  { path: 'trees', name: 'trees', component: page('TreesView'), meta: { title: '家谱管理', permission: 'tree:view' } },
  { path: 'trees/:id', name: 'tree-detail', component: page('TreeDetailView'), meta: { title: '家谱详情', permission: 'tree:view' } },
  { path: 'members', name: 'members', component: page('MembersView'), meta: { title: '成员管理', permission: 'member:view' } },
  { path: 'members/:id', name: 'member-detail', component: page('MemberDetailView'), meta: { title: '成员详情', permission: 'member:view' } },
  { path: 'profile', name: 'profile', component: page('ProfileView'), meta: {title:'我的个人信息'} },
  { path: 'personal-template', redirect: '/profile' },
  { path: 'member-fields', name: 'member-fields', component: page('PersonalTemplateView'), meta: {title:'族谱扩展字段',permission:'template:edit'} },
  { path: 'relations', name: 'relations', component: page('RelationsView'), meta: { title: '关系编辑', permission: 'relation:edit' } },
  { path: 'search', name: 'search', component: page('SearchView'), meta: { title: '谱系查询', permission: 'member:view' } },
  { path: 'graph', name: 'graph', component: page('GraphView'), meta: { title: '图谱视图', permission: 'tree:view' } },
  { path: 'media', name: 'media', component: page('ResourceView'), props: { resource: 'media', title: '媒体资料' }, meta: { title: '媒体资料', permission: 'member:view' } },
  { path: 'stories', name: 'stories', component: page('ResourceView'), props: { resource: 'stories', title: '故事与回忆录' }, meta: { title: '故事与回忆录', permission: 'member:view' } },
  { path: 'events', name: 'events', component: page('ResourceView'), props: { resource: 'events', title: '事件与提醒' }, meta: { title: '事件与提醒', permission: 'member:view' } },
  { path: 'suggestions', name: 'suggestions', component: page('SuggestionsView'), meta: { title: '修改建议', permission: 'member:view' } },
  { path: 'review', name: 'review', component: page('SuggestionsView'), props: { review: true }, meta: { title: '建议审核', permission: 'suggestion:review' } },
  { path: 'data', name: 'data', component: page('DataView'), meta: { title: '导入导出与备份', permission: 'data:manage' } },
  { path: 'ocr', name: 'ocr', component: page('OcrView'), meta: { title: 'OCR 识别', permission: 'data:manage' } },
  { path: 'ocr/:id', name: 'ocr-correct', component: page('OcrCorrectView'), meta: { title: 'OCR 校对', permission: 'data:manage' } },
  { path: 'notifications', name: 'notifications', component: page('NotificationsView'), meta: { title: '通知中心' } },
  { path: 'users', name: 'users', component: page('UsersView'), meta: { title: '用户与权限', permission: 'user:manage' } },
]

const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/login', name: 'login', component: page('LoginView'), meta: { public: true, title: '登录' } },
  { path: '/', component: () => import('@/layouts/AppLayout.vue'), children },
  { path: '/:pathMatch(.*)*', component: page('NotFoundView'), meta: { public: true, title: '页面不存在' } },
] })

router.beforeEach(async (to) => {
  document.title = `${String(to.meta.title || '家谱管理')} · 冯氏`
  if (to.meta.public) return true
  const auth = useAuthStore()
  try { await auth.fetchMe() } catch { /* Account status and self binding must be current on navigation. */ }
  if (!auth.isAuthenticated) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.name === 'profile') return true
  const app = useAppStore()
  // Refresh authorization on navigation; never trust a previous account's localStorage tree ID.
  try { await app.loadTrees(true) } catch { return false }
  const requestedTree = to.name === 'tree-detail' ? to.params.id : to.query.treeId
  if (requestedTree) {
    const tree = app.trees.find(item => String(item.id) === String(requestedTree))
    if (!tree) return { path: '/trees', query: { denied: '1' } }
    app.setTree(tree.id)
  }
  if (!app.trees.length && !['trees','dashboard','users','notifications','profile'].includes(String(to.name))) return { path: '/trees' }
  if (!auth.hasPermission(to.meta.permission as Permission | undefined)) return { path: '/dashboard', query: { denied: '1' } }
  return true
})
export default router
