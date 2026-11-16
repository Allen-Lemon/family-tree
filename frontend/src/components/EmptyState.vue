<script setup lang="ts">
defineProps<{ title?: string; description?: string }>()
</script>
<template><el-empty :description="title || '暂无数据'"><p v-if="description" class="muted">{{ description }}</p><slot /></el-empty></template>
