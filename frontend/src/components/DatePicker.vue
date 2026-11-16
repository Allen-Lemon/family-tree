<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ modelValue?: string | null; placeholder?: string }>(), {
  modelValue: '',
  placeholder: '请选择日期',
})
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const value = computed({
  // Historical member records may use dots or slashes; display them without rewriting the database.
  get: () => (props.modelValue || '').replace(/^(\d{4})[./](\d{2})[./](\d{2})$/, '$1-$2-$3'),
  set: (date: string | null) => emit('update:modelValue', date || ''),
})
</script>

<template>
  <el-date-picker
    v-model="value"
    type="date"
    format="YYYY-MM-DD"
    value-format="YYYY-MM-DD"
    :placeholder="placeholder"
    :editable="false"
    clearable
    style="width: 100%"
  />
</template>
