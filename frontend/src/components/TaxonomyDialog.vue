<script setup lang="ts">
import { computed, ref } from 'vue'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import type { Category, CategoryInput, Tag } from '../api/metadata'

const props = defineProps<{
  modelValue: boolean
  categories: Category[]
  tags: Tag[]
  loading: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  createCategory: [input: CategoryInput]
  createTag: [name: string]
  editCategory: [category: Category]
  editTag: [tag: Tag]
  deleteCategory: [category: Category]
  deleteTag: [tag: Tag]
  clearCategory: [category: Category]
  clearLibrary: []
}>()

const categoryName = ref('')
const categoryParentId = ref<string | null>(null)
const tagName = ref('')
const rootCategories = computed(() => props.categories.filter((category) => !category.parentId))

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
        <div class="taxonomy-heading"><div><h3>分类</h3><p>稳定的层级结构，每篇帖子选择一个。</p></div></div>
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
              <el-button text type="danger" aria-label="清空此分类" @click="$emit('clearCategory', category)">清空</el-button>
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
            <button type="button" aria-label="重命名标签" @click="$emit('editTag', tag)"><Edit /></button>
            <button type="button" aria-label="删除标签" @click="$emit('deleteTag', tag)"><Delete /></button>
          </span>
        </div>
        <el-empty v-else :image-size="60" description="还没有标签" />
      </section>
    </div>
    <template #footer>
      <el-button type="danger" plain @click="$emit('clearLibrary')">清空整个知识库</el-button>
    </template>
  </el-dialog>
</template>
