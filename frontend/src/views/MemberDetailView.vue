<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import DatePicker from '@/components/DatePicker.vue'
import TemplateValueInput from '@/components/TemplateValueInput.vue'
import { customFieldApi, featureApi, memberApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { genderLabel, memberInput, requireTreeId } from '@/utils/backend'
import { notifyFamilyChanged } from '@/utils/data-changed'
import type { CustomFieldValue, Member } from '@/types'
const route=useRoute(),app=useAppStore(),auth=useAuthStore()
const loading=ref(true),saving=ref(false),active=ref('basic'),member=reactive<Partial<Member>>({}),fields=ref<(CustomFieldValue&{definitionId?:number})[]>([])
const personnel=computed(()=>auth.canManagePersonnel(app.activeTreeId))
const editable=computed(()=>!loading.value&&auth.canEditMember(app.activeTreeId,String(route.params.id)))
async function load(){
 loading.value=true
 try{
  await auth.fetchMe()
  const tree=requireTreeId(app.activeTreeId),id=String(route.params.id)
  Object.assign(member,await memberApi.get(tree,id))
  const [values,definitions]=await Promise.all([customFieldApi.values(tree,id),customFieldApi.definitions(tree)])
  fields.value=values.map(value=>({...value,definitionId:definitions.find(d=>d.key===value.field_key)?.id}))
 }finally{loading.value=false}
}
async function save(){
 if(!editable.value||saving.value)return
 const tree=requireTreeId(app.activeTreeId),id=String(route.params.id)
 saving.value=true
 try{
  const data=memberInput(member)
  if(!personnel.value)delete data.avatarMediaId
  Object.assign(member,await memberApi.update(tree,id,data))
  if(personnel.value)await Promise.all(fields.value.filter(f=>f.definitionId&&f.enabled!==false&&f.value_text!==undefined).map(f=>customFieldApi.setValue(tree,f.definitionId!,id,f.value_text||'')))
  notifyFamilyChanged(tree);ElMessage.success('成员资料已保存')
 }finally{saving.value=false}
}
function uploadRequest(options:any){
 if(!personnel.value)return options.onError(new Error('仅管理员可以上传系统媒体'))
 const fd=new FormData();fd.append('file',options.file)
 featureApi.uploadMedia(requireTreeId(app.activeTreeId),fd).then(m=>{member.avatarMediaId=m.id;options.onSuccess(m)}).catch(options.onError)
}
onMounted(load)
</script>
<template>
 <PageHeader :title="member.name||'成员详情'" :description="personnel?'管理员可维护当前族谱成员资料':editable?'当前为登录账号绑定的本人资料，可编辑基本信息':'只读资料：普通账号仅可修改绑定的本人基本信息'"><el-button @click="$router.push('/members')">返回列表</el-button><el-button v-if="editable" type="primary" :loading="saving" @click="save">保存资料</el-button></PageHeader>
 <section v-loading="loading" class="panel detail-shell">
  <aside class="profile"><el-avatar :size="84" :src="member.avatarMediaId?featureApi.mediaUrl(app.activeTreeId,member.avatarMediaId):undefined">{{member.name?.slice(0,1)}}</el-avatar><h2>{{member.name}}</h2><p>{{member.gender?genderLabel[member.gender]:'未标注'}}</p><div class="profile-actions"><el-button v-if="auth.isAdmin" @click="$router.push(`/relations?memberId=${member.id}`)">编辑关系</el-button><el-button @click="$router.push(`/graph?focus=${member.id}`)">图谱定位</el-button></div></aside>
  <main class="detail-main"><el-tabs v-model="active">
   <el-tab-pane label="基本信息" name="basic"><el-form label-position="top" :disabled="!editable||saving"><div class="form-grid">
    <el-form-item label="姓名"><el-input v-model="member.name"/></el-form-item>
    <el-form-item label="性别"><el-select v-model="member.gender" style="width:100%"><el-option v-for="(label,value) in genderLabel" :key="value" :label="label" :value="value"/></el-select></el-form-item>
    <el-form-item label="出生日期"><DatePicker v-model="member.birthDate" placeholder="请选择出生日期"/></el-form-item><el-form-item label="出生地"><el-input v-model="member.birthPlace"/></el-form-item>
    <el-form-item label="逝世日期"><DatePicker v-model="member.deathDate" placeholder="请选择逝世日期"/></el-form-item><el-form-item label="逝世地"><el-input v-model="member.deathPlace"/></el-form-item>
    <el-form-item label="职业"><el-input v-model="member.occupation"/></el-form-item><el-form-item label="身高（厘米）"><el-input-number v-model="member.heightCm" :min="0" :max="300"/></el-form-item>
    <el-form-item label="地址" class="full"><el-input v-model="member.address"/></el-form-item><el-form-item label="备注" class="full"><el-input v-model="member.notes" type="textarea" :rows="4"/></el-form-item>
   </div></el-form></el-tab-pane>
   <el-tab-pane label="族谱扩展资料" name="fields"><el-form :disabled="!personnel||saving"><div v-for="field in fields" :key="field.field_key" class="field-row"><label>{{field.label}}<small v-if="field.sensitive">敏感</small><small v-if="field.enabled===false">已停用</small></label><TemplateValueInput v-model="field.value_text" :type="field.field_type" :disabled="!personnel||saving||field.enabled===false" :placeholder="`请填写${field.label}`"/></div></el-form><p v-if="!personnel" class="muted">族谱扩展字段仅当前族谱管理员可修改。</p><el-button v-if="personnel" link type="primary" @click="$router.push('/member-fields')">配置族谱扩展字段</el-button><el-empty v-if="!fields.length" description="管理员尚未配置族谱扩展字段"/></el-tab-pane>
   <el-tab-pane label="媒体资料" name="media"><el-upload v-if="personnel" drag :http-request="uploadRequest"><div>拖放头像、照片、PDF 或文档到此处上传</div></el-upload><p v-else class="muted">系统媒体资料仅管理员可维护。</p><p v-if="member.avatarMediaId" class="muted">头像媒体 #{{member.avatarMediaId}}；管理员保存成员资料后生效。</p></el-tab-pane>
  </el-tabs></main>
 </section>
</template>
<style scoped>.detail-shell{display:grid;grid-template-columns:240px 1fr;min-height:520px}.profile{text-align:center;padding:30px;border-right:1px solid #e2e8f0}.profile-actions{display:grid;gap:10px;margin-top:24px}.detail-main{padding:16px 24px}.field-row{display:grid;grid-template-columns:180px 1fr;gap:14px;align-items:center;margin:12px}.field-row label{font-weight:600}.field-row small{margin-left:8px;color:#d97706}@media(max-width:720px){.detail-shell{grid-template-columns:1fr}.profile{border:0;border-bottom:1px solid #e2e8f0}.field-row{grid-template-columns:1fr}}</style>
