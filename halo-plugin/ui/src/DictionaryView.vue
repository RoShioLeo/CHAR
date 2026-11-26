<!-- SPDX-License-Identifier: MIT -->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { Dialog, Toast, VButton, VCard, VEmpty, VLoading, VModal, VPageHeader } from '@halo-dev/components';
import { unicodeInput, unicodeCodes } from './unicode';

const api = '/apis/console.api.char.lemonslice.cloud/v1alpha1/dictionaries';
const option = ref('v2s');
const query = ref('');
const source = ref('all');
const page = ref(1);
const result = ref({items: [], total: 0, version: null, effectiveCount: 0, changedCount: 0});
const loading = ref(false);
const saving = ref(false);
const selected = ref([]);
const selectable = computed(() => result.value.items.filter(row => row.source !== 'disabled'));
const allSelected = computed(() => selectable.value.length > 0 && selectable.value.every(row => selected.value.includes(row.character)));
const error = ref('');
const editorVisible = ref(false);
const historyVisible = ref(false);
const history = ref([]);
const edit = reactive({character: '', candidates: '', note: '', existing: false, version: null, option: 'v2s'});
const candidatePreview = computed(() => edit.candidates.split('\n').filter(value => value.trim()).map(value => {
    try { const character = unicodeInput(value); return {character, code: unicodeCodes(character)}; }
    catch (failure) { return {error: failure.message}; }
}));
const characterPreview = computed(() => {
    try { return unicodeInput(edit.character); } catch (_) { return ''; }
});
let sequence = 0;

async function request(url, init = {}) {
    const csrfCookie = document.cookie.split('; ').find(cookie => cookie.startsWith('XSRF-TOKEN='));
    const headers = {Accept: 'application/json', ...init.headers};
    if (init.body) headers['Content-Type'] = 'application/json';
    if (csrfCookie) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrfCookie.slice('XSRF-TOKEN='.length));
    const response = await fetch(url, {...init, headers, credentials: 'same-origin'});
    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(response.status === 403 || response.status === 401 ? '仅超级管理员可以管理字库' : response.status === 409 ? '字库已被修改，请刷新后重新确认' : problem.detail || '操作失败，请重试');
    }
    return response.json();
}

async function load() {
    const ticket = ++sequence;
    loading.value = true;
    error.value = '';
    try {
        const params = new URLSearchParams({query: unicodeInput(query.value), source: source.value, page: String(page.value), size: '50'});
        const data = await request(`${api}/${option.value}?${params}`);
        if (ticket === sequence) { result.value = data; selected.value = []; }
    } catch (failure) { if (ticket === sequence) error.value = failure.message; }
    finally { if (ticket === sequence) loading.value = false; }
}

watch([option, source], () => { page.value = 1; editorVisible.value = false; load(); });
onMounted(load);
function search() { page.value = 1; load(); }
function toggleAll() { selected.value = allSelected.value ? [] : selectable.value.map(row => row.character); }
function disableSelected() {
    if (!selected.value.length || saving.value) return;
    const characters = [...selected.value];
    const selectedOption = option.value;
    const version = result.value.version ?? null;
    Dialog.warning({
        title: `停用${selectedOption === 'v2s' ? '简体' : '繁体'}字库中选中的 ${characters.length} 项映射？`,
        description: `原字：${characters.slice(0, 12).join('、')}${characters.length > 12 ? '…' : ''}。转换时将保留原字。`,
        confirmText: '停用', cancelText: '取消', showCancel: true,
        onConfirm: async () => {
            saving.value = true;
            try {
                await request(`${api}/${selectedOption}/batch-disable`, {method: 'PUT', body: JSON.stringify({characters, version, note: ''})});
                Toast.success(`已停用 ${characters.length} 项映射`);
                await load();
            } catch (failure) { Toast.error(failure.message); }
            finally { saving.value = false; }
        }
    });
}
function openEditor(row) {
    Object.assign(edit, {character: row?.character || '', candidates: (row?.candidates || []).join('\n'),
        note: row?.note || '', existing: !!row, version: result.value.version ?? null, option: option.value});
    editorVisible.value = true;
}
async function save() {
    saving.value = true;
    try {
        await request(`${api}/${edit.option}/entries`, {method: 'PUT', body: JSON.stringify({character: edit.character,
            candidates: edit.candidates.split('\n').map(value => value.trim()).filter(Boolean), note: edit.note,
            action: 'override', version: edit.version})});
        editorVisible.value = false;
        Toast.success('已保存');
        await load();
    } catch (failure) { Toast.error(failure.message); }
    finally { saving.value = false; }
}
function change(row, action) {
    const selectedOption = option.value;
    const version = result.value.version ?? null;
    Dialog.warning({
        title: action === 'disable' ? `停用「${row.character}」的映射？` : `恢复「${row.character}」的基础映射？`,
        description: action === 'disable' ? '转换时将保留原字。' : '该字将使用插件基础字库中的结果。',
        confirmText: '确认', cancelText: '取消', showCancel: true,
        onConfirm: async () => {
            saving.value = true;
            try {
                await request(`${api}/${selectedOption}/entries`, {method: 'PUT', body: JSON.stringify({character: row.character, action, version, note: ''})});
                Toast.success('已保存');
                await load();
            } catch (failure) { Toast.error(failure.message); }
            finally { saving.value = false; }
        }
    });
}
async function showHistory() {
    try { history.value = await request(`${api}/${option.value}/history`); historyVisible.value = true; }
    catch (failure) { Toast.error(failure.message); }
}
async function download(kind) {
    try {
        const data = await request(`${api}/${option.value}/export?kind=${kind}`);
        const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], {type: 'application/json;charset=utf-8'}));
        const link = document.createElement('a');
        link.href = url;
        link.download = option.value + (kind === 'overrides' ? '-修正记录' : '-字库') + '.json';
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (failure) { Toast.error(failure.message); }
}
const formatCandidates = values => values?.length ? values.join(' / ') : '保持原字';
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN') : '初始修正';
function openPublic() { window.open('/tools/char/', '_blank', 'noopener,noreferrer'); }
</script>

