<script setup lang="ts">
import { computed } from 'vue'
import DatePicker from '@/components/DatePicker.vue'

const props = withDefaults(defineProps<{
  modelValue?: string | null
  type: 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN'
  disabled?: boolean
  placeholder?: string
  clearable?: boolean
}>(), { disabled: false, clearable: true, placeholder: '请输入字段值' })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const textValue = computed({
  get: () => props.modelValue ?? '',
  set: (value: string | null | undefined) => emit('update:modelValue', value ?? ''),
})
const numberValue = computed({
  get: () => {
    if (props.modelValue == null || props.modelValue.trim() === '') return undefined
    const value = Number(props.modelValue)
    return Number.isFinite(value) ? value : undefined
  },
  set: (value: number | null | undefined) => {
    emit('update:modelValue', value == null || !Number.isFinite(value) ? '' : String(value))
  },
})
</script>

<template>
  <el-input-number
    v-if="type === 'NUMBER'"
    v-model="numberValue"
    :disabled="disabled"
    :placeholder="placeholder"
    :controls="false"
    style="width: 100%"
  />
  <DatePicker
    v-else-if="type === 'DATE'"
    v-model="textValue"
    :disabled="disabled"
    :placeholder="placeholder"
    :clearable="clearable"
  />
  <el-select
    v-else-if="type === 'BOOLEAN'"
    v-model="textValue"
    :disabled="disabled"
    :placeholder="placeholder"
    :clearable="clearable"
    style="width: 100%"
  >
    <el-option label="是" value="true" />
    <el-option label="否" value="false" />
  </el-select>
  <el-input
    v-else
    v-model="textValue"
    :disabled="disabled"
    :placeholder="placeholder"
    :clearable="clearable"
  />
</template>
