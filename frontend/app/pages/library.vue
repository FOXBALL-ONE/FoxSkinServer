<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const http = useHttp()

type LibraryEntry = {
  id: number
  name: string
  type: string
  hash: string
  size: number
  uploader_id: number
  upload_at: string
  likes: number
}

type Pagination = { page: number; total: number; total_pages: number }
type Feedback = { tone: 'ok' | 'error'; text: string }

const filters = computed(() => [
  { value: '', label: t('library.filterAll') },
  { value: 'skin', label: t('library.filterSkin') },
  { value: 'cape', label: t('library.filterCape') },
])

/** 后端 skinlib 列表允许的分页大小，默认 30。 */
const PAGE_SIZES = [30, 40, 50]
const pageSize = ref(30)
const page = ref(1)
const totalPages = ref(1)
const total = ref(0)
const keyword = ref('')
const activeType = ref('')
const loading = ref(true)
const entries = ref<LibraryEntry[]>([])
const feedback = ref<Feedback | null>(null)

/** 弹层里正在查看的材质；支持 ?view={id} 深链直达。 */
const viewerEntry = ref<LibraryEntry | null>(null)

const isCape = (entry: { type: string }) => entry.type === 'cape'
const modelTypeOf = (entry: { type: string }): 'cape' | 'alex' | 'steve' =>
  isCape(entry) ? 'cape' : entry.type === 'alex' ? 'alex' : 'steve'

function describe(error: unknown, fallback: string) {
  const value = error as { data?: { message?: string }; statusMessage?: string; message?: string }
  return value.data?.message || value.statusMessage || value.message || fallback
}

async function loadLibrary() {
  loading.value = true
  try {
    const data = await http.get<{ list: LibraryEntry[]; pagination: Pagination }>('/skinlib', {
      type: activeType.value,
      keyword: keyword.value.trim(),
      page: page.value,
      size: pageSize.value,
    })
    entries.value = data.list
    total.value = data.pagination.total
    totalPages.value = Math.max(1, data.pagination.total_pages)
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('library.loadFailed')) }
  } finally {
    loading.value = false
  }
}

/** 筛选/搜索/换每页大小都回到第一页。 */
function applyFilters() {
  page.value = 1
  void loadLibrary()
}

function gotoPage(delta: number) {
  const next = page.value + delta
  if (next < 1 || next > totalPages.value) return
  page.value = next
  void loadLibrary()
}

async function collect(entry: LibraryEntry) {
  feedback.value = null
  try {
    await http.post('/users/me/closet', { texture_id: entry.id })
    feedback.value = { tone: 'ok', text: t('library.collected', { name: entry.name }) }
    viewerEntry.value = null
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('library.collectFailed')) }
  }
}

function openViewer(entry: LibraryEntry) {
  viewerEntry.value = entry
}

function closeViewer() {
  viewerEntry.value = null
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeViewer()
}

// 弹层打开时锁住页面滚动，ESC 关闭。
watch(viewerEntry, (value) => {
  if (value) {
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKeydown)
  } else {
    document.body.style.overflow = ''
    window.removeEventListener('keydown', onKeydown)
  }
})

onUnmounted(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})

onMounted(async () => {
  await auth.hydrate()
  if (!auth.isAuthenticated || !auth.user) {
    await router.replace({ path: '/login', query: { redirect: '/library' } })
    return
  }
  await loadLibrary()
  const wanted = Number(route.query.view)
  const found = Number.isFinite(wanted) ? entries.value.find((entry) => entry.id === wanted) : null
  if (found) viewerEntry.value = found
})
</script>

