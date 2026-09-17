<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const http = useHttp()

type ClosetEntry = {
  texture_id: number
  item_name: string | null
  name: string
  type: string
  hash: string
  size: number
  uploader_id: number
  public: boolean
  upload_at: string
  likes: number
}

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

type PlayerSummary = { id: number; name: string; uuid: string }
type Feedback = { tone: 'ok' | 'error'; text: string }

const filters = computed(() => [
  { value: '', label: t('closet.filterAll') },
  { value: 'skin', label: t('closet.filterSkin') },
  { value: 'cape', label: t('closet.filterCape') },
])

const keyword = ref('')
const loading = ref(true)
const entries = ref<ClosetEntry[]>([])
const players = ref<PlayerSummary[]>([])
const feedback = ref<Feedback | null>(null)

/** 正在“应用到角色”的材质主键，非空时展示角色选择条。 */
const applyingTexture = ref<number | null>(null)
/** 正在重命名的材质主键。 */
const renamingTexture = ref<number | null>(null)
const renameDraft = ref('')

const library = reactive({ keyword: '', type: '', list: [] as LibraryEntry[], loading: false })
const uploadPanelOpen = ref(false)
const uploadForm = reactive({ name: '', type: 'steve', public: true, file: null as File | null })
const uploading = ref(false)

const activeKind = computed({
  get: () => (route.query.type === 'cape' ? 'cape' : route.query.type === 'skin' ? 'skin' : ''),
  set: (value: string) => { router.replace({ query: value ? { type: value } : {} }) },
})

const isCape = (entry: { type: string }) => entry.type === 'cape'
const titleOf = (entry: { item_name?: string | null; name: string }) => entry.item_name || entry.name

function describe(error: unknown, fallback: string) {
  const value = error as { data?: { message?: string }; statusMessage?: string; message?: string }
  return value.data?.message || value.statusMessage || value.message || fallback
}

async function loadCloset() {
  loading.value = true
  try {
    const data = await http.get<{ list: ClosetEntry[] }>('/users/me/closet', {
      type: activeKind.value,
      keyword: keyword.value.trim(),
      page: 1,
      size: 60,
    })
    entries.value = data.list
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('closet.loadFailed')) }
  } finally {
    loading.value = false
  }
}

async function loadPlayers() {
  try {
    players.value = (await http.get<{ list: PlayerSummary[] }>('/users/me/players')).list
  } catch {
    players.value = []
  }
}

async function searchLibrary() {
  library.loading = true
  try {
    library.list = (await http.get<{ list: LibraryEntry[] }>('/skinlib', {
      type: library.type,
      keyword: library.keyword.trim(),
      page: 1,
      size: 24,
    })).list
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('closet.libraryFailed')) }
  } finally {
    library.loading = false
  }
}

async function addToCloset(textureId: number) {
  feedback.value = null
  try {
    await http.post('/users/me/closet', { texture_id: textureId })
    feedback.value = { tone: 'ok', text: t('closet.collected') }
    await loadCloset()
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('closet.collectFailed')) }
  }
}

async function removeFromCloset(textureId: number) {
  feedback.value = null
  try {
    await http.delete(`/users/me/closet/${textureId}`)
    feedback.value = { tone: 'ok', text: t('closet.removed') }
    await loadCloset()
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('closet.removeFailed')) }
  }
}

function startRename(entry: ClosetEntry) {
  renamingTexture.value = entry.texture_id
  renameDraft.value = entry.item_name ?? ''
}

async function commitRename(entry: ClosetEntry) {
  feedback.value = null
  try {
    await http.patch(`/users/me/closet/${entry.texture_id}`, { item_name: renameDraft.value })
    renamingTexture.value = null
    await loadCloset()
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('closet.renameFailed')) }
  }
}

