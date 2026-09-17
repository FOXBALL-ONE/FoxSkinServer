<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const http = useHttp()

type PlayerEntry = {
  id: number
  name: string
  uuid: string
  skin_texture_id: number
  skin_hash: string | null
  cape_texture_id: number
  cape_hash: string | null
  last_modified: string
}

type ClosetEntry = {
  texture_id: number
  item_name: string | null
  name: string
  type: string
  hash: string
}

type Feedback = { tone: 'ok' | 'error'; text: string }

const loading = ref(true)
const players = ref<PlayerEntry[]>([])
const feedback = ref<Feedback | null>(null)
const creating = ref(false)
const newPlayer = reactive({ name: '' })

const renamingId = ref<number | null>(null)
const renameDraft = ref('')

/** 正在为哪个角色挑材质；kind 决定挑皮肤还是披风。 */
const picking = ref<{ playerId: number; kind: 'skin' | 'cape' } | null>(null)
const pickerOptions = ref<ClosetEntry[]>([])
const pickerLoading = ref(false)

const activePlayer = computed(() => players.value.find((item) => item.id === picking.value?.playerId) ?? null)
const pickerTitle = computed(() => (picking.value?.kind === 'cape' ? t('characters.pickerCape') : t('characters.pickerSkin')))
const pickerKindLabel = computed(() =>
    picking.value?.kind === 'cape' ? t('characters.cape').toLowerCase() : t('characters.skin').toLowerCase())

function describe(error: unknown, fallback: string) {
  const value = error as { data?: { message?: string }; statusMessage?: string; message?: string }
  return value.data?.message || value.statusMessage || value.message || fallback
}

function formatDate(value?: string | null) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

async function loadPlayers() {
  loading.value = true
  try {
    players.value = (await http.get<{ list: PlayerEntry[] }>('/users/me/players')).list
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('characters.loadFailed')) }
  } finally {
    loading.value = false
  }
}

async function createPlayer() {
  feedback.value = null
  const name = newPlayer.name.trim()
  if (!name) {
    feedback.value = { tone: 'error', text: t('characters.nameRequired') }
    return
  }
  creating.value = true
  try {
    await http.post('/users/me/players', { name })
    newPlayer.name = ''
    feedback.value = { tone: 'ok', text: t('characters.created', { name }) }
    await loadPlayers()
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('characters.createFailed')) }
  } finally {
    creating.value = false
  }
}

function startRename(player: PlayerEntry) {
  renamingId.value = player.id
  renameDraft.value = player.name
}

async function commitRename(player: PlayerEntry) {
  feedback.value = null
  try {
    await http.patch(`/users/me/players/${player.id}`, { name: renameDraft.value.trim() })
    renamingId.value = null
    await loadPlayers()
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('characters.renameFailed')) }
  }
}

async function deletePlayer(player: PlayerEntry) {
  feedback.value = null
  try {
    await http.delete(`/users/me/players/${player.id}`)
    feedback.value = { tone: 'ok', text: t('characters.deleted', { name: player.name }) }
    await loadPlayers()
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('characters.deleteFailed')) }
  }
}

async function openPicker(player: PlayerEntry, kind: 'skin' | 'cape') {
  picking.value = { playerId: player.id, kind }
  pickerLoading.value = true
  feedback.value = null
  try {
    pickerOptions.value = (await http.get<{ list: ClosetEntry[] }>('/users/me/closet', {
      type: kind === 'cape' ? 'cape' : 'skin',
      page: 1,
      size: 60,
    })).list
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('closet.loadFailed')) }
    pickerOptions.value = []
  } finally {
    pickerLoading.value = false
  }
}

/** textureId 传该位置的“未绑定”哨兵值（皮肤 -1、披风 0）即解除绑定。 */
async function bindTexture(playerId: number, kind: 'skin' | 'cape', textureId: number) {
  feedback.value = null
  try {
    const payload = kind === 'cape' ? { cape_texture_id: textureId } : { skin_texture_id: textureId }
    await http.put(`/users/me/players/${playerId}/textures`, payload)
    picking.value = null
    await loadPlayers()
  } catch (error: unknown) {
    feedback.value = { tone: 'error', text: describe(error, t('characters.bindFailed')) }
  }
}

async function copyUuid(uuid: string) {
  feedback.value = null
  try {
    await navigator.clipboard.writeText(uuid.replace(/-/g, ''))
    feedback.value = { tone: 'ok', text: t('characters.uuidCopied') }
  } catch {
    feedback.value = { tone: 'error', text: t('characters.copyFailed') }
  }
}

onMounted(async () => {
  await auth.hydrate()
  if (!auth.isAuthenticated || !auth.user) {
    await router.replace({ path: '/login', query: { redirect: '/characters' } })
    return
  }
  await loadPlayers()
})
</script>

