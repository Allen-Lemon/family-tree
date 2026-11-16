<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import MemberSelect from '@/components/MemberSelect.vue'
import { adminApi, treeApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { BackendRole, FamilyTree, User } from '@/types'
const auth=useAuthStore(), users=ref<User[]>([]), trees=ref<FamilyTree[]>([]), loading=ref(false), saving=ref(false), busy=ref<number>()
const createOpen=ref(false), assignOpen=ref(false), current=ref<User>(), selectedTrees=ref<number[]>([])
const bindingOpen=ref(false),bindingTree=ref<number|string>(''),bindingMember=ref<number|string>('')
const roleOpen=ref(false),selectedRole=ref<BackendRole>('MEMBER')
const roleNames:Record<BackendRole,string>={ADMIN:'总管理员',FAMILY_ADMIN:'普通管理员',MEMBER:'普通用户'}
function openRole(user:User){current.value=user;selectedRole.value=user.role;roleOpen.value=true}
async function changeRole(){
 if(!current.value||saving.value)return
 if(selectedRole.value===current.value.role){roleOpen.value=false;return}
 try{await ElMessageBox.confirm(`确定将“${current.value.username}”设为${roleNames[selectedRole.value]}吗？总管理员可管理全部族谱；普通管理员仅管理分配族谱的人员。`,'确认权限变更',{type:'warning',confirmButtonText:'确认修改',cancelButtonText:'取消'})}catch{return}
 saving.value=true
 try{await adminApi.changeRole(current.value.id,selectedRole.value);roleOpen.value=false;ElMessage.success('用户角色已更新');await load()}finally{saving.value=false}
}
const form=reactive({username:'',password:'',role:'MEMBER' as 'ADMIN'|'FAMILY_ADMIN'|'MEMBER',treeIds:[] as number[]})
const activeAdmins=computed(()=>users.value.filter(user=>user.role==='ADMIN'&&user.enabled).length)
function isProtected(user:User){return user.id===auth.user?.id||(user.role==='ADMIN'&&user.enabled&&activeAdmins.value<=1)}
function treeLabel(id:number){return trees.value.find(tree=>tree.id===id)?.name||'已不可用族谱'}
async function load(){loading.value=true;try{[users.value,trees.value]=await Promise.all([adminApi.users(),treeApi.list()])}finally{loading.value=false}}
function openCreate(){Object.assign(form,{username:'',password:'',role:'MEMBER',treeIds:[]});createOpen.value=true}
async function create(){
 if(saving.value)return
 if(!form.username.trim()||form.password.length<10)return ElMessage.warning('请填写用户名和至少 10 位的初始密码')
 saving.value=true
 try{await adminApi.createUser({...form,username:form.username.trim(),treeIds:form.role==='ADMIN'?[]:form.treeIds});createOpen.value=false;ElMessage.success('用户已创建');await load()}finally{saving.value=false}
}
function openAssign(user:User){current.value=user;selectedTrees.value=[...(user.assignedTreeIds||[])];assignOpen.value=true}
async function assign(){if(!current.value||saving.value)return;saving.value=true;try{await adminApi.assignTrees(current.value.id,selectedTrees.value);assignOpen.value=false;ElMessage.success('族谱分配已更新');await load()}finally{saving.value=false}}
async function toggle(user:User){if(busy.value!==undefined)return;busy.value=user.id;try{await adminApi.setEnabled(user.id,!user.enabled);await load()}finally{busy.value=undefined}}
async function remove(user:User){
 if(busy.value!==undefined||isProtected(user))return
 busy.value=user.id
 try{
  try{await ElMessageBox.confirm(`确定删除账号“${user.username}”吗？这会清除账号姓名、个人资料、人像照片、授权及登录能力，已有会话立即失效。共同族谱、成员和关系保留，仅保留匿名关联记录以维持历史资料。此操作不可恢复。`,'删除用户',{type:'warning',confirmButtonText:'确认删除',cancelButtonText:'取消'})}catch{return}
  await adminApi.removeUser(user.id);ElMessage.success('账号个人资料已清除，共同族谱资料保留');await load()
 }finally{busy.value=undefined}
}
function openBinding(user:User){current.value=user;bindingTree.value=user.memberTreeId||user.assignedTreeIds?.[0]||'';bindingMember.value=user.memberId||'';bindingOpen.value=true}
async function bind(){if(!current.value||saving.value)return;saving.value=true;try{await adminApi.bindMember(current.value.id,bindingMember.value?Number(bindingMember.value):null);bindingOpen.value=false;ElMessage.success('本人资料绑定已更新');await load()}finally{saving.value=false}}
onMounted(load)
</script>
<template>
 <PageHeader title="用户与权限" description="总管理员可查看全部族谱；普通账号仅可进入本人拥有或获分配的族谱"><el-button type="primary" @click="openCreate">新增用户</el-button></PageHeader>
 <el-alert title="删除会清除账号私人资料和人像，共同族谱资料保留匿名关联；当前账号、配置的初始管理员及最后一位可用总管理员受保护。" type="info" :closable="false" class="users-note"/>
 <section v-loading="loading" class="panel">
  <el-table :data="users" row-key="id" stripe>
   <el-table-column prop="username" label="用户名"/>
   <el-table-column label="系统角色" width="140"><template #default="{row}">{{row.role==='ADMIN'?'总管理员':row.role==='FAMILY_ADMIN'?'普通管理员':'普通用户'}}</template></el-table-column>
   <el-table-column label="可访问族谱" min-width="250"><template #default="{row}"><span v-if="row.role==='ADMIN'">全部族谱</span><template v-else-if="row.assignedTreeIds?.length"><el-tag v-for="id in row.assignedTreeIds" :key="id" class="tree-tag">{{treeLabel(id)}}</el-tag></template><span v-else class="muted">尚未分配（无法进入族谱）</span></template></el-table-column>
   <el-table-column label="本人资料" min-width="170"><template #default="{row}"><span v-if="row.memberId">{{treeLabel(row.memberTreeId)}} · {{row.memberName||'已绑定成员'}}</span><span v-else class="muted">未绑定（不能修改成员）</span></template></el-table-column>
   <el-table-column label="启用" width="90"><template #default="{row}"><el-switch :model-value="row.enabled" :disabled="isProtected(row)||busy!==undefined" @change="toggle(row)"/></template></el-table-column>
   <el-table-column prop="createdAt" label="创建时间" min-width="180"/>
   <el-table-column label="操作" width="330"><template #default="{row}"><el-button link type="primary" :disabled="isProtected(row)||busy!==undefined" @click="openRole(row)">设置管理员</el-button><el-button v-if="row.role!=='ADMIN'" link type="primary" @click="openAssign(row)">分配族谱</el-button><el-button v-if="row.role!=='ADMIN'" link type="primary" @click="openBinding(row)">绑定本人</el-button><el-button link type="danger" :disabled="isProtected(row)||busy!==undefined" :loading="busy===row.id" @click="remove(row)">删除用户</el-button></template></el-table-column>
  </el-table>
 </section>
 <el-dialog v-model="createOpen" title="新增用户" width="min(560px,94vw)" :close-on-click-modal="!saving">
  <el-form label-position="top">
   <el-form-item label="用户名" required><el-input v-model="form.username" maxlength="80" autocomplete="off"/></el-form-item>
   <el-form-item label="初始密码（至少 10 位）" required><el-input v-model="form.password" type="password" show-password autocomplete="new-password"/></el-form-item>
   <el-form-item label="角色"><el-radio-group v-model="form.role"><el-radio value="MEMBER">普通用户</el-radio><el-radio value="FAMILY_ADMIN">普通管理员</el-radio><el-radio value="ADMIN">总管理员</el-radio></el-radio-group></el-form-item>
   <el-form-item v-if="form.role!=='ADMIN'" label="分配族谱"><el-select v-model="form.treeIds" multiple filterable placeholder="按族谱名称选择（可稍后分配）" style="width:100%"><el-option v-for="tree in trees" :key="tree.id" :label="tree.name" :value="tree.id"/></el-select></el-form-item>
   <el-alert v-else title="总管理员可查看和管理全部族谱，请谨慎授予。" type="warning" :closable="false"/>
  </el-form>
  <template #footer><el-button :disabled="saving" @click="createOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="create">创建用户</el-button></template>
 </el-dialog>
 <el-dialog v-model="assignOpen" :title="`分配族谱 · ${current?.username||''}`" width="min(560px,94vw)">
  <el-alert title="普通管理员可管理选中族谱的人员与扩展字段；普通用户仅编辑绑定本人的族谱资料。取消绑定所属族谱时自动解绑；本人拥有的族谱不能直接取消。" type="info" :closable="false"/>
  <el-select v-model="selectedTrees" multiple filterable placeholder="选择允许访问的族谱" style="width:100%;margin-top:18px"><el-option v-for="tree in trees" :key="tree.id" :label="tree.name" :value="tree.id"/></el-select>
  <template #footer><el-button :disabled="saving" @click="assignOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="assign">保存分配</el-button></template>
 </el-dialog>
 <el-dialog v-model="bindingOpen" :title="`绑定本人 · ${current?.username||''}`" width="min(560px,94vw)">
  <el-alert title="仅管理员可绑定。必须选择该账号已获授权的族谱成员，一个成员只能绑定一个账号；清空成员后保存即可解绑。" type="info" :closable="false"/>
  <el-form label-position="top" style="margin-top:18px"><el-form-item label="所属族谱"><el-select v-model="bindingTree" placeholder="选择本人所在族谱" style="width:100%" @change="bindingMember='' "><el-option v-for="id in current?.assignedTreeIds||[]" :key="id" :label="treeLabel(id)" :value="id"/></el-select></el-form-item><el-form-item label="本人姓名"><MemberSelect v-model="bindingMember" :tree-id="bindingTree" placeholder="选择该账号本人的成员姓名"/></el-form-item></el-form>
  <template #footer><el-button :disabled="saving" @click="bindingOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="bind">保存绑定</el-button></template>
 </el-dialog>
 <el-dialog v-model="roleOpen" :title="`设置管理员 · ${current?.username||''}`" width="min(560px,94vw)" :close-on-click-modal="!saving"><el-alert title="仅总管理员可调整角色；普通管理员只能维护分配族谱的人员，不能管理系统用户。降为普通用户后，仅可编辑绑定本人资料。" type="warning" :closable="false"/><el-form label-position="top" style="margin-top:18px"><el-form-item label="是否设为管理员"><el-radio-group v-model="selectedRole" :disabled="saving"><el-radio value="MEMBER">否，普通用户</el-radio><el-radio value="FAMILY_ADMIN">是，普通管理员</el-radio><el-radio value="ADMIN">是，总管理员</el-radio></el-radio-group></el-form-item></el-form><template #footer><el-button :disabled="saving" @click="roleOpen=false">取消</el-button><el-button type="primary" :loading="saving" @click="changeRole">保存角色</el-button></template></el-dialog>
</template>
<style scoped>.users-note{margin-bottom:18px}.tree-tag{margin:3px}</style>
