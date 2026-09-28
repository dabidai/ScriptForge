<template>
  <div class="upload-panel">
    <!-- 拖拽上传区域 -->
    <div
      class="drop-zone"
      :class="{ dragging, uploading }"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
      @click="triggerInput"
    >
      <input
        ref="fileInput"
        type="file"
        accept=".txt,.md,.epub"
        class="file-input"
        @change="onFileChange"
      />

      <div v-if="uploading" class="upload-status">
        <div class="spinner" />
        <p>正在上传并分析章节...</p>
      </div>

      <div v-else class="upload-prompt">
        <p class="upload-text">
          拖拽小说文件到此处，或<span class="link">点击选择文件</span>
        </p>
        <p class="upload-hint">支持 .txt / .md / .epub 格式，最大 10MB</p>
      </div>
    </div>

    <!-- 错误提示 -->
    <div v-if="error" class="error-msg">{{ error }}</div>

    <!-- 分章结果 -->
    <div v-if="result" class="chapter-result">
      <div class="chapter-header">
        <h3>分章结果（{{ result.chapters.length }} 章）</h3>
        <button class="btn-toggle" @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</button>
      </div>
      <ul class="chapter-list">
        <li v-for="ch in result.chapters" :key="ch.index" class="chapter-item" :class="{ unselected: !selected.has(ch.index) }">
          <input type="checkbox" :checked="selected.has(ch.index)" @change="toggleChapter(ch.index)" />
          <span class="ch-index">{{ ch.index }}</span>
          <span class="ch-title">{{ ch.title }}</span>
          <span class="ch-count">{{ ch.charCount }} 字</span>
        </li>
      </ul>
      <p class="select-count">已选 {{ selected.size }}/{{ result.chapters.length }} 章</p>
      <button class="btn" @click="$emit('confirm', result.novelId, Array.from(selected))">
        确认并继续
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { uploadNovel } from "@/api/novel";
import type { UploadResultDto } from "@/types";

/** Confirm 事件，传递 novelId 和选中的章节序号 */
defineEmits<{
  confirm: [novelId: string, chapterNumbers: number[]];
}>();

const fileInput = ref<HTMLInputElement | null>(null);
const dragging = ref(false);
const uploading = ref(false);
const error = ref("");
const result = ref<UploadResultDto | null>(null);
const selected = ref<Set<number>>(new Set());

function initSelection(chapterCount: number) {
  selected.value = new Set(Array.from({ length: chapterCount }, (_, i) => i + 1));
}
const allSelected = computed(() => result.value ? selected.value.size === result.value.chapters.length : false);

function toggleAll() {
  if (!result.value) return;
  if (allSelected.value) selected.value = new Set();
  else initSelection(result.value.chapters.length);
}
function toggleChapter(n: number) {
  const s = new Set(selected.value);
  s.has(n) ? s.delete(n) : s.add(n);
  selected.value = s;
}

function triggerInput() {
  if (!uploading.value) fileInput.value?.click();
}

function onDrop(e: DragEvent) {
  dragging.value = false;
  const files = e.dataTransfer?.files;
  if (files?.length) handleFile(files[0]);
}

function onFileChange() {
  const files = fileInput.value?.files;
  if (files?.length) handleFile(files[0]);
}

async function handleFile(file: File) {
  const ext = file.name.split(".").pop()?.toLowerCase();
  if (ext !== "txt" && ext !== "md" && ext !== "epub") {
    error.value = "仅支持 .txt / .md / .epub 格式";
    return;
  }

  error.value = "";
  uploading.value = true;
  result.value = null;

  try {
    result.value = await uploadNovel(file);
    initSelection(result.value.chapters.length);
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : "上传失败";
  } finally {
    uploading.value = false;
  }
}
</script>

<style scoped>
.upload-panel { max-width: 640px; margin: 0 auto; }

.drop-zone {
  border: 2px dashed #d1d5db;
  border-radius: 12px;
  padding: 48px;
  text-align: center;
  cursor: pointer;
  transition: all .15s;
  background: #fff;
}
.drop-zone:hover, .drop-zone.dragging {
  border-color: #6366f1;
  background: #eef2ff;
}
.drop-zone.uploading { cursor: default; opacity: .7; }

.file-input { display: none; }

.upload-text { font-size: 1.1rem; color: #1a1a2e; }
.link { color: #6366f1; text-decoration: underline; }
.upload-hint { font-size: .85rem; color: #9ca3af; margin-top: 8px; }

.upload-status { display: flex; flex-direction: column; align-items: center; gap: 16px; }

.spinner {
  width: 36px; height: 36px;
  border: 3px solid #e5e7eb; border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin .8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.error-msg {
  margin-top: 8px; padding: 8px 16px;
  background: #fee2e2; color: #ef4444; border-radius: 6px; font-size: .9rem;
}

.chapter-result { margin-top: 24px; }
.chapter-result h3 { margin-bottom: 12px; }

.chapter-list {
  list-style: none; display: flex; flex-direction: column; gap: 4px;
  margin-bottom: 16px;
}

.chapter-item {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 16px; background: #f9fafb; border-radius: 6px;
}
.ch-index { font-weight: 600; color: #6366f1; min-width: 24px; }
.ch-title { flex: 1; }
.ch-count { color: #9ca3af; font-size: .85rem; }

.btn {
  padding: 10px 32px; border: none; border-radius: 8px;
  background: #6366f1; color: #fff; font-size: 1rem; cursor: pointer;
}
.btn:hover { background: #4f46e5; }
</style>