async function applyToPlayer(entry: ClosetEntry, playerId: number) {
  feedback.value = null
  try {
    const payload = isCape(entry) ? { cape_texture_id: entry.texture_id } : { skin_texture_id: entry.texture_id }
    await http.put(`/users/me/players/${playerId}/textures`, payload)
    const player = players.value.find((item) => item.id === playerId)
    feedback.value = { tone: 'ok', text: t('closet.applied', { name: titleOf(entry), player: player?.name ?? '' }) }
    applyingTexture.value = null
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('closet.applyFailed')) }
  }
}

function pickFile(event: Event) {
  uploadForm.file = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function submitUpload() {
  feedback.value = null
  if (!uploadForm.file) {
    feedback.value = { tone: 'error', text: t('closet.pickFile') }
    return
  }
  uploading.value = true
  try {
    const body = new FormData()
    body.append('file', uploadForm.file)
    body.append('name', uploadForm.name.trim() || uploadForm.file.name.replace(/\.[^/.]+$/, ''))
    body.append('type', uploadForm.type)
    body.append('public', String(uploadForm.public))
    await http.post('/skinlib', body)
    feedback.value = { tone: 'ok', text: t('closet.uploaded') }
    uploadPanelOpen.value = false
    uploadForm.name = ''
    uploadForm.file = null
    await searchLibrary()
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('closet.uploadFailed')) }
  } finally {
    uploading.value = false
  }
}

watch(activeKind, loadCloset)

onMounted(async () => {
  await auth.hydrate()
  if (!auth.isAuthenticated || !auth.user) {
    await router.replace({ path: '/login', query: { redirect: '/closet' } })
    return
  }
  await Promise.all([loadCloset(), loadPlayers(), searchLibrary()])
})
</script>

