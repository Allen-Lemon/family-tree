<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { featureApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { requireTreeId } from '@/utils/backend'
const route=useRoute(),app=useAppStore(),text=ref(''),saving=ref(false),zoom=ref(100),chars=computed(()=>text.value.length),mediaId=computed(()=>String(route.query.mediaId||''))
onMounted(()=>{if(route.query.treeId)app.setTree(String(route.query.treeId));text.value=String(route.query.text||'')})
async function save(){saving.value=true;try{const task=await featureApi.correctOcr(requireTreeId(app.activeTreeId),String(route.params.id),text.value,{});text.value=task.correctedText||text.value;ElMessage.success('校对内容已保存')}finally{saving.value=false}}
</script>
<template><PageHeader title="OCR 左右校对" :description="`任务 #${route.params.id} · 媒体 #${mediaId}`"><el-button @click="$router.push('/ocr')">返回</el-button><el-button type="primary" :loading="saving" @click="save">保存校对</el-button></PageHeader><section class="proof-panel"><div class="proof-head"><b>原始影像</b><div><el-button size="small" @click="zoom=Math.max(50,zoom-10)">－</el-button><span>{{zoom}}%</span><el-button size="small" @click="zoom=Math.min(200,zoom+10)">＋</el-button></div><b>校对文本</b><span class="muted">{{chars}} 字</span></div><div class="proof-body"><div class="image-pane"><img v-if="mediaId" :src="featureApi.mediaUrl(app.activeTreeId,mediaId)" :style="{width:`${zoom}%`}" alt="待校对扫描件"/></div><div class="text-pane"><textarea v-model="text" spellcheck="false" aria-label="OCR 校对文本"></textarea></div></div><footer><span>保存时后端接收 correctedText 与 draftMember。</span><el-button type="primary" @click="save">保存当前进度</el-button></footer></section></template>
<style scoped>.proof-panel{background:white;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden}.proof-head{display:grid;grid-template-columns:1fr auto 1fr auto;gap:14px;padding:12px 18px}.proof-body{height:calc(100vh - 250px);min-height:480px;display:grid;grid-template-columns:1fr 1fr}.image-pane{overflow:auto;background:#334155;padding:28px;text-align:center}.image-pane img{max-width:none;min-width:50%}.text-pane textarea{width:100%;height:100%;border:0;resize:none;padding:28px;font-size:17px;line-height:2;outline:none}.proof-panel footer{display:flex;justify-content:space-between;padding:12px 18px;border-top:1px solid #e2e8f0}</style>
