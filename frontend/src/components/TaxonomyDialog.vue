<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowDown, ArrowUp, Delete, Edit, MagicStick, Plus } from '@element-plus/icons-vue'
import type { Category, CategoryInput, CategorySuggestion, SourceTag, Tag } from '../api/metadata'

const props = defineProps<{
  modelValue: boolean
  categories: Category[]
  tags: Tag[]
  sourceTags: SourceTag[]
  suggestions: CategorySuggestion[]
  loading: boolean
  suggesting: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  createCategory: [input: CategoryInput]
  createTag: [name: string]
  editCategory: [category: Category]
  editTag: [tag: Tag]
  moveCategory: [category: Category, direction: -1 | 1]
  mergeTag: [sourceTag: Tag, targetTagId: string]
  deleteCategory: [category: Category]
  deleteTag: [tag: Tag]
  generateSuggestions: []
  confirmSuggestions: [suggestions: CategorySuggestion[]]
}>()

const categoryName = ref('')
const categoryParentId = ref<string | null>(null)
const tagName = ref('')
const mergeTargetIds = ref<Record<string, string>>({})
const editableSuggestions = ref<CategorySuggestion[]>([])
const rootCategories = computed(() => props.categories.filter((category) => !category.parentId))

watch(() => props.suggestions, (suggestions) => {
  editableSuggestions.value = suggestions.map((suggestion) => ({ ...suggestion }))
}, { immediate: true })

function submitCategory() {
  const name = categoryName.value.trim()
  if (!name) return
  emit('createCategory', { name, parentId: categoryParentId.value, sortOrder: 0 })
  categoryName.value = ''
  categoryParentId.value = null
}

function submitTag() {
  const name = tagName.value.trim()
  if (!name) return
  emit('createTag', name)
  tagName.value = ''
}

function categoryLabel(category: Category) {
  const parent = props.categories.find((candidate) => candidate.id === category.parentId)
  return parent ? `${parent.name} / ${category.name}` : category.name
}

function canMove(category: Category, direction: -1 | 1) {
  const siblings = props.categories.filter((candidate) => candidate.parentId === category.parentId)
  const index = siblings.findIndex((candidate) => candidate.id === category.id)
  return Boolean(siblings[index + direction])
}

function mergeTag(tag: Tag, targetTagId: string | number | boolean | unknown[]) {
  if (typeof targetTagId !== 'string') return
  if (!targetTagId) return
  emit('mergeTag', tag, targetTagId)
  mergeTargetIds.value[tag.id] = ''
}

function confirmSuggestions() {
  const suggestions = editableSuggestions.value
    .map((suggestion) => ({
      ...suggestion,
      name: suggestion.name.trim(),
      definition: suggestion.definition?.trim() || '',
      scope: suggestion.scope?.trim() || '',
      exclusions: suggestion.exclusions?.trim() || '',
    }))
    .filter((suggestion) => suggestion.name)
  if (suggestions.length) emit('confirmSuggestions', suggestions)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="分类与标签"
    width="min(760px, calc(100% - 24px))"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="taxonomy-layout">
      <section class="taxonomy-section">
        <div class="taxonomy-heading">
          <div><h3>分类</h3><p>稳定的层级结构，每篇帖子选择一个。</p></div>
          <el-button :icon="MagicStick" :loading="suggesting" @click="$emit('generateSuggestions')">AI 建议</el-button>
        </div>
        <div v-if="sourceTags.length" class="source-tags">
          <span v-for="tag in sourceTags.slice(0, 10)" :key="tag.name">#{{ tag.name }} <small>{{ tag.itemCount }}</small></span>
        </div>
        <div v-if="editableSuggestions.length" class="category-suggestions">
          <div v-for="(suggestion, index) in editableSuggestions" :key="index" class="suggestion-row">
            <el-input v-model="suggestion.name" maxlength="50" />
            <el-input v-model="suggestion.definition" maxlength="300" placeholder="定义" />
          </div>
          <el-button type="primary" @click="confirmSuggestions">确认创建分类</el-button>
        </div>
        <form class="taxonomy-create category-create" @submit.prevent="submitCategory">
          <el-input v-model="categoryName" maxlength="50" placeholder="新分类名称" />
          <el-select v-model="categoryParentId" clearable placeholder="作为一级分类" style="width: 170px">
            <el-option v-for="category in rootCategories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
          <el-button native-type="submit" :icon="Plus" type="primary">添加</el-button>
        </form>
        <div v-if="categories.length" class="taxonomy-list">
          <div v-for="category in categories" :key="category.id" class="taxonomy-row">
            <span>{{ categoryLabel(category) }}<small>{{ category.itemCount }} 条</small></span>
            <div>
              <el-button text :icon="ArrowUp" :disabled="!canMove(category, -1)" aria-label="上移分类" @click="$emit('moveCategory', category, -1)" />
              <el-button text :icon="ArrowDown" :disabled="!canMove(category, 1)" aria-label="下移分类" @click="$emit('moveCategory', category, 1)" />
              <el-button text :icon="Edit" aria-label="重命名分类" @click="$emit('editCategory', category)" />
              <el-button text type="danger" :icon="Delete" aria-label="删除分类" @click="$emit('deleteCategory', category)" />
            </div>
          </div>
        </div>
        <el-empty v-else :image-size="60" description="还没有分类" />
      </section>

      <section class="taxonomy-section">
        <div class="taxonomy-heading"><div><h3>标签</h3><p>跨分类关联，一篇帖子可以有多个。</p></div></div>
        <form class="taxonomy-create" @submit.prevent="submitTag">
          <el-input v-model="tagName" maxlength="50" placeholder="例如 #AI" />
          <el-button native-type="submit" :icon="Plus" type="primary">添加</el-button>
        </form>
        <div v-if="tags.length" class="tag-manager-list">
          <span v-for="tag in tags" :key="tag.id" class="tag-manager-chip">
            #{{ tag.name }} <small>{{ tag.itemCount }}</small>
            <el-select
              v-model="mergeTargetIds[tag.id]"
              size="small"
              placeholder="合并到"
              style="width: 108px"
              @change="mergeTag(tag, $event)"
            >
              <el-option
                v-for="target in tags.filter((candidate) => candidate.id !== tag.id)"
                :key="target.id"
                :label="`#${target.name}`"
                :value="target.id"
              />
            </el-select>
            <button type="button" aria-label="重命名标签" @click="$emit('editTag', tag)"><Edit /></button>
            <button type="button" aria-label="删除标签" @click="$emit('deleteTag', tag)"><Delete /></button>
          </span>
        </div>
        <el-empty v-else :image-size="60" description="还没有标签" />
      </section>
    </div>
  </el-dialog>
</template>