<template>
  <AppShell :active="activeKind === 'cape' ? 'capes' : 'closet'" :crumb="t('shell.nav.closet')">
    <section class="page-head">
      <div>
        <p class="eyebrow">{{ t('closet.eyebrow', { count: String(entries.length).padStart(2, '0') }) }}</p>
        <h1>{{ t('closet.headlineLine1') }}<br><em>{{ t('closet.headlineLine2') }}</em></h1>
      </div>
      <button class="primary-button" type="button" @click="uploadPanelOpen = !uploadPanelOpen">
        <span>{{ uploadPanelOpen ? t('closet.collapseUpload') : t('closet.upload') }}</span><span aria-hidden="true">+</span>
      </button>
    </section>

    <p v-if="feedback" :class="`notice notice--${feedback.tone}`" role="status">{{ feedback.text }}</p>

    <form v-if="uploadPanelOpen" class="upload-panel" @submit.prevent="submitUpload">
      <div>
        <p class="eyebrow">{{ t('closet.uploadEyebrow') }}</p>
        <h2>{{ t('closet.uploadTitle') }}</h2>
      </div>
      <input accept="image/png" type="file" @change="pickFile">
      <input v-model="uploadForm.name" maxlength="50" :placeholder="t('closet.namePlaceholder')" type="text">
      <select v-model="uploadForm.type">
        <option value="steve">Steve</option>
        <option value="alex">Alex</option>
        <option value="cape">{{ t('closet.filterCape') }}</option>
      </select>
      <label class="switch">
        <input v-model="uploadForm.public" type="checkbox"><b>{{ t('closet.tagPublic') }}</b>
      </label>
      <button :disabled="uploading" class="primary-button" type="submit">
        <span>{{ uploading ? t('closet.uploading') : t('closet.startUpload') }}</span><span aria-hidden="true">↗</span>
      </button>
    </form>

    <section class="wardrobe">
      <div>
        <div class="toolbar">
          <div class="filters" role="tablist">
            <button v-for="item in filters" :key="item.value" :aria-selected="activeKind === item.value"
                    :class="{ active: activeKind === item.value }" role="tab" type="button"
                    @click="activeKind = item.value">{{ item.label }}
            </button>
          </div>
          <form class="search" @submit.prevent="loadCloset">
            <input v-model="keyword" :placeholder="t('closet.searchPlaceholder')" type="search">
            <button type="submit">{{ t('common.search') }}</button>
          </form>
        </div>

        <p v-if="loading" class="placeholder">{{ t('closet.loading') }}</p>
        <p v-else-if="!entries.length" class="placeholder">{{ t('closet.empty') }}</p>

        <div v-else class="closet-grid">
          <article v-for="entry in entries" :key="entry.texture_id" class="closet-card">
            <div class="closet-card__stage">
              <SkinPreview :hash="entry.hash" :scale="isCape(entry) ? 6 : 3" :variant="isCape(entry) ? 'cape' : 'body'"/>
              <span :class="{ 'closet-card__tag--cape': isCape(entry) }" class="closet-card__tag">
                {{ isCape(entry) ? t('closet.tagCape') : entry.type }}
              </span>
              <span v-if="entry.public" class="closet-card__public">{{ t('closet.tagPublic') }}</span>
            </div>

            <div class="closet-card__meta">
              <form v-if="renamingTexture === entry.texture_id" class="rename" @submit.prevent="commitRename(entry)">
                <input v-model="renameDraft" :placeholder="entry.name" maxlength="50" type="text">
                <button type="submit">{{ t('common.save') }}</button>
                <button type="button" @click="renamingTexture = null">{{ t('common.cancel') }}</button>
              </form>
              <template v-else>
                <b>{{ titleOf(entry) }}</b>
                <small v-if="entry.item_name">{{ t('closet.originalName', { name: entry.name }) }}</small>
                <small v-else>{{ t('closet.meta', { id: entry.texture_id, size: (entry.size / 1024).toFixed(1) }) }}</small>
              </template>
            </div>

            <div v-if="applyingTexture === entry.texture_id" class="apply">
              <p v-if="!players.length">
                {{ t('closet.noPlayers') }}<NuxtLink to="/characters">{{ t('closet.noPlayersLink') }}</NuxtLink>{{ t('closet.noPlayersTail') }}
              </p>
              <template v-else>
                <p>{{ t('closet.applyTo') }}</p>
                <button v-for="player in players" :key="player.id" type="button" @click="applyToPlayer(entry, player.id)">
                  {{ player.name }}
                </button>
              </template>
              <button class="apply__cancel" type="button" @click="applyingTexture = null">{{ t('common.cancel') }}</button>
            </div>

            <footer v-else class="closet-card__actions">
              <button type="button" @click="applyingTexture = entry.texture_id">{{ t('closet.apply') }}</button>
              <button type="button" @click="startRename(entry)">{{ t('closet.rename') }}</button>
              <button class="danger" type="button" @click="removeFromCloset(entry.texture_id)">{{ t('closet.remove') }}</button>
            </footer>
          </article>
        </div>
      </div>

      <aside class="library">
        <div class="library__head">
          <p class="eyebrow">{{ t('closet.libraryEyebrow') }}</p>
          <h2>{{ t('closet.libraryTitle') }}</h2>
        </div>
        <form class="library__search" @submit.prevent="searchLibrary">
          <input v-model="library.keyword" :placeholder="t('closet.librarySearch')" type="search">
          <select v-model="library.type">
            <option value="">{{ t('closet.filterAll') }}</option>
            <option value="skin">{{ t('closet.filterSkin') }}</option>
            <option value="cape">{{ t('closet.filterCape') }}</option>
          </select>
          <button type="submit">{{ t('common.search') }}</button>
        </form>

        <p v-if="library.loading" class="placeholder placeholder--tight">{{ t('closet.libraryLoading') }}</p>
        <p v-else-if="!library.list.length" class="placeholder placeholder--tight">{{ t('closet.libraryEmpty') }}</p>

        <ul v-else class="library__list">
          <li v-for="item in library.list" :key="item.id">
            <SkinPreview :hash="item.hash" :scale="1.5" :variant="item.type === 'cape' ? 'cape' : 'face'"/>
            <span><b>{{ item.name }}</b><small>{{ item.type }} · #{{ item.id }}</small></span>
            <button type="button" @click="addToCloset(item.id)">{{ t('closet.collect') }}</button>
          </li>
        </ul>
      </aside>
    </section>
  </AppShell>