<template>
  <AppShell :active="'library'" :crumb="t('shell.nav.library')">
    <section class="page-head">
      <div>
        <p class="eyebrow">{{ t('library.eyebrow', { total }) }}</p>
        <h1>{{ t('library.headlineLine1') }}<br><em>{{ t('library.headlineLine2') }}</em></h1>
      </div>
      <NuxtLink class="primary-button" to="/closet">
        <span>{{ t('library.uploadCta') }}</span><span aria-hidden="true">↗</span>
      </NuxtLink>
    </section>

    <p v-if="feedback" :class="`notice notice--${feedback.tone}`" role="status">{{ feedback.text }}</p>

    <div class="toolbar">
      <div class="filters" role="tablist">
        <button v-for="item in filters" :key="item.value" :aria-selected="activeType === item.value"
                :class="{ active: activeType === item.value }" role="tab" type="button"
                @click="activeType = item.value; applyFilters()">{{ item.label }}
        </button>
      </div>
      <form class="search" @submit.prevent="applyFilters">
        <input v-model="keyword" :placeholder="t('library.searchPlaceholder')" type="search">
        <button type="submit">{{ t('common.search') }}</button>
      </form>
    </div>

    <p v-if="loading" class="placeholder">{{ t('library.loading') }}</p>
    <p v-else-if="!entries.length" class="placeholder">{{ t('library.empty') }}</p>

    <template v-else>
      <div class="lib-grid">
        <article v-for="entry in entries" :key="entry.id" class="lib-card">
          <button class="lib-card__stage" type="button" @click="openViewer(entry)">
            <SkinPreview :hash="entry.hash" :scale="isCape(entry) ? 6 : 3" :variant="isCape(entry) ? 'cape' : 'body'"/>
            <span :class="{'lib-card__tag--cape': isCape(entry)}" class="lib-card__tag">
              {{ isCape(entry) ? t('library.filterCape') : entry.type }}
            </span>
            <span class="lib-card__view">{{ t('library.view3d') }}</span>
          </button>
          <div class="lib-card__meta">
            <b>{{ entry.name }}</b>
            <small>{{ entry.type }} · #{{ entry.id }} · ♥ {{ entry.likes }}</small>
          </div>
        </article>
      </div>

      <nav class="pager" :aria-label="t('library.pager')">
        <div class="pager__nav">
          <button :disabled="page <= 1" type="button" @click="gotoPage(-1)">{{ t('library.prev') }}</button>
          <span>{{ t('library.pageOf', { page, totalPages }) }}</span>
          <button :disabled="page >= totalPages" type="button" @click="gotoPage(1)">{{ t('library.next') }}</button>
        </div>
        <label class="pager__size">
          <span>{{ t('library.perPage') }}</span>
          <select v-model.number="pageSize" @change="applyFilters">
            <option v-for="size in PAGE_SIZES" :key="size" :value="size">{{ size }}</option>
          </select>
        </label>
      </nav>
    </template>

    <Teleport to="body">
      <div v-if="viewerEntry" class="lightbox" role="dialog" aria-modal="true" @click.self="closeViewer">
        <div class="lightbox__panel">
          <header class="lightbox__head">
            <p class="eyebrow">{{ t('closet.viewerEyebrow') }}</p>
            <button :aria-label="t('common.close')" class="lightbox__close" type="button" @click="closeViewer">✕</button>
          </header>
          <div class="lightbox__body">
            <div class="lightbox__stage">
              <SkinModel :key="viewerEntry.id" :hash="viewerEntry.hash" :type="modelTypeOf(viewerEntry)"/>
            </div>
            <div class="lightbox__info">
              <div class="lightbox__title">
                <h2>{{ viewerEntry.name }}</h2>
                <span :class="{'tag--cape': isCape(viewerEntry)}" class="tag">
                  {{ isCape(viewerEntry) ? t('library.filterCape') : viewerEntry.type }}
                </span>
              </div>
              <dl class="lightbox__meta">
                <div v-if="!isCape(viewerEntry)">
                  <dt>{{ t('closet.metaModel') }}</dt>
                  <dd>{{ viewerEntry.type === 'alex' ? t('closet.modelSlim') : t('closet.modelClassic') }}</dd>
                </div>
                <div>
                  <dt>{{ t('closet.metaId') }}</dt>
                  <dd>#{{ viewerEntry.id }}</dd>
                </div>
                <div>
                  <dt>{{ t('closet.metaSize') }}</dt>
                  <dd>{{ (viewerEntry.size / 1024).toFixed(1) }} KB</dd>
                </div>
                <div>
                  <dt>{{ t('library.metaLikes') }}</dt>
                  <dd>♥ {{ viewerEntry.likes }}</dd>
                </div>
              </dl>
              <button class="primary-button lightbox__collect" type="button" @click="collect(viewerEntry)">
                <span>{{ t('library.collect') }}</span><span aria-hidden="true">↓</span>
              </button>
              <p class="lightbox__hint">{{ t('closet.viewerHint') }}</p>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </AppShell>
