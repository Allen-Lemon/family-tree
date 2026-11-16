<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import DatePicker from '@/components/DatePicker.vue'
import { profileApi } from '@/api'
import { http } from '@/api/client'
import { genderLabel } from '@/utils/backend'
import type { AccountProfile, Gender } from '@/types'
const loading=ref(true),saving=ref(false),uploading=ref(false),error=ref(''),portrait=ref('')
const account=ref<AccountProfile>(),form=reactive({displayName:'',birthDate:'',gender:'UNKNOWN' as Gender,spouseStatus:'',education:'',biography:''})
let disposed=false,imageVersion=0
const age=computed(()=>{
 if(!form.birthDate)return '未填写出生日期'
 const date=new Date(form.birthDate+'T00:00:00'),today=new Date()
 if(Number.isNaN(date.getTime())||date>today)return '请选择有效的出生日期'
 let years=today.getFullYear()-date.getFullYear()
 if(today.getMonth()<date.getMonth()||(today.getMonth()===date.getMonth()&&today.getDate()<date.getDate()))years--
 return `${years} 岁`
})
async function refreshPortrait(){
 const version=++imageVersion
 if(portrait.value){URL.revokeObjectURL(portrait.value);portrait.value=''}
 if(!account.value?.hasPortrait)return
 const response=await http.get('/profile/portrait',{responseType:'blob'})
 if(!disposed&&version===imageVersion)portrait.value=URL.createObjectURL(response.data)
}
function apply(profile:AccountProfile){account.value=profile;Object.assign(form,{displayName:profile.displayName||'',birthDate:profile.birthDate||'',gender:profile.gender||'UNKNOWN',spouseStatus:profile.spouseStatus||'',education:profile.education||'',biography:profile.biography||''})}
async function load(){loading.value=true;error.value='';try{apply(await profileApi.get());await refreshPortrait()}catch{error.value='个人信息加载失败，请重试。'}finally{loading.value=false}}
async function save(){
 if(saving.value||loading.value)return
 if(form.birthDate&&new Date(form.birthDate+'T00:00:00')>new Date())return ElMessage.warning('出生日期不能晚于今天')
 saving.value=true
 try{apply(await profileApi.update({displayName:form.displayName.trim(),birthDate:form.birthDate||null,gender:form.gender,spouseStatus:form.spouseStatus.trim(),education:form.education.trim(),biography:form.biography.trim()}));ElMessage.success('个人信息已保存')}finally{saving.value=false}
}
async function upload(file:UploadFile){
 if(!file.raw||uploading.value)return
 if(!['image/jpeg','image/png'].includes(file.raw.type))return ElMessage.warning('请选择 JPG 或 PNG 人像照片')
 if(file.raw.size>5*1024*1024)return ElMessage.warning('人像照片不能超过 5MB')
 uploading.value=true
 try{account.value=await profileApi.uploadPortrait(file.raw);await refreshPortrait();ElMessage.success('人像照片已更新')}finally{uploading.value=false}
}
onMounted(load)
onBeforeUnmount(()=>{disposed=true;imageVersion++;if(portrait.value)URL.revokeObjectURL(portrait.value)})
</script>
<template>
 <PageHeader title="我的个人信息" description="当前登录账号的个人资料，与族谱成员资料独立保存；仅本人可修改"><el-button type="primary" :loading="saving" :disabled="loading||!!error" @click="save">保存个人信息</el-button></PageHeader>
 <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
 <section v-loading="loading" class="panel profile-layout">
  <aside class="portrait-panel"><el-avatar :size="150" :src="portrait||undefined">{{(form.displayName||account?.username||'我').slice(0,1)}}</el-avatar><h2>{{form.displayName||account?.username||'个人资料'}}</h2><el-upload :auto-upload="false" :show-file-list="false" accept="image/jpeg,image/png" :on-change="upload" :disabled="uploading||loading"><el-button :loading="uploading">{{account?.hasPortrait?'更换人像照片':'添加人像照片'}}</el-button></el-upload><p class="muted">JPG / PNG，最大 5MB。<br>照片仅供当前账号私有访问。</p></aside>
  <div class="profile-form"><el-form label-position="top" :disabled="loading||saving"><el-form-item label="登录账号"><el-input :model-value="account?.username" disabled/></el-form-item><el-form-item label="姓名 / 昵称"><el-input v-model="form.displayName" maxlength="120" placeholder="填写姓名或昵称"/></el-form-item><el-form-item label="出生日期"><DatePicker v-model="form.birthDate" placeholder="请选择出生日期"/></el-form-item><el-form-item label="年龄"><el-input :model-value="age" readonly/><small class="muted">年龄根据出生日期自动计算，无需手动修改。</small></el-form-item><el-form-item label="性别"><el-select v-model="form.gender" style="width:100%"><el-option v-for="(label,value) in genderLabel" :key="value" :label="label" :value="value"/></el-select></el-form-item><el-form-item label="配偶情况"><el-input v-model="form.spouseStatus" maxlength="120" placeholder="例如未婚、已婚，或填写配偶情况"/></el-form-item><el-form-item label="学历"><el-input v-model="form.education" maxlength="120" placeholder="填写最高学历或教育经历"/></el-form-item><el-form-item label="个人介绍"><el-input v-model="form.biography" type="textarea" :rows="5" maxlength="5000" show-word-limit placeholder="介绍自己的经历、兴趣或其他信息"/></el-form-item></el-form></div>
 </section>
</template>
<style scoped>.profile-layout{display:grid;grid-template-columns:300px minmax(0,650px);gap:30px;padding:30px}.portrait-panel{text-align:center;border-right:1px solid #e2e8f0;padding:15px 30px 15px 0}.portrait-panel .el-upload{margin-top:14px}.portrait-panel p{font-size:13px;line-height:1.8}.profile-form{padding:10px}.profile-form small{display:block;margin-top:6px}@media(max-width:760px){.profile-layout{grid-template-columns:1fr;padding:20px}.portrait-panel{border-right:0;border-bottom:1px solid #e2e8f0;padding:0 0 20px}}</style>
