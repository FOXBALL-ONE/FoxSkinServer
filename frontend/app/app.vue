<script setup lang="ts">
import { computed } from 'vue'

// 主题在服务端渲染时就写进 <html data-theme>，避免首屏先亮后暗的闪烁。
// 这里必须传 computed：直接读成字符串的话 unhead 拿到的是一次性的常量，切换主题不会更新 dom。
const preferences = usePreferencesStore()
const themeAttribute = computed(() => preferences.colorMode)

useHead({
  htmlAttrs: {
    'data-theme': themeAttribute,
  },
})
</script>
<template>
  <NMessageProvider>
      <NuxtPage />
  </NMessageProvider>
</template>