</template>

<style scoped>
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.page-head h1 { margin: 0; font: 600 clamp(37px, 4.3vw, 58px)/1.01 'Space Grotesk', sans-serif; }
.page-head h1 em { color: var(--accent-strong); font-style: normal; }
.primary-button { display: inline-flex; align-items: center; gap: 22px; min-height: 46px; padding: 0 16px 0 19px; border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); font-size: 12px; font-weight: 700; text-decoration: none; transition: background .2s; }
.primary-button:hover { background: var(--accent-hover); }
.primary-button span:last-child { font-size: 20px; font-weight: 400; }
.notice { margin: 22px 0 -6px; padding: 9px 12px; font-size: 12px; }
.notice--ok { color: var(--ok); background: var(--ok-bg); }
.notice--error { color: var(--danger); background: var(--danger-bg); }

.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 34px; }
.filters { display: flex; gap: 8px; }
.filters button { padding: 7px 13px; border: 1px solid var(--border); background: var(--surface-panel); color: var(--text-muted); font-size: 11px; transition: border-color .2s, color .2s; }
.filters button.active { border-color: var(--accent-strong); color: var(--text-strong); background: var(--accent-soft-bg); }
.search { display: flex; gap: 8px; }
.search input { width: 210px; height: 34px; padding: 0 11px; border: 1px solid var(--border); background: var(--surface-raised); color: var(--text); font-size: 12px; }
.search button { padding: 0 13px; border: 1px solid var(--border-strong); background: var(--surface-panel); color: var(--text-muted); font-size: 11px; }

.placeholder { margin: 26px 0 0; padding: 26px; border: 1px dashed var(--border-strong); color: var(--text-muted); background: var(--surface-panel); font-size: 12px; line-height: 1.8; }

