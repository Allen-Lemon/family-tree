<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { treeApi } from '@/api'
import { useAppStore } from '@/stores/app'
import type { FamilyTree } from '@/types'
const app=useAppStore(),items=ref<FamilyTree[]>([]),filtered=ref<FamilyTree[]>([]),loading=ref(false),dialog=ref(false),form=reactive({name:'',description:''}),formRef=ref<FormInstance>()
async function load(){loading.value=true;try{await app.loadTrees(true);items.value=app.trees;filtered.value=items.value}finally{loading.value=false}}
function filter(q:string){filtered.value=items.value.filter(i=>!q||i.name.includes(q))}
function open(){Object.assign(form,{name:'',description:''});dialog.value=true}
async function save(){if(!await formRef.value?.validate().catch(()=>false))return;const created=await treeApi.create(form);await app.loadTrees(true);app.setTree(created.id);ElMessage.success('家谱已创建');dialog.value=false;load()}
onMounted(load)
</script>
<template><PageHeader title="家谱管理" description="建立家谱档案并进入成员、图谱和数据工作区"><el-button v-permission="'tree:edit'" type="primary" @click="open">新建家谱</el-button></PageHeader><section class="panel"><div class="filters"><el-input placeholder="搜索家谱名称" clearable style="width:280px" @input="filter"/><el-button @click="load">刷新</el-button></div><div class="card-list panel-body" v-loading="loading"><article v-for="item in filtered" :key="item.id" class="panel resource-card"><div class="tree-cover">谱</div><h3>{{item.name}}</h3><p class="muted">{{item.description||'暂无家谱简介'}}</p><div class="tree-meta"><span>所有者 #{{item.ownerId}}</span><span>版本 {{item.version}}</span></div><small class="muted">创建于 {{item.createdAt||'—'}}</small><footer><el-button @click="app.setTree(item.id);$router.push(`/trees/${item.id}`)">进入家谱</el-button><el-button type="primary" plain @click="app.setTree(item.id);$router.push(`/graph?treeId=${item.id}`)">查看图谱</el-button></footer></article></div><el-empty v-if="!loading&&!filtered.length" description="暂无可访问族谱，请联系总管理员分配"><el-button v-permission="'tree:edit'" type="primary" @click="open">创建第一部家谱</el-button></el-empty></section><el-dialog v-model="dialog" title="新建家谱" width="min(520px,94vw)"><el-form ref="formRef" :model="form" label-position="top"><el-form-item label="家谱名称" prop="name" :rules="[{required:true,message:'请输入名称'}]"><el-input v-model="form.name" placeholder="如：陇西李氏家谱"/></el-form-item><el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="5000" show-word-limit/></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog></template>
<style scoped>.tree-cover{float:right;width:52px;height:52px;border-radius:12px;background:#eff6ff;color:#2563eb;display:grid;place-items:center;font-family:serif;font-size:26px;font-weight:bold}.tree-meta{display:flex;gap:28px;padding:14px 0;color:#475569}.resource-card footer{border-top:1px solid #e2e8f0;padding-top:14px}</style>