</template>

<style scoped>
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.page-head h1 { margin: 0; font: 600 clamp(37px, 4.3vw, 58px)/1.01 'Space Grotesk', sans-serif; }
.page-head h1 em { color: var(--accent-strong); font-style: normal; }
.primary-button { display: inline-flex; align-items: center; gap: 22px; min-height: 46px; padding: 0 16px 0 19px; border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); font-size: 12px; font-weight: 700; transition: transform .2s, background .2s; }
.primary-button:hover:not(:disabled) { background: var(--accent-hover); transform: translateY(-2px); }
.primary-button:disabled { cursor: wait; opacity: .65; }
.primary-button span:last-child { font-size: 20px; font-weight: 400; }
.notice { margin: 22px 0 -6px; padding: 9px 12px; font-size: 12px; }
.notice--ok { color: var(--ok); background: var(--ok-bg); }
.notice--error { color: var(--danger); background: var(--danger-bg); }

.upload-panel { display: grid; grid-template-columns: minmax(160px, .8fr) 1.1fr 1.2fr .9fr auto auto; gap: 12px; align-items: center; margin-top: 26px; padding: 20px; border: 1px solid var(--border); background: var(--surface-panel); }
.upload-panel h2 { margin: 4px 0 0; font: 600 19px 'Space Grotesk', sans-serif; }
.upload-panel input[type="text"], .upload-panel select { height: 42px; padding: 0 11px; border: 1px solid var(--border); background: var(--surface-raised); color: var(--text); font-size: 12px; }
.upload-panel input[type="file"] { color: var(--text-muted); font-size: 11px; }
.switch { display: flex; align-items: center; gap: 8px; color: var(--text-body); font-size: 12px; }
.switch input { accent-color: var(--accent-strong); }
.upload-panel .primary-button { margin: 0; }

.wardrobe { display: grid; grid-template-columns: minmax(0, 1fr) 296px; gap: 19px; margin-top: 34px; align-items: start; }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.filters { display: flex; gap: 8px; }
.filters button { padding: 7px 13px; border: 1px solid var(--border); background: var(--surface-panel); color: var(--text-muted); font-size: 11px; transition: border-color .2s, color .2s; }
.filters button.active { border-color: var(--accent-strong); color: var(--text-strong); background: var(--accent-soft-bg); }
.search { display: flex; gap: 8px; }
.search input { width: 210px; height: 34px; padding: 0 11px; border: 1px solid var(--border); background: var(--surface-raised); color: var(--text); font-size: 12px; }
.search button { padding: 0 13px; border: 1px solid var(--border-strong); background: var(--surface-panel); color: var(--text-muted); font-size: 11px; }

.placeholder { margin: 26px 0 0; padding: 26px; border: 1px dashed var(--border-strong); color: var(--text-muted); background: var(--surface-panel); font-size: 12px; line-height: 1.8; }
.placeholder--tight { margin: 0; padding: 16px; }
.placeholder a { color: var(--accent-strong); }