.lib-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(178px, 1fr)); gap: 13px; margin-top: 22px; }
.lib-card { display: flex; flex-direction: column; border: 1px solid var(--border); background: var(--surface-panel); }
.lib-card__stage { position: relative; display: grid; width: 100%; height: 190px; place-items: center; overflow: hidden; padding: 0; border: 0; cursor: pointer; background-color: var(--surface-stage-from); background-image: linear-gradient(var(--stage-grid) 1px, transparent 1px), linear-gradient(90deg, var(--stage-grid) 1px, transparent 1px); background-size: 16px 16px; }
.lib-card__stage:focus-visible { outline: 2px solid var(--accent-strong); outline-offset: -2px; }
.lib-card__tag { position: absolute; top: 8px; left: 8px; padding: 3px 6px; color: var(--accent-tag-ink); background: var(--accent-tag-bg); font: 8px 'DM Mono', monospace; }
.lib-card__tag--cape { color: var(--cape-ink); background: var(--cape-bg); }
.lib-card__view { position: absolute; right: 8px; bottom: 8px; padding: 3px 6px; color: var(--text-inverse); background: var(--sidebar-active); font: 8px 'DM Mono', monospace; opacity: 0; transition: opacity .15s; }
.lib-card__stage:hover .lib-card__view, .lib-card__stage:focus-visible .lib-card__view { opacity: 1; }
.lib-card__meta { display: grid; gap: 3px; padding: 11px 10px; }
.lib-card__meta b { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.lib-card__meta small { overflow: hidden; color: var(--text-faint); font: 8px 'DM Mono', monospace; text-overflow: ellipsis; white-space: nowrap; }

.pager { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 24px; }
.pager__nav { display: flex; align-items: center; gap: 12px; }
.pager__nav button { min-height: 34px; padding: 0 14px; border: 1px solid var(--border-strong); background: var(--surface-panel); color: var(--text-muted); font-size: 11px; transition: color .15s, border-color .15s; }
.pager__nav button:hover:not(:disabled) { color: var(--text-strong); border-color: var(--accent-strong); }
.pager__nav button:disabled { cursor: not-allowed; opacity: .45; }
.pager__nav span { color: var(--text-faint); font: 10px 'DM Mono', monospace; }
.pager__size { display: flex; align-items: center; gap: 8px; color: var(--text-faint); font: 10px 'DM Mono', monospace; }
.pager__size select { height: 34px; padding: 0 8px; border: 1px solid var(--border-strong); background: var(--surface-raised); color: var(--text); font-size: 12px; }

/* —— 3D 模型弹层。 —— */
.lightbox { position: fixed; z-index: 40; inset: 0; display: grid; place-items: center; padding: 24px; background: rgba(10, 14, 12, .62); }
.lightbox__panel { display: flex; flex-direction: column; width: min(920px, 100%); max-height: 100%; border: 1px solid var(--border); background: var(--surface-panel); }
.lightbox__head { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; border-bottom: 1px solid var(--border-soft); }
.lightbox__head .eyebrow { margin: 0; }
.lightbox__close { width: 30px; height: 30px; border: 1px solid var(--border-strong); background: transparent; color: var(--text-muted); font-size: 13px; line-height: 1; transition: color .15s, border-color .15s; }
.lightbox__close:hover { color: var(--text-strong); border-color: var(--accent-strong); }
.lightbox__body { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(240px, .9fr); gap: 18px; padding: 18px; overflow-y: auto; }
.lightbox__stage { position: relative; min-height: 430px; border: 1px solid var(--border); background-color: var(--surface-stage-from); background-image: linear-gradient(var(--stage-grid) 1px, transparent 1px), linear-gradient(90deg, var(--stage-grid) 1px, transparent 1px); background-size: 16px 16px; }
.lightbox__stage :deep(.skin-model) { position: absolute; inset: 0; }
.lightbox__info { display: flex; flex-direction: column; }
.lightbox__title { display: flex; flex-wrap: wrap; align-items: center; gap: 9px; }
.lightbox__title h2 { margin: 0; overflow-wrap: anywhere; font: 600 22px 'Space Grotesk', sans-serif; }
.tag { padding: 3px 6px; color: var(--accent-tag-ink); background: var(--accent-tag-bg); font: 9px 'DM Mono', monospace; }
.tag--cape { color: var(--cape-ink); background: var(--cape-bg); }
.lightbox__meta { margin: 16px 0 0; }
.lightbox__meta div { display: grid; grid-template-columns: 96px 1fr; gap: 12px; padding: 9px 0; border-top: 1px solid var(--border-soft); }
.lightbox__meta dt { color: var(--text-faint); font: 9px 'DM Mono', monospace; letter-spacing: .08em; }
.lightbox__meta dd { margin: 0; color: var(--text-body); font-size: 12px; }
.lightbox__collect { margin-top: auto; }
.lightbox__hint { margin: 14px 0 0; color: var(--text-faint); font: 9px 'DM Mono', monospace; letter-spacing: .08em; }

@media (max-width: 950px) {
  .toolbar { flex-direction: column; align-items: stretch; }
  .search input { width: 100%; }
  .lightbox { padding: 12px; }
  .lightbox__body { grid-template-columns: 1fr; }
  .lightbox__stage { min-height: 340px; }
}

@media (max-width: 620px) {
  .page-head { align-items: flex-start; flex-direction: column; }
  .page-head h1 { font-size: 34px; }
  .pager { flex-direction: column; align-items: stretch; }
  .pager__nav { justify-content: center; }
}
</style>
