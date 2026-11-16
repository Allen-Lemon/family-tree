<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { featureApi } from '@/api'
import type { NotificationItem } from '@/types'
const rows=ref<NotificationItem[]>([]),unreadOnly=ref(false),visible=computed(()=>rows.value.filter(i=>!unreadOnly.value||!i.readAt))
async function load(){rows.value=await featureApi.notifications()}
async function read(item:NotificationItem){if(item.readAt)return;const updated=await featureApi.readNotification(item.id);Object.assign(item,updated)}
onMounted(load)
</script>
<template><PageHeader title="通知中心" description="后端通知使用 message、createdAt 与 readAt 字段"><el-switch v-model="unreadOnly" active-text="只看未读"/></PageHeader><section class="panel notice-list"><button v-for="item in visible" :key="item.id" :class="{unread:!item.readAt}" @click="read(item)"><span class="notice-icon">讯</span><span><b>{{item.message}}</b><small>{{item.createdAt}} · {{item.type}}</small></span><i v-if="!item.readAt"></i></button><el-empty v-if="!visible.length" description="暂无通知"/></section></template>
<style scoped>.notice-list button{width:100%;display:grid;grid-template-columns:auto 1fr auto;gap:14px;padding:16px 20px;border:0;border-bottom:1px solid #e2e8f0;background:#fff;text-align:left}.notice-list button.unread{background:#f5f9ff}.notice-list span:nth-child(2){display:flex;flex-direction:column;gap:5px}.notice-list small{color:#64748b}.notice-list i{width:8px;height:8px;background:#2563eb;border-radius:50%}.notice-icon{width:36px;height:36px;border-radius:9px;background:#eff6ff;color:#2563eb;display:grid;place-items:center}</style>
