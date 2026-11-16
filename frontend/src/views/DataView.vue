<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { featureApi } from '@/api'
import { http } from '@/api/client'
import { useAppStore } from '@/stores/app'
import { requireTreeId } from '@/utils/backend'
import type { BackupRecord } from '@/types'
const app=useAppStore(),backups=ref<BackupRecord[]>([]),importFile=ref<File>(),format=ref('JSON'),loading=ref(false)
function selectImportFile(upload: {raw?: File}) { importFile.value=upload.raw }
async function load(){backups.value=await featureApi.backups(requireTreeId(app.activeTreeId))}
async function exportData(){loading.value=true;try{const tree=requireTreeId(app.activeTreeId),response=await http.get(`/trees/${tree}/exports/sync`,{params:{format:format.value},responseType:'blob'}),a=document.createElement('a');a.href=URL.createObjectURL(response.data);a.download=`family.${format.value==='GEDCOM'?'ged':format.value==='JSON'?'json':'zip'}`;a.click();URL.revokeObjectURL(a.href)}finally{loading.value=false}}
async function importData(){if(!importFile.value)return ElMessage.warning('请选择导入文件');const fd=new FormData();fd.append('file',importFile.value);const job=await featureApi.importData(requireTreeId(app.activeTreeId),format.value,fd);if(job.status==='FAILED')ElMessage.error(job.errorMessage||'导入失败');else ElMessage.success(`导入任务 ${job.status}`)}
async function backup(){await featureApi.backup(requireTreeId(app.activeTreeId));ElMessage.success('备份已创建');load()}
async function download(item:BackupRecord){const response=await http.get(`/trees/${requireTreeId(app.activeTreeId)}/backups/${item.id}/download`,{responseType:'blob'}),a=document.createElement('a');a.href=URL.createObjectURL(response.data);a.download=`backup-${item.id}.json`;a.click();URL.revokeObjectURL(a.href)}
onMounted(load)
</script>
<template><PageHeader title="导入导出与备份" :description="`当前家谱 #${app.activeTreeId}；后端支持 JSON / GEDCOM / CSV_ZIP`"/><div class="grid-2"><section class="panel"><div class="panel-header"><h2>导入数据</h2></div><div class="panel-body"><el-select v-model="format" style="width:100%"><el-option value="JSON"/><el-option value="GEDCOM"/><el-option value="CSV_ZIP"/></el-select><el-upload drag :auto-upload="false" :limit="1" :on-change="selectImportFile" style="margin:18px 0"><div>拖放文件或点击选择</div></el-upload><el-button type="primary" style="width:100%" @click="importData">提交导入</el-button></div></section><section class="panel"><div class="panel-header"><h2>同步导出</h2></div><div class="panel-body"><el-radio-group v-model="format" class="format-list"><el-radio border value="JSON">JSON</el-radio><el-radio border value="GEDCOM">GEDCOM</el-radio><el-radio border value="CSV_ZIP">CSV ZIP</el-radio></el-radio-group><el-button type="primary" :loading="loading" style="width:100%" @click="exportData">生成并下载</el-button></div></section></div><section class="panel backups"><div class="panel-header"><h2>备份记录</h2><el-button type="primary" @click="backup">立即创建备份</el-button></div><el-table :data="backups" stripe><el-table-column prop="createdAt" label="创建时间"/><el-table-column prop="createdBy" label="创建人 ID"/><el-table-column prop="sizeBytes" label="字节数"/><el-table-column label="操作"><template #default="{row}"><el-button link type="primary" @click="download(row)">下载</el-button></template></el-table-column></el-table><el-empty v-if="!backups.length" description="暂无备份"/></section></template>
<style scoped>.format-list{display:grid;gap:12px;margin:24px 0}.backups{margin-top:18px}</style>
