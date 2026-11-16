<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import PageHeader from '@/components/PageHeader.vue'
import AccessibleChart from '@/components/AccessibleChart.vue'
import LoadingPanel from '@/components/LoadingPanel.vue'
import { featureApi, treeApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { genderLabel } from '@/utils/backend'
import type { DashboardStats } from '@/types'
const app=useAppStore(),loading=ref(true),stats=ref<DashboardStats>({trees:0,members:0,generations:0,pending:0,gender:[],generationsSeries:[]})
const cards=computed(()=>[{label:'家谱数量',value:stats.value.trees,icon:'谱'},{label:'当前家谱成员',value:stats.value.members,icon:'人'},{label:'关系数量',value:stats.value.generations,icon:'联'},{label:'待审核建议',value:stats.value.pending,icon:'审'}])
const genderOption=computed<EChartsOption>(()=>({tooltip:{trigger:'item',formatter:'{b}<br/>{c} 人（{d}%）'},legend:{bottom:0},series:[{name:'成员性别',type:'pie',radius:['46%','70%'],itemStyle:{borderColor:'#fff',borderWidth:2},label:{color:'#334155',formatter:'{b} {c}人'},data:stats.value.gender}]}))
const tableRows=computed(()=>stats.value.gender)
onMounted(async()=>{try{const trees=await treeApi.list();if(!app.activeTreeId&&trees[0])app.setTree(trees[0].id);let members=0,relationships=0,pending=0,gender:DashboardStats['gender']=[];if(app.activeTreeId){const s=await treeApi.statistics(app.activeTreeId);members=s.members;relationships=s.relationships;gender=Object.entries(s.byGender||{}).map(([key,value])=>({name:genderLabel[key as keyof typeof genderLabel]||key,value}));try{pending=(await featureApi.pendingSuggestions(app.activeTreeId)).length}catch{pending=0}}stats.value={trees:trees.length,members,generations:relationships,pending,gender,generationsSeries:[]}}finally{loading.value=false}})
</script>
<template><PageHeader title="总览统计" description="汇总实际后端的家谱、当前家谱成员、关系与待审建议"><el-button type="primary" @click="$router.push('/members')">成员管理</el-button></PageHeader><LoadingPanel v-if="loading"/><template v-else><div class="stat-grid"><article v-for="card in cards" :key="card.label" class="stat-card"><span class="stat-icon">{{card.icon}}</span><div class="stat-content"><small>{{card.label}}</small><strong>{{card.value}}</strong></div></article></div><AccessibleChart title="当前家谱成员性别构成" :option="genderOption" :rows="tableRows" :columns="[{key:'name',label:'类别'},{key:'value',label:'人数'}]"/><section class="panel quick"><div class="panel-header"><h2>快捷工作台</h2></div><div class="quick-grid"><router-link to="/search"><b>谱系查询</b><span>姓名、祖先、后代及两人路径</span></router-link><router-link to="/ocr"><b>史料 OCR</b><span>上传媒体并创建校对任务</span></router-link><router-link to="/review"><b>建议审核</b><span>处理待审修改建议</span></router-link><router-link to="/data"><b>安全备份</b><span>导入、导出和下载备份</span></router-link></div></section></template></template>
<style scoped>.quick{margin-top:18px}.quick-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:#e2e8f0}.quick-grid a{padding:20px;background:white;display:flex;flex-direction:column;gap:8px}.quick-grid span{font-size:13px;color:#64748b}@media(max-width:800px){.quick-grid{grid-template-columns:1fr 1fr}}</style>
