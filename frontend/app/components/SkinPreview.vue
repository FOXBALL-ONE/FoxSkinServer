<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

/** 一块要贴到模型上的皮肤区域：目标位置 + 64x64 皮肤图中的源区域，单位都是皮肤像素。 */
type Part = {
  dx: number
  dy: number
  sx: number
  sy: number
  w: number
  h: number
  /** 64x32 旧版皮肤没有左臂/左腿，需要把右臂/右腿镜像过去。 */
  mirror?: boolean
}

const props = withDefaults(defineProps<{
  /** 材质哈希，对应后端 /textures/{hash} */
  hash?: string
  variant?: 'body' | 'face' | 'cape'
  /** 一个皮肤像素渲染成多少 CSS 像素 */
  scale?: number
}>(), {
  hash: '',
  variant: 'body',
  scale: 4,
})

/** 现代 64x64 皮肤：基础层 + 帽子/外套/袖子/裤腿的覆盖层。 */
const BODY_MODERN: Part[] = [
  { dx: 4, dy: 0, sx: 8, sy: 8, w: 8, h: 8 },
  { dx: 4, dy: 8, sx: 20, sy: 20, w: 8, h: 12 },
  { dx: 0, dy: 8, sx: 44, sy: 20, w: 4, h: 12 },
  { dx: 12, dy: 8, sx: 36, sy: 52, w: 4, h: 12 },
  { dx: 4, dy: 20, sx: 4, sy: 20, w: 4, h: 12 },
  { dx: 8, dy: 20, sx: 20, sy: 52, w: 4, h: 12 },
  { dx: 4, dy: 0, sx: 40, sy: 8, w: 8, h: 8 },
  { dx: 4, dy: 8, sx: 20, sy: 36, w: 8, h: 12 },
  { dx: 0, dy: 8, sx: 44, sy: 36, w: 4, h: 12 },
  { dx: 12, dy: 8, sx: 52, sy: 52, w: 4, h: 12 },
  { dx: 4, dy: 20, sx: 4, sy: 36, w: 4, h: 12 },
  { dx: 8, dy: 20, sx: 4, sy: 52, w: 4, h: 12 },
]

/** 旧版 64x32 皮肤：左臂/左腿由右臂/右腿镜像而来，覆盖层只有帽子。 */
const BODY_LEGACY: Part[] = [
  { dx: 4, dy: 0, sx: 8, sy: 8, w: 8, h: 8 },
  { dx: 4, dy: 8, sx: 20, sy: 20, w: 8, h: 12 },
  { dx: 0, dy: 8, sx: 44, sy: 20, w: 4, h: 12 },
  { dx: 12, dy: 8, sx: 44, sy: 20, w: 4, h: 12, mirror: true },
  { dx: 4, dy: 20, sx: 4, sy: 20, w: 4, h: 12 },
  { dx: 8, dy: 20, sx: 4, sy: 20, w: 4, h: 12, mirror: true },
  { dx: 4, dy: 0, sx: 40, sy: 8, w: 8, h: 8 },
]

const FACE: Part[] = [{ dx: 0, dy: 0, sx: 8, sy: 8, w: 8, h: 8 }]
const CAPE: Part[] = [{ dx: 0, dy: 0, sx: 1, sy: 1, w: 10, h: 16 }]

const SIZE = {
  body: { w: 16, h: 32 },
  face: { w: 8, h: 8 },
  cape: { w: 10, h: 16 },
}

const legacy = ref(false)

const parts = computed(() => {
  if (props.variant === 'face') return FACE
  if (props.variant === 'cape') return CAPE
  return legacy.value ? BODY_LEGACY : BODY_MODERN
})

const box = computed(() => SIZE[props.variant])
const imageUrl = computed(() => (props.hash ? `/textures/${props.hash}` : ''))

function layerStyle(part: Part) {
  return {
    left: `${part.dx * props.scale}px`,
    top: `${part.dy * props.scale}px`,
    width: `${part.w * props.scale}px`,
    height: `${part.h * props.scale}px`,
    backgroundImage: `url(${imageUrl.value})`,
    backgroundPosition: `${-part.sx * props.scale}px ${-part.sy * props.scale}px`,
    backgroundSize: `${64 * props.scale}px auto`,
    transform: part.mirror ? 'scaleX(-1)' : undefined,
  }
}

// 旧版皮肤没有独立左肢，需按真实高度切换镜像布局；探测不到时按现代格式渲染。
onMounted(() => {
  if (props.variant !== 'body' || !imageUrl.value) return
  const image = new Image()
  image.addEventListener('load', () => {
    legacy.value = image.naturalHeight <= 32
  })
  image.src = imageUrl.value
})
</script>

<template>
  <div
      :style="{ width: `${box.w * scale}px`, height: `${box.h * scale}px` }"
      :class="['skin-preview', { 'skin-preview--empty': !hash }]"
  >
    <span v-if="!hash" aria-hidden="true" class="skin-preview__placeholder">?</span>
    <span v-for="(part, index) in parts" v-else :key="index" :style="layerStyle(part)" class="skin-preview__layer"/>
  </div>
</template>

<style scoped>
.skin-preview { position: relative; filter: drop-shadow(6px 9px 5px rgba(34, 54, 43, .18)); }
.skin-preview--empty { display: grid; place-items: center; border: 1px dashed #b9c4b7; filter: none; }
.skin-preview__layer { position: absolute; display: block; background-repeat: no-repeat; image-rendering: pixelated; }
.skin-preview__placeholder { color: #9aa79a; font-size: 13px; }
</style>
