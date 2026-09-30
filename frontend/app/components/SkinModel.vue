<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

/**
 * 用纯 CSS 3D 把 64x64 / 64x32 皮肤贴图贴回体素模型：
 * 每个身体部位是一个长方体，六个面按 Minecraft 皮肤展开图切片贴图，
 * 支持拖拽旋转、滚轮缩放、视角吸附和外层（hat/jacket/sleeve/pants）开关。
 */
type Rect = { sx: number; sy: number; w: number; h: number }
type FaceName = 'front' | 'back' | 'right' | 'left' | 'top' | 'bottom'

/** 一个待贴图的部位盒：中心相对角色锚点（单位=皮肤像素，x 右正 / y 下正，锚点在角色几何中心）。 */
type BoxSpec = {
  id: string
  x: number
  y: number
  w: number
  h: number
  d: number
  rects: Record<FaceName, Rect>
  /** overlay 是外层盒，几何上外扩 0.5px 避免与基础层共面。 */
  overlay?: boolean
  /** 旧版 64x32 皮肤没有独立左肢，左肢贴图取自右肢并镜像。 */
  mirror?: boolean
}

const props = withDefaults(defineProps<{
  /** 材质哈希，对应后端 /textures/{hash}。 */
  hash?: string
  /** steve 经典 4px 臂，alex 3px 臂，cape 披风。 */
  type?: 'steve' | 'alex' | 'cape'
  /** 直接指定贴图地址（默认按 hash 拼公开端点）。 */
  src?: string
  initialYaw?: number
  initialPitch?: number
}>(), {
  hash: '',
  type: 'steve',
  src: '',
  initialYaw: -18,
  initialPitch: -4,
})

const { t } = useI18n()

/**
 * 皮肤展开图通用拆解：给定区域左上角与盒体尺寸，
 * 按标准布局切出六个面（side 行在 top 行下方，高度 d）。
 */
function unpack(rx: number, ry: number, w: number, h: number, d: number): Record<FaceName, Rect> {
  return {
    top: { sx: rx + d, sy: ry, w, h: d },
    bottom: { sx: rx + d + w, sy: ry, w, h: d },
    right: { sx: rx, sy: ry + d, w: d, h },
    front: { sx: rx + d, sy: ry + d, w, h },
    left: { sx: rx + d + w, sy: ry + d, w: d, h },
    back: { sx: rx + d + w + d, sy: ry + d, w, h },
  }
}

function limbBoxes(id: 'arm' | 'leg', side: 'l' | 'r', armW: number, legacy: boolean): BoxSpec[] {
  const w = id === 'arm' ? armW : 4
  const cx = id === 'arm' ? (side === 'l' ? 6 : -6) : (side === 'l' ? 2 : -2)
  // 手臂与躯干同高（中心 y=-2），腿在下半身（中心 y=10）。
  const cy = id === 'arm' ? -2 : 10
  const baseOrigin = id === 'arm'
    ? (side === 'l' ? { x: 32, y: 48 } : { x: 40, y: 16 })
    : (side === 'l' ? { x: 16, y: 48 } : { x: 0, y: 16 })
  const overlayOrigin = id === 'arm'
    ? (side === 'l' ? { x: 48, y: 48 } : { x: 40, y: 32 })
    : (side === 'l' ? { x: 0, y: 48 } : { x: 0, y: 32 })
  // 旧版皮肤：左肢复用右肢区域并镜像。
  const useOrigin = legacy && side === 'l' ? (id === 'arm' ? { x: 40, y: 16 } : { x: 0, y: 16 }) : baseOrigin
  const boxes: BoxSpec[] = [{
    id: `${id}-${side}`,
    x: cx, y: cy,
    w, h: 12, d: 4,
    rects: unpack(useOrigin.x, useOrigin.y, w, 12, 4),
    mirror: legacy && side === 'l',
  }]
  if (!legacy) {
    boxes.push({
      id: `${id}-${side}-overlay`,
      x: cx, y: cy,
      w: w + 0.5, h: 12.5, d: 4.5,
      rects: unpack(overlayOrigin.x, overlayOrigin.y, w, 12, 4),
      overlay: true,
    })
  }
  return boxes
}

