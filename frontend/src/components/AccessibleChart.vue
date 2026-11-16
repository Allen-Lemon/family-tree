<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{ option: echarts.EChartsOption; rows: Record<string, string | number>[]; columns: { key: string; label: string }[]; title: string }>()
const chartEl = ref<HTMLElement>(), tableMode = ref(false)
let chart: echarts.ECharts | undefined
const finalOption = computed(() => ({ color: ['#2a78d6','#eb6834','#1baf7a','#eda100'], textStyle: { color: '#334155' }, ...props.option }))
function render() { if (!chartEl.value || tableMode.value) return; chart ||= echarts.init(chartEl.value); chart.setOption(finalOption.value, true); chart.resize() }
function resize() { chart?.resize() }
onMounted(() => { render(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { chart?.dispose(); window.removeEventListener('resize', resize) })
watch(() => props.option, render, { deep: true })
watch(tableMode, (value) => { if (!value) nextTick(render) })
</script>
<template><section class="panel"><div class="panel-header"><h2>{{ title }}</h2><el-segmented v-model="tableMode" :options="[{label:'图表',value:false},{label:'表格',value:true}]" size="small" aria-label="切换图表和表格" /></div><div class="panel-body"><div v-show="!tableMode" ref="chartEl" class="chart" role="img" :aria-label="`${title}图表`"></div><el-table v-if="tableMode" :data="rows" stripe><el-table-column v-for="col in columns" :key="col.key" :prop="col.key" :label="col.label" /></el-table><p v-if="!tableMode" class="chart-note">图表支持悬停查看精确数据；绿色、黄色系列另提供表格视图以辅助识别。</p></div></section></template>
<style scoped>.chart-note{font-size:12px;color:#64748b;margin:8px 0 0}</style>