<template>
    <VPageHeader title="字库管理">
        <template #actions>
            <div class="actions">
                <VButton :disabled="loading || !!error" @click="showHistory">修改记录</VButton>
                <VButton :disabled="loading || !!error" @click="download('overrides')">导出修正</VButton>
                <VButton :disabled="loading || !!error" @click="download('merged')">导出字库</VButton>
                <VButton @click="openPublic">查看前台</VButton>
            </div>
        </template>
    </VPageHeader>
    <div class="dictionary-page">
        <VCard>
            <div class="filters">
                <div class="filter-field">
                    <label for="char-filter-option">字库</label>
                    <FormKit id="char-filter-option" v-model="option" type="select" outer-class="char-filter-control" :options="[{label: '简体字', value: 'v2s'}, {label: '繁体字', value: 'v2t'}]" />
                </div>
                <div class="filter-field">
                    <label for="char-filter-source">范围</label>
                    <FormKit id="char-filter-source" v-model="source" type="select" outer-class="char-filter-control" :options="[{label: '全部字形', value: 'all'}, {label: '自定义修正', value: 'changed'}]" />
                </div>
                <div class="filter-field search-field">
                    <label for="char-filter-query">检索</label>
                    <FormKit id="char-filter-query" v-model="query" type="text" outer-class="char-filter-control" :delay="0" placeholder="原字、候选字或 Unicode 编码" @keyup.enter="search" />
                </div>
                <div class="filter-actions">
                    <VButton :loading="loading" @click="search">查询</VButton>
                    <VButton type="primary" :disabled="loading || saving || !!error" @click="openEditor()">新增映射</VButton>
                </div>
            </div>
        </VCard>
        <VCard v-if="error"><p role="alert">{{ error }}</p><VButton @click="load">重新加载</VButton></VCard>
        <VCard v-else>
            <div class="list-toolbar">
                <div class="summary">有效映射 {{ result.effectiveCount }} 项 · 自定义修正 {{ result.changedCount }} 项</div>
                <div class="actions">
                    <span v-if="selected.length" class="selection-count">已选 {{ selected.length }} 项</span>
                    <VButton :disabled="!selected.length || loading || saving" :loading="saving" @click="disableSelected">批量停用</VButton>
                    <VButton v-if="selected.length" :disabled="saving" @click="selected = []">取消选择</VButton>
                </div>
            </div>
            <VLoading v-if="loading" />
            <VEmpty v-else-if="!result.items.length" title="没有匹配的字形" />
            <div v-else class="table-wrap">
                <table>
                    <thead><tr><th class="selection-cell"><input type="checkbox" aria-label="全选当前页可停用映射" :checked="allSelected" :indeterminate="selected.length > 0 && !allSelected" :disabled="!selectable.length || saving" @change="toggleAll"></th><th>原字</th><th>当前候选</th><th>来源</th><th>备注</th><th>操作</th></tr></thead>
                    <tbody>
                        <tr v-for="row in result.items" :key="row.character">
                            <td class="selection-cell"><input v-model="selected" type="checkbox" :value="row.character" :aria-label="`选择原字 ${row.character}`" :disabled="row.source === 'disabled' || saving"></td>
                            <td><span class="glyph"><span class="character">{{ row.character }}</span><small>{{ unicodeCodes(row.character) }}</small></span></td>
                            <td><div class="glyph-list"><span v-for="(candidate, index) in (row.candidates?.length ? row.candidates : [row.character])" :key="index" class="glyph"><span class="character">{{ candidate }}</span><small>{{ unicodeCodes(candidate) }}</small></span></div></td>
                            <td>{{ row.source === 'base' ? '基础字库' : row.source === 'disabled' ? '保持原字' : '自定义修正' }}</td>
                            <td class="note">{{ row.note }}</td>
                            <td><div class="actions">
                                <VButton size="xs" :disabled="saving" @click="openEditor(row)">编辑</VButton>
                                <VButton v-if="row.source !== 'disabled'" size="xs" :disabled="saving" @click="change(row, 'disable')">停用</VButton>
                                <VButton v-if="row.source !== 'base'" size="xs" :disabled="saving" @click="change(row, 'reset')">恢复</VButton>
                            </div></td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <div class="pagination">
                <span>共 {{ result.total }} 项 · 第 {{ page }} 页</span>
                <VButton :disabled="loading || page <= 1" @click="page--; load()">上一页</VButton>
                <VButton :disabled="loading || page * 50 >= result.total" @click="page++; load()">下一页</VButton>
            </div>
        </VCard>
        <VModal :visible="editorVisible" :title="edit.existing ? '编辑映射' : '新增映射'" :width="560" @close="editorVisible = false">
            <FormKit type="form" submit-label="保存" :disabled="saving" @submit="save">
                <FormKit v-model="edit.character" type="text" label="原字" validation="required" :disabled="edit.existing" :help="edit.existing ? undefined : '支持字形、U+4E00、0x4E00、4E00'" />
                <div v-if="!edit.existing && characterPreview" class="edit-preview"><span class="glyph"><span class="character">{{ characterPreview }}</span><small>{{ unicodeCodes(characterPreview) }}</small></span></div>
                <FormKit v-model="edit.candidates" type="textarea" label="候选字" help="每行一个候选，支持字形或十六进制 Unicode 码。" validation="required" :rows="5" />
                <div class="edit-preview"><template v-for="(candidate, index) in candidatePreview" :key="index"><span v-if="!candidate.error" class="glyph"><span class="character">{{ candidate.character }}</span><small>{{ candidate.code }}</small></span><span v-else role="alert">{{ candidate.error }}</span></template></div>
                <FormKit v-model="edit.note" type="textarea" label="备注" :maxlength="300" :rows="2" />
            </FormKit>
        </VModal>
        <VModal :visible="historyVisible" title="修改记录" :width="900" @close="historyVisible = false">
            <VEmpty v-if="!history.length" title="还没有后台修改记录" />
            <div v-else class="table-wrap history">
                <table>
                    <thead><tr><th>时间</th><th>原字</th><th>修改前</th><th>修改后</th><th>操作人</th><th>备注</th></tr></thead>
                    <tbody><tr v-for="(item, index) in history" :key="index">
                        <td>{{ formatTime(item.updatedAt) }}</td><td class="character">{{ item.character }}</td>
                        <td>{{ formatCandidates(item.before) }}</td><td>{{ formatCandidates(item.after) }}</td>
                        <td>{{ item.updatedBy }}</td><td>{{ item.note }}</td>
                    </tr></tbody>
                </table>
            </div>
        </VModal>
    </div>