<template>
  <AppShell active="characters" :crumb="t('shell.nav.characters')">
    <section class="page-head">
      <div>
        <p class="eyebrow">{{ t('characters.eyebrow', { count: String(players.length).padStart(2, '0') }) }}</p>
        <h1>{{ t('characters.headlineLine1') }}<br><em>{{ t('characters.headlineLine2') }}</em></h1>
      </div>
      <form class="create" @submit.prevent="createPlayer">
        <input v-model="newPlayer.name" maxlength="16" :placeholder="t('characters.newNamePlaceholder')" type="text">
        <button :disabled="creating" type="submit">{{ creating ? t('characters.creating') : t('characters.create') }}</button>
      </form>
    </section>

    <p v-if="feedback" :class="`notice notice--${feedback.tone}`" role="status">{{ feedback.text }}</p>

    <p v-if="loading" class="placeholder">{{ t('characters.loading') }}</p>
    <p v-else-if="!players.length" class="placeholder">{{ t('characters.empty') }}</p>

    <section v-else class="character-grid">
      <article v-for="player in players" :key="player.id" class="character-card">
        <div class="character-card__stage">
          <SkinPreview :hash="player.skin_hash ?? ''" :scale="3.2" variant="body"/>
          <SkinPreview v-if="player.cape_hash" class="character-card__cape" :hash="player.cape_hash" :scale="4" variant="cape"/>
        </div>

        <div class="character-card__body">
          <form v-if="renamingId === player.id" class="rename" @submit.prevent="commitRename(player)">
            <input v-model="renameDraft" maxlength="16" type="text">
            <button type="submit">{{ t('common.save') }}</button>
            <button type="button" @click="renamingId = null">{{ t('common.cancel') }}</button>
          </form>
          <template v-else>
            <h2>{{ player.name }}</h2>
            <button class="uuid" :title="t('characters.copyUuid')" type="button" @click="copyUuid(player.uuid)">
              {{ player.uuid }} <span aria-hidden="true">⧉</span>
            </button>
          </template>

          <dl>
            <div>
              <dt>{{ t('characters.skin') }}</dt>
              <dd>{{ player.skin_texture_id > 0 ? `#${player.skin_texture_id}` : t('characters.notSet') }}</dd>
            </div>
            <div>
              <dt>{{ t('characters.cape') }}</dt>
              <dd>{{ player.cape_texture_id > 0 ? `#${player.cape_texture_id}` : t('characters.notSet') }}</dd>
            </div>
            <div><dt>{{ t('characters.updatedAt') }}</dt><dd>{{ formatDate(player.last_modified) }}</dd></div>
          </dl>
        </div>

        <footer class="character-card__actions">
          <button type="button" @click="openPicker(player, 'skin')">{{ t('characters.changeSkin') }}</button>
          <button type="button" @click="openPicker(player, 'cape')">{{ t('characters.changeCape') }}</button>
          <button type="button" @click="startRename(player)">{{ t('characters.rename') }}</button>
          <button class="danger" type="button" @click="deletePlayer(player)">{{ t('common.delete') }}</button>
        </footer>
      </article>
    </section>

    <div v-if="picking" class="picker">
      <div class="picker__panel">
        <header class="picker__head">
          <div>
            <p class="eyebrow">{{ activePlayer?.name }}</p>
            <h2>{{ pickerTitle }}</h2>
          </div>
          <button type="button" @click="picking = null">{{ t('common.close') }}</button>
        </header>

        <p v-if="pickerLoading" class="placeholder placeholder--tight">{{ t('characters.pickerLoading') }}</p>
        <p v-else-if="!pickerOptions.length" class="placeholder placeholder--tight">
          {{ t('characters.pickerEmpty', { kind: pickerKindLabel }) }}
          <NuxtLink to="/closet">{{ t('characters.pickerEmptyLink') }}</NuxtLink>{{ t('characters.pickerEmptyTail') }}
        </p>

        <ul v-else class="picker__list">
          <li v-for="option in pickerOptions" :key="option.texture_id">
            <SkinPreview :hash="option.hash" :scale="1.6" :variant="picking.kind === 'cape' ? 'cape' : 'face'"/>
            <span><b>{{ option.item_name || option.name }}</b><small>{{ option.type }} · #{{ option.texture_id }}</small></span>
            <button type="button" @click="bindTexture(picking.playerId, picking.kind, option.texture_id)">
              {{ t('characters.use') }}
            </button>
          </li>
        </ul>

        <footer class="picker__foot">
          <button type="button" @click="bindTexture(picking.playerId, picking.kind, picking.kind === 'cape' ? 0 : -1)">
            {{ t('characters.unbind') }}
          </button>
        </footer>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; }
