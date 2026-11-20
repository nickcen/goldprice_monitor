<template>
  <div class="page">
    <div class="eyebrow">WORKSPACE</div>
    <h1>{{ title }}</h1>
    <p class="lead">{{ description }}</p>
    <el-empty description="暂无数据" />
  </div>
</template>
<script setup lang="ts">
defineProps<{ title: string; description: string }>()
</script>
