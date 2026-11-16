<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { treeApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { notifyFamilyChanged } from '@/utils/data-changed'
import type { FamilyTree, TreeStatistics } from '@/types'
const route=useRoute(),app=useAppStore(),auth=useAuthStore(),tree=reactive<Partial<FamilyTree>>({}),stats=ref<TreeStatistics>()
const editing=ref(false),saving=ref(false),form=reactive({name:'',description:''})
async function load(){const id=String(route.params.id);app.setTree(id);await app.loadTrees(true);Object.assign(tree,app.trees.find(t=>String(t.id)===id)||{});stats.value=await treeApi.statistics(id)}
function edit(){form.name=tree.name||'';form.description=tree.description||'';editing.value=true}
async function save(){if(!auth.isAdmin||saving.value)return;if(!form.name.trim())return ElMessage.warning('请填写家谱名称');saving.value=true;try{Object.assign(tree,await treeApi.update(String(tree.id),{name:form.name.trim(),description:form.description}));await app.loadTrees(true);notifyFamilyChanged(String(tree.id));editing.value=false;ElMessage.success('家谱信息已保存')}finally{saving.value=false}}
onMounted(load)
</script>
<template>
 <PageHeader :title="tree.name||'家谱详情'" :description="auth.isAdmin?'管理员可编辑家谱名称和简介':'家谱档案只读，仅管理员可修改'"><el-button @click="$router.push('/trees')">返回</el-button><el-button v-if="auth.isAdmin" type="primary" @click="edit">编辑家谱</el-button></PageHeader>
 <div class="grid-2"><section class="panel"><div class="panel-header"><h2>基本档案</h2></div><div class="panel-body"><el-descriptions :column="1" border><el-descriptions-item label="家谱名称">{{tree.name}}</el-descriptions-item><el-descriptions-item label="所有者 ID">{{tree.ownerId}}</el-descriptions-item><el-descriptions-item label="创建时间">{{tree.createdAt||'—'}}</el-descriptions-item><el-descriptions-item label="数据版本">{{tree.version}}</el-descriptions-item><el-descriptions-item label="家谱简介">{{tree.description||'暂无'}}</el-descriptions-item><el-descriptions-item label="成员 / 关系">{{stats?.members||0}} / {{stats?.relationships||0}}</el-descriptions-item></el-descriptions></div></section>
 <section class="panel"><div class="panel-header"><h2>家谱工作台</h2></div><div class="action-list"><button @click="$router.push('/members')"><b>成员名册</b><span>查找收录成员；普通账号仅可编辑本人资料</span></button><button @click="$router.push(`/graph?treeId=${tree.id}`)"><b>可视化图谱</b><span>基于最新成员和关系数据构图</span></button><button @click="$router.push('/search')"><b>谱系查询</b><span>查询祖先、后代与亲缘关系</span></button><button v-if="auth.isAdmin" @click="$router.push('/data')"><b>导出与备份</b><span>保存这部家谱的完整副本</span></button></div></section></div>
 <el-dialog v-model="editing" title="编辑家谱" width="min(560px,94vw)"><el-form label-position="top"><el-form-item label="家谱名称" required><el-input v-model="form.name" maxlength="200"/></el-form-item><el-form-item label="家谱简介"><el-input v-model="form.description" type="textarea" :rows="5" maxlength="5000" show-word-limit/></el-form-item></el-form><template #footer><el-button :disabled="saving" @click="editing=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存家谱</el-button></template></el-dialog>
</template>
<style scoped>.action-list{display:grid}.action-list button{text-align:left;border:0;border-bottom:1px solid #e2e8f0;background:#fff;padding:18px;display:flex;flex-direction:column;gap:5px}.action-list button:hover{background:#f8fafc}.action-list span{font-size:13px;color:#64748b}</style>