.page-head h1 { margin: 0; font: 600 clamp(37px, 4.3vw, 58px)/1.01 'Space Grotesk', sans-serif; }
.page-head h1 em { color: var(--accent-strong); font-style: normal; }
.create { display: flex; gap: 9px; }
.create input { width: 250px; height: 46px; padding: 0 13px; border: 1px solid var(--border); background: var(--surface-raised); color: var(--text); font-size: 12px; }
.create button { padding: 0 18px; border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); font-size: 12px; font-weight: 700; }
.create button:disabled { cursor: wait; opacity: .65; }
.notice { margin: 22px 0 -6px; padding: 9px 12px; font-size: 12px; }
.notice--ok { color: var(--ok); background: var(--ok-bg); }
.notice--error { color: var(--danger); background: var(--danger-bg); }
.placeholder { margin: 34px 0 0; padding: 28px; border: 1px dashed var(--border-strong); color: var(--text-muted); background: var(--surface-panel); font-size: 12px; line-height: 1.8; }
.placeholder--tight { margin: 0; padding: 16px; }
.placeholder a { color: var(--accent-strong); }

.character-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(268px, 1fr)); gap: 15px; margin-top: 36px; }
.character-card { display: flex; flex-direction: column; border: 1px solid var(--border); background: var(--surface-panel); }
.character-card__stage { position: relative; display: grid; height: 226px; place-items: center; overflow: hidden; background: linear-gradient(135deg, var(--surface-stage-from), var(--surface-stage-to)); background-image: linear-gradient(var(--stage-grid) 1px, transparent 1px), linear-gradient(90deg, var(--stage-grid) 1px, transparent 1px); background-size: 16px 16px; }
.character-card__cape { position: absolute; top: 16px; right: 16px; }
.character-card__body { display: grid; gap: 6px; padding: 16px 15px 14px; }
.character-card__body h2 { margin: 0; font: 600 20px 'Space Grotesk', sans-serif; word-break: break-all; }
.uuid { padding: 0; border: 0; background: none; color: var(--text-muted); text-align: left; font: 9px 'DM Mono', monospace; word-break: break-all; }
.uuid:hover { color: var(--text-strong); }
.rename { display: flex; flex-wrap: wrap; gap: 6px; }
.rename input { width: 100%; height: 32px; padding: 0 9px; border: 1px solid var(--border); background: var(--surface-raised); color: var(--text); font-size: 12px; }
.rename button { padding: 4px 10px; border: 1px solid var(--border-strong); background: var(--surface-raised); color: var(--text-muted); font-size: 10px; }
.character-card__body dl { display: grid; gap: 7px; margin: 12px 0 0; padding-top: 12px; border-top: 1px solid var(--border-soft); }
.character-card__body dl div { display: flex; justify-content: space-between; gap: 10px; }
.character-card__body dt { color: var(--text-faint); font: 9px 'DM Mono', monospace; }
.character-card__body dd { margin: 0; color: var(--text-body); font-size: 11px; }
.character-card__actions { display: flex; margin-top: auto; border-top: 1px solid var(--border-soft); }
.character-card__actions button { flex: 1; padding: 10px 0; border: 0; border-right: 1px solid var(--border-soft); background: transparent; color: var(--text-body); font-size: 11px; }
.character-card__actions button:last-child { border-right: 0; }
.character-card__actions button:hover { background: var(--surface-muted); }
.character-card__actions .danger:hover { color: var(--danger); background: var(--danger-bg); }

.picker { position: fixed; inset: 0; z-index: 20; display: grid; place-items: center; padding: 24px; background: var(--shadow-pop); }
.picker__panel { width: min(560px, 100%); max-height: 84svh; overflow-y: auto; padding: 22px; border: 1px solid var(--border-strong); background: var(--surface-panel); }
.picker__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.picker__head h2 { margin: 4px 0 0; font: 600 21px 'Space Grotesk', sans-serif; }
.picker__head button { padding: 6px 11px; border: 1px solid var(--border-strong); background: var(--surface-raised); color: var(--text-muted); font-size: 11px; }
.picker__list { display: grid; gap: 9px; margin: 0; padding: 0; list-style: none; }
.picker__list li { display: grid; grid-template-columns: auto 1fr auto; gap: 11px; align-items: center; padding-bottom: 9px; border-bottom: 1px solid var(--border-soft); }
.picker__list span { min-width: 0; }
.picker__list b { display: block; overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.picker__list small { color: var(--text-faint); font: 8px 'DM Mono', monospace; }
.picker__list button { padding: 6px 12px; border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); font-size: 11px; font-weight: 700; }
.picker__foot { margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--border-soft); }
.picker__foot button { padding: 8px 13px; border: 1px solid var(--border-strong); background: var(--surface-raised); color: var(--text-muted); font-size: 11px; }

@media (max-width: 950px) {
  .page-head { flex-direction: column; align-items: flex-start; }
}

@media (max-width: 620px) {
  .page-head h1 { font-size: 34px; }
  .create { width: 100%; }
  .create input { width: 100%; }
}
</style>