</template>

<style scoped>
.dictionary-page { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; gap: 16px; padding: 16px; container-type: inline-size; }
.actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.filters { display: grid; grid-template-columns: minmax(140px, 200px) minmax(140px, 200px) minmax(220px, 1fr) auto; align-items: end; gap: 16px; padding: 8px; }
.filter-field { display: grid; gap: 12px; min-width: 0; }
.filter-field > label { font-size: 13px; }
.filter-field :deep(.char-filter-control) { margin: 0 !important; }
.filter-actions { display: flex; align-items: center; gap: 16px; }
.list-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; padding: 8px 12px 16px; }
.summary, .selection-count { font-size: 13px; opacity: .7; }
.selection-cell { width: 40px; }
.selection-cell input { width: 16px; height: 16px; vertical-align: middle; accent-color: #292b2a; cursor: pointer; }
.selection-cell input:disabled { cursor: default; opacity: .4; }
.table-wrap { overflow: auto; }
table { border-collapse: collapse; width: 100%; font-size: 13px; }
th, td { text-align: left; padding: 12px; border-bottom: 1px solid #94a3b830; vertical-align: middle; }
th { white-space: nowrap; font-weight: 500; }
td small { display: block; font-size: 10px; opacity: .6; }
.character { font-size: 22px; line-height: 1.4; }
.glyph { display: inline-flex; flex-direction: column; align-items: flex-start; }
.glyph small { display: block; margin-top: 2px; font-size: 10px; line-height: 1.5; opacity: .6; }
.glyph-list { display: flex; flex-wrap: wrap; align-items: start; gap: 12px 20px; }
.edit-preview { display: flex; flex-wrap: wrap; gap: 12px 20px; margin: -8px 0 20px; }
.note { min-width: 120px; max-width: 240px; overflow-wrap: anywhere; }
.pagination { display: flex; align-items: center; justify-content: end; flex-wrap: wrap; gap: 12px; padding: 16px 8px 0; font-size: 13px; }
.history { max-height: 60vh; }
@media (max-width: 900px) { .filters { grid-template-columns: 1fr 1fr; } .search-field { grid-column: 1 / -1; } .filter-actions { grid-column: 1 / -1; justify-content: end; } }
@container (max-width: 900px) { .filters { grid-template-columns: 1fr 1fr; } .search-field { grid-column: 1 / -1; } .filter-actions { grid-column: 1 / -1; justify-content: end; } }
@media (max-width: 700px) { .dictionary-page { padding: 8px; } }
</style>
