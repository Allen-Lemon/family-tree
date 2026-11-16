<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { featureApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { requireTreeId } from '@/utils/backend'
import type { Media, OcrTask } from '@/types'
const app=useAppStore(),file=ref<File>(),provider=ref('manual'),media=ref<Media>(),task=ref<OcrTask>(),loading=ref(false)
function selectFile(upload: {raw?: File}) { file.value=upload.raw }
async function start(){if(!file.value)return ElMessage.warning('请选择图片或 PDF');loading.value=true;try{const fd=new FormData();fd.append('file',file.value);media.value=await featureApi.uploadMedia(requireTreeId(app.activeTreeId),fd);task.value=await featureApi.startOcr(requireTreeId(app.activeTreeId),media.value.id,provider.value);ElMessage.info(`OCR 任务已创建，当前状态：${task.value.status}；自动识别适配器尚未完成`)}finally{loading.value=false}}
</script>
<template><PageHeader title="OCR 史料识别" :description="`当前家谱 #${app.activeTreeId}；流程为先上传媒体，再以 mediaId 创建 OCR`"/><section class="panel"><div class="upload-grid"><el-upload drag :auto-upload="false" :limit="1" accept="image/*,.pdf" :on-change="selectFile"><div class="upload-text"><b>上传扫描图片或 PDF</b><span>后端允许 JPEG、PNG、GIF、PDF 等类型</span></div></el-upload><div><el-form label-position="top"><el-form-item label="OCR provider"><el-input v-model="provider"/></el-form-item><el-alert title="当前后端 provider 适配器返回可校对的占位识别结果。" type="info" :closable="false"/><el-button type="primary" :loading="loading" style="width:100%;margin-top:18px" @click="start">上传并开始识别</el-button></el-form></div></div></section><section v-if="task" class="panel result"><div class="panel-header"><h2>任务 #{{task.id}}</h2><el-tag>{{task.status}}</el-tag></div><div class="panel-body"><p>{{task.correctedText}}</p><el-button type="primary" @click="$router.push(`/ocr/${task.id}?treeId=${task.treeId}&mediaId=${task.mediaId}`)">进入左右校对</el-button></div></section></template>
<style scoped>.upload-grid{display:grid;grid-template-columns:1.4fr 1fr;gap:24px;padding:24px}.upload-text{padding:40px;display:flex;flex-direction:column;gap:10px}.result{margin-top:18px}@media(max-width:720px){.upload-grid{grid-template-columns:1fr}}</style>