/** 组装整个角色（或披风）的盒子列表。 */
function buildBoxes(type: 'steve' | 'alex' | 'cape', legacy: boolean): BoxSpec[] {
  if (type === 'cape') {
    return [{ id: 'cape', x: 0, y: 0, w: 10, h: 16, d: 1, rects: unpack(0, 0, 10, 16, 1) }]
  }
  const armW = type === 'alex' ? 3 : 4
  const armOffset = armW === 3 ? 5.5 : 6
  const head: BoxSpec[] = [
    { id: 'head', x: 0, y: -12, w: 8, h: 8, d: 8, rects: unpack(0, 0, 8, 8, 8) },
  ]
  const body: BoxSpec[] = [{ id: 'body', x: 0, y: -2, w: 8, h: 12, d: 4, rects: unpack(16, 16, 8, 12, 4) }]
  const limbs = [...limbBoxes('arm', 'r', armW, legacy), ...limbBoxes('arm', 'l', armW, legacy), ...limbBoxes('leg', 'r', armW, legacy), ...limbBoxes('leg', 'l', armW, legacy)]
  if (legacy) return [...head, ...body, ...limbs]
  const overlays: BoxSpec[] = [
    { id: 'head-overlay', x: 0, y: -12, w: 8.5, h: 8.5, d: 8.5, rects: unpack(32, 0, 8, 8, 8), overlay: true },
    { id: 'body-overlay', x: 0, y: -2, w: 8.5, h: 12.5, d: 4.5, rects: unpack(16, 32, 8, 12, 4), overlay: true },
  ]
  return [...head, ...body, ...overlays, ...limbs]
}

const FACE_KEYS: FaceName[] = ['front', 'back', 'right', 'left', 'top', 'bottom']

/** 每个面的旋转与半尺寸轴：front/back 推出 d/2，side 面推出 w/2，top/bottom 推出 h/2。 */
function faceGeometry(box: BoxSpec, face: FaceName): { rot: string; fw: number; fh: number; z: number } {
  switch (face) {
    case 'back': return { rot: 'rotateY(180deg)', fw: box.w, fh: box.h, z: box.d / 2 }
    case 'right': return { rot: 'rotateY(-90deg)', fw: box.d, fh: box.h, z: box.w / 2 }
    case 'left': return { rot: 'rotateY(90deg)', fw: box.d, fh: box.h, z: box.w / 2 }
    case 'top': return { rot: 'rotateX(90deg)', fw: box.w, fh: box.d, z: box.h / 2 }
    case 'bottom': return { rot: 'rotateX(-90deg)', fw: box.w, fh: box.d, z: box.h / 2 }
    default: return { rot: '', fw: box.w, fh: box.h, z: box.d / 2 }
  }
}

const imageUrl = computed(() => props.src || (props.hash ? `/textures/${props.hash}` : ''))

/** 旧版 64x32 皮肤没有独立左肢；按图片真实高度判定。 */
const legacy = ref(false)
const zoom = ref(1)
const yaw = ref(props.initialYaw)
const pitch = ref(props.initialPitch)
const dragging = ref(false)
const showOverlay = ref(true)
/** 基准像素比：贴图像素对应的 CSS 像素，缩放通过 figure 的 scale3d 完成。 */
const baseScale = computed(() => (props.type === 'cape' ? 12.5 : 9))

let probeToken = 0
function probeLegacy() {
  const url = imageUrl.value
  if (!url) {
    legacy.value = false
    return
  }
  const token = ++probeToken
  const image = new Image()
  image.addEventListener('load', () => {
    if (token === probeToken) legacy.value = image.naturalHeight > 0 && image.naturalHeight <= 32
  })
  image.src = url
}

watch(imageUrl, probeLegacy)
onMounted(probeLegacy)

watch(() => props.type, () => {
  zoom.value = 1
  yaw.value = props.initialYaw
  pitch.value = props.initialPitch
})

const boxes = computed(() => buildBoxes(props.type, legacy.value))
const visibleBoxes = computed(() => boxes.value.filter((box) => !box.overlay || showOverlay.value))
const hasOverlay = computed(() => boxes.value.some((box) => box.overlay))

const figureStyle = computed(() => ({
  transform: `rotateX(${pitch.value}deg) rotateY(${yaw.value}deg) scale3d(${zoom.value}, ${zoom.value}, ${zoom.value})`,
  transition: dragging.value ? 'none' : 'transform .3s ease',
}))

function anchorStyle(box: BoxSpec) {
  const s = baseScale.value
  return { left: `${box.x * s}px`, top: `${box.y * s}px` }
}

function faceStyle(box: BoxSpec, face: FaceName) {
  const s = baseScale.value
  const rect = box.rects[face]
  const geo = faceGeometry(box, face)
  return {
    width: `${geo.fw * s}px`,
    height: `${geo.fh * s}px`,
    backgroundImage: `url(${imageUrl.value})`,
    backgroundPosition: `${-rect.sx * s}px ${-rect.sy * s}px`,
    backgroundSize: `${64 * s}px auto`,
    transform: `translate(-50%, -50%) ${box.mirror ? 'scaleX(-1) ' : ''}${geo.rot} translateZ(${geo.z * s}px)`,
  }
}