.closet-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(168px, 1fr)); gap: 13px; margin-top: 20px; }
.closet-card { display: flex; flex-direction: column; border: 1px solid var(--border); background: var(--surface-panel); }
.closet-card__stage { position: relative; display: grid; height: 168px; place-items: center; overflow: hidden; background: linear-gradient(135deg, var(--surface-stage-from), var(--surface-stage-to)); background-image: linear-gradient(var(--stage-grid) 1px, transparent 1px), linear-gradient(90deg, var(--stage-grid) 1px, transparent 1px); background-size: 16px 16px; }
.closet-card__tag { position: absolute; top: 8px; left: 8px; padding: 3px 6px; color: var(--accent-tag-ink); background: var(--accent-tag-bg); font: 8px 'DM Mono', monospace; }
.closet-card__tag--cape { color: var(--cape-ink); background: var(--cape-bg); }
.closet-card__public { position: absolute; top: 8px; right: 8px; padding: 3px 6px; color: var(--text-muted); background: var(--surface-panel); font: 8px 'DM Mono', monospace; }
.closet-card__meta { display: grid; gap: 3px; padding: 11px 10px; }
.closet-card__meta b { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.closet-card__meta small { overflow: hidden; color: var(--text-faint); font: 8px 'DM Mono', monospace; text-overflow: ellipsis; white-space: nowrap; }
.rename { display: flex; flex-wrap: wrap; gap: 5px; }
.rename input { width: 100%; height: 28px; padding: 0 7px; border: 1px solid var(--border); background: var(--surface-raised); color: var(--text); font-size: 11px; }
.rename button { padding: 3px 8px; border: 1px solid var(--border-strong); background: var(--surface-raised); color: var(--text-muted); font-size: 10px; }
.closet-card__actions { display: flex; margin-top: auto; border-top: 1px solid var(--border-soft); }
.closet-card__actions button { flex: 1; padding: 9px 0; border: 0; border-right: 1px solid var(--border-soft); background: transparent; color: var(--text-body); font-size: 11px; }
.closet-card__actions button:last-child { border-right: 0; }
.closet-card__actions button:hover { background: var(--surface-muted); }
.closet-card__actions .danger:hover { color: var(--danger); background: var(--danger-bg); }
.apply { display: grid; gap: 5px; margin-top: auto; padding: 10px; border-top: 1px solid var(--border-soft); background: var(--surface-muted); }
.apply p { margin: 0 0 3px; color: var(--text-muted); font-size: 11px; }
.apply button { padding: 6px 8px; border: 1px solid var(--border-strong); background: var(--surface-raised); color: var(--text-body); font-size: 11px; text-align: left; }
.apply__cancel { color: var(--text-faint) !important; }

.library { border: 1px solid var(--border); background: var(--surface-panel); padding: 20px; }
.library__head h2 { margin: 0 0 16px; font: 600 20px 'Space Grotesk', sans-serif; }
.library__search { display: grid; gap: 8px; grid-template-columns: 1fr auto; }
.library__search input { grid-column: 1 / -1; height: 36px; padding: 0 11px; border: 1px solid var(--border); background: var(--surface-raised); color: var(--text); font-size: 12px; }
.library__search select, .library__search button { height: 32px; padding: 0 10px; border: 1px solid var(--border-strong); background: var(--surface-raised); color: var(--text-muted); font-size: 11px; }
.library__list { display: grid; gap: 9px; margin: 14px 0 0; padding: 0; list-style: none; max-height: 520px; overflow-y: auto; }
.library__list li { display: grid; grid-template-columns: auto 1fr auto; gap: 10px; align-items: center; padding-bottom: 9px; border-bottom: 1px solid var(--border-soft); }
.library__list span { min-width: 0; }
.library__list b { display: block; overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.library__list small { color: var(--text-faint); font: 8px 'DM Mono', monospace; }
.library__list button { padding: 5px 9px; border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); font-size: 10px; font-weight: 700; }

@media (max-width: 950px) {
  .wardrobe { grid-template-columns: 1fr; }
  .upload-panel { grid-template-columns: 1fr 1fr; }
}

@media (max-width: 620px) {
  .page-head { align-items: flex-start; flex-direction: column; }
  .page-head h1 { font-size: 34px; }
  .toolbar { flex-direction: column; align-items: stretch; }
  .search input { width: 100%; }
  .upload-panel { grid-template-columns: 1fr; }
}
</style>
