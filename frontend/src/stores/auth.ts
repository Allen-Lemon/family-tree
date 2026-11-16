import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { clearCsrf } from '@/api/client'
import { useAppStore } from './app'
import type { Permission, User } from '@/types'

const memberPermissions:Permission[]=['dashboard:view','tree:view','member:view']
const editorPermissions:Permission[]=[...memberPermissions,'member:edit','relation:edit','suggestion:review','data:manage']
const adminPermissions:Permission[]=[...editorPermissions,'tree:edit','user:manage','template:edit']
export const useAuthStore = defineStore('auth', () => {
  const app = useAppStore()
  const user = ref<User|null>(null), checked=ref(false), loading=ref(false)
  const isAuthenticated=computed(()=>!!user.value)
  const permissions=computed(()=>{
    if (!user.value) return []
    if (user.value.role==='ADMIN') return adminPermissions
    if(user.value.role==='FAMILY_ADMIN'&&app.activeTree)return [...memberPermissions,'member:edit','template:edit'] as Permission[]
    return memberPermissions
  })
  const hasPermission=(permission?:Permission|Permission[])=>{if(!permission)return true;const required=Array.isArray(permission)?permission:[permission];return required.every(item=>permissions.value.includes(item))}
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  function canManagePersonnel(treeId: number | string) {
    return isAdmin.value || (user.value?.role==='FAMILY_ADMIN' && app.trees.some(tree=>tree.id===Number(treeId)))
  }
  function canEditMember(treeId: number | string, memberId: number | string) {
    return !!user.value && (canManagePersonnel(treeId) || (Number(user.value.memberTreeId) === Number(treeId) && Number(user.value.memberId) === Number(memberId) && !!app.trees.find(tree => tree.id === Number(treeId))))
  }
  async function fetchMe(){
    try {
      const current = await authApi.me()
      if (user.value && user.value.id !== current.id) app.resetAccount()
      user.value=current
    } catch(error) { user.value=null; app.resetAccount(); clearCsrf(); throw error }
    finally { checked.value=true }
  }
  async function login(payload:{username:string;password:string}){
    loading.value=true
    try {
      const current=await authApi.login(payload)
      app.resetAccount(); clearCsrf()
      user.value=current; checked.value=true
      await app.loadTrees()
    } catch(error) { user.value=null; app.resetAccount(); clearCsrf(); throw error }
    finally { loading.value=false }
  }
  async function logout(){try{await authApi.logout()}finally{user.value=null;checked.value=false;app.resetAccount();clearCsrf()}}
  return {user,checked,loading,isAuthenticated,isAdmin,permissions,hasPermission,canManagePersonnel,canEditMember,fetchMe,login,logout}
})