const ZOOM_MIN = 0.55
const ZOOM_MAX = 1.9

function clampZoom(value: number) {
  return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, value))
}

function zoomStep(direction: number) {
  zoom.value = clampZoom(zoom.value * (direction > 0 ? 1.15 : 1 / 1.15))
}

function onWheel(event: WheelEvent) {
  zoomStep(event.deltaY < 0 ? 1 : -1)
}

let lastX = 0
let lastY = 0

function onPointerDown(event: PointerEvent) {
  dragging.value = true
  lastX = event.clientX
  lastY = event.clientY
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value) return
  yaw.value += (event.clientX - lastX) * 0.45
  // 向下拖动时俯视头顶、向上拖动时仰视脚底。
  pitch.value = Math.min(45, Math.max(-45, pitch.value - (event.clientY - lastY) * 0.3))
  lastX = event.clientX
  lastY = event.clientY
}

function onPointerUp(event: PointerEvent) {
  dragging.value = false
  ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)
}

/** 视角吸附：从当前 yaw 走最短路径转到目标角度。 */
function snapView(deg: number) {
  const base = deg + Math.round((yaw.value - deg) / 360) * 360
  yaw.value = base
  pitch.value = 0
}
</script>

<template>
  <div :class="{'skin-model--empty': !imageUrl}" class="skin-model">
    <div v-if="!imageUrl" aria-hidden="true" class="skin-model__placeholder">?</div>
    <template v-else>
      <div
          class="skin-model__scene"
          role="img"
          :aria-label="t('closet.modelAria')"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
          @wheel.prevent="onWheel"
      >
        <div :style="figureStyle" class="skin-model__figure">
          <template v-for="box in visibleBoxes" :key="box.id">
            <span :style="anchorStyle(box)" class="skin-model__part">
              <span v-for="face in FACE_KEYS" :key="face" :style="faceStyle(box, face)" class="skin-model__face"/>
            </span>
          </template>
        </div>
      </div>
      <div class="skin-model__controls">
        <div class="skin-model__group" role="group" :aria-label="t('closet.viewFront')">
          <button type="button" @click="snapView(0)">{{ t('closet.viewFront') }}</button>
          <button type="button" @click="snapView(-90)">{{ t('closet.viewLeft') }}</button>
          <button type="button" @click="snapView(90)">{{ t('closet.viewRight') }}</button>
          <button type="button" @click="snapView(180)">{{ t('closet.viewBack') }}</button>
        </div>
        <div class="skin-model__group">
          <button
              v-if="hasOverlay"
              :class="{ active: showOverlay }"
              :aria-pressed="showOverlay"
              type="button"
              @click="showOverlay = !showOverlay"
          >{{ t('closet.overlay') }}
          </button>
          <button :aria-label="t('closet.zoomOut')" type="button" @click="zoomStep(-1)">−</button>
          <button :aria-label="t('closet.zoomIn')" type="button" @click="zoomStep(1)">＋</button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.skin-model {
  position: relative;
  width: 100%;
  height: 100%;
}

.skin-model__scene {
  position: absolute;
  inset: 0 0 46px;
  overflow: hidden;
  cursor: grab;
  touch-action: none;
}

.skin-model__scene:active {
  cursor: grabbing;
}

.skin-model__figure {
  position: absolute;
  left: 50%;
  top: calc((100% - 46px) / 2);
  width: 0;
  height: 0;
  transform-style: preserve-3d;
  will-change: transform;
}

.skin-model__part {
  position: absolute;
  width: 0;
  height: 0;
  transform-style: preserve-3d;
}

.skin-model__face {
  position: absolute;
  left: 0;
  top: 0;
  display: block;
  background-repeat: no-repeat;
  image-rendering: pixelated;
  backface-visibility: hidden;
}

.skin-model__placeholder {
  position: absolute;
  inset: 0 0 46px;
  display: grid;
  place-items: center;
  border: 1px dashed var(--border-strong);
  color: var(--text-faint);
  font-size: 16px;
}

.skin-model__controls {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 46px;
  padding: 0 10px;
  border-top: 1px solid var(--border-soft);
}

.skin-model__group {
  display: flex;
  gap: 6px;
}

.skin-model__group button {
  min-height: 26px;
  padding: 0 9px;
  border: 1px solid var(--border-strong);
  background: var(--surface-panel);
  color: var(--text-muted);
  font: 10px 'DM Mono', monospace;
  transition: color .15s, border-color .15s, background .15s;
}

.skin-model__group button:hover {
  color: var(--text-strong);
  border-color: var(--accent-strong);
}

.skin-model__group button.active {
  color: var(--accent-tag-ink);
  border-color: var(--accent-strong);
  background: var(--accent-soft-bg);
}
</style>
