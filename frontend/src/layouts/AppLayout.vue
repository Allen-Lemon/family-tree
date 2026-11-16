<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import type { Permission } from '@/types'

const route = useRoute(), router = useRouter(), auth = useAuthStore(), app = useAppStore()
const menus: { path: string; label: string; icon: string; permission?: Permission; group: string }[] = [
  { path: '/dashboard', label: '总览统计', icon: '▦', permission: 'dashboard:view', group: '概览' },
  { path: '/trees', label: '家谱管理', icon: '谱', permission: 'tree:view', group: '家谱' },
  { path: '/members', label: '成员管理', icon: '人', permission: 'member:view', group: '家谱' },
  { path: '/profile', label: '我的个人信息', icon: '我', group: '个人' },
  { path: '/relations', label: '关系编辑', icon: '联', permission: 'relation:edit', group: '家谱' },
  { path: '/graph', label: '图谱视图', icon: '图', permission: 'tree:view', group: '家谱' },
  { path: '/search', label: '谱系查询', icon: '查', permission: 'member:view', group: '查询' },
  { path: '/media', label: '媒体资料', icon: '像', permission: 'member:view', group: '内容' },
  { path: '/stories', label: '故事回忆录', icon: '记', permission: 'member:view', group: '内容' },
  { path: '/events', label: '事件提醒', icon: '事', permission: 'member:view', group: '内容' },
  { path: '/suggestions', label: '修改建议', icon: '议', permission: 'member:view', group: '协作' },
  { path: '/review', label: '管理员审核', icon: '审', permission: 'suggestion:review', group: '协作' },
  { path: '/ocr', label: 'OCR 识别', icon: '扫', permission: 'data:manage', group: '工具' },
  { path: '/data', label: '数据与备份', icon: '备', permission: 'data:manage', group: '工具' },
  { path: '/users', label: '用户与权限', icon: '权', permission: 'user:manage', group: '系统' },
]
const visibleMenus = computed(() => menus.filter((m) => auth.hasPermission(m.permission)))
const groups = computed(() => [...new Set(visibleMenus.value.map((m) => m.group))])
async function logout() { await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录'); await auth.logout(); router.push('/login') }
</script>
<template>
  <div class="app-shell" :class="{ collapsed: app.collapsed }">
    <aside class="sidebar" :class="{ open: app.mobileMenu }">
      <div class="brand"><span class="brand-mark">冯</span><div><strong>冯氏</strong><small>家谱管理系统</small></div></div>
      <nav aria-label="主导航">
        <section v-for="group in groups" :key="group" class="nav-group"><p>{{ group }}</p>
          <router-link v-for="item in visibleMenus.filter(m => m.group === group)" :key="item.path" :to="item.path" @click="app.mobileMenu=false"><span class="nav-icon">{{ item.icon }}</span><span>{{ item.label }}</span></router-link>
        </section>
      </nav>
    </aside>
    <main class="main-area">
      <header class="topbar">
        <button class="icon-button menu-button" aria-label="打开菜单" @click="app.mobileMenu = !app.mobileMenu">☰</button>
        <button class="icon-button desktop-collapse" aria-label="折叠菜单" @click="app.collapsed = !app.collapsed">☰</button>
        <div class="breadcrumb"><span>冯氏</span><b>/</b><strong>{{ route.meta.title }}</strong></div>
        <div class="top-actions"><router-link class="icon-button notice-button" to="/notifications" aria-label="通知">铃<span></span></router-link>
          <el-dropdown><button class="user-button"><span class="avatar">{{ auth.user?.username?.slice(0,1) }}</span><span>{{ auth.user?.username }}</span><i>⌄</i></button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item @click="router.push('/profile')">我的个人信息</el-dropdown-item><el-dropdown-item divided @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>
      <div class="content"><router-view :key="`${auth.user?.id}:${app.activeTreeId}`" /></div>
    </main>
    <div v-if="app.mobileMenu" class="overlay" @click="app.mobileMenu=false"></div>
  </div>
</template>
