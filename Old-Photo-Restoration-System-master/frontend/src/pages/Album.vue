<template>
  <div class="album-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <template v-if="!currentGroup">
              <span>{{ isTrashView ? '回收站' : '我的相册' }}</span>
            </template>
            <template v-else>
              <el-button link type="primary" @click="backToGroups" class="back-btn">
                <el-icon><arrow-left /></el-icon> 返回相册列表
              </el-button>
              <span class="breadcrumb-separator">/</span>
              <span>{{ currentGroup }}</span>
            </template>
          </div>
          <div class="header-actions">
            <!-- Global Actions -->
            <template v-if="!currentGroup">
              <el-button v-if="!isTrashView" type="warning" plain @click="openTrash">
                <el-icon><delete /></el-icon> 回收站
              </el-button>
              <el-button v-else type="primary" plain @click="backFromTrash">
                <el-icon><arrow-left /></el-icon> 返回相册
              </el-button>
              <el-button v-if="!isTrashView" type="success" plain @click="showAddGroupDialog">
                <el-icon><folder-add /></el-icon> 新建分组
              </el-button>
              <el-button v-if="!isTrashView" type="primary" @click="handleClassify" :loading="classifying">
                <el-icon><magic-stick /></el-icon> 一键分类
              </el-button>
            </template>
            <!-- Group Actions -->
            <template v-else>
              <el-button type="primary" plain @click="toggleSelectMode" v-if="currentGroupImages.length > 0">
                <el-icon>
                  <finished />
                </el-icon> {{ isSelectMode ? '取消选择' : '批量操作' }}
              </el-button>
              <el-button type="danger" plain @click="handleDeleteGroup(currentGroup)"
                v-if="currentGroup !== '未分类'">
                <el-icon>
                  <delete />
                </el-icon> 删除该分组
              </el-button>
            </template>
          </div>
        </div>
      </template>

      <div v-if="loading" class="loading-state">
        <el-skeleton :rows="5" animated />
      </div>

      <!-- Trash View -->
      <div v-else-if="isTrashView" class="images-view">
        <div v-if="trashImages.length === 0" class="empty-state">
          <el-empty description="回收站为空" />
        </div>
        <el-row :gutter="20" v-else>
          <el-col :xs="12" :sm="8" :md="6" :lg="4" v-for="img in trashImages" :key="img.absolutePath"
            class="image-col">
            <div class="image-wrapper">
              <el-image :src="`/api/album/image?path=${encodeURIComponent(img.absolutePath)}`" fit="cover"
                class="album-image">
                <template #placeholder>
                  <div class="image-placeholder"><el-icon><picture-rounded /></el-icon></div>
                </template>
                <template #error>
                  <div class="image-error"><el-icon><warning /></el-icon></div>
                </template>
              </el-image>
              <div class="image-name" :title="img.name">{{ img.name }}</div>
              <div class="trash-actions">
                <el-button size="small" type="primary" plain @click="restoreTrashImage(img)">恢复</el-button>
                <el-button size="small" type="danger" plain @click="hardDeleteTrashImage(img)">彻底删除</el-button>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- View 1: Folder Grid -->
      <div v-else-if="!currentGroup" class="groups-view">
        <div v-if="Object.keys(groups).length === 0" class="empty-state">
          <el-empty description="相册内没有任何内容，您可以先上传照片或新建分组" />
        </div>
        <el-row :gutter="20" v-else>
          <el-col :xs="12" :sm="8" :md="6" :lg="4" v-for="(images, groupName) in groups" :key="groupName"
            class="folder-col">
            <div class="folder-wrapper" @click="openGroup(groupName)">
              <el-button v-if="groupName !== '未分类'" class="folder-delete-btn" size="small" type="danger" circle
                @click.stop="handleDeleteGroup(groupName)">
                <el-icon>
                  <delete />
                </el-icon>
              </el-button>
              <div class="folder-icon">
                <el-icon><folder-opened v-if="images.length > 0" />
                  <folder v-else />
                </el-icon>
                <div class="folder-count">{{ images.length }}</div>
              </div>
              <div class="folder-info">
                <span class="folder-name">{{ groupName }}</span>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- View 2: Images within a Folder -->
      <div v-else class="images-view">
        <div v-if="currentGroupImages.length === 0" class="empty-state">
          <el-empty description="该分组内暂无照片" />
        </div>
        <el-row :gutter="20" v-else>
          <el-col :xs="12" :sm="8" :md="6" :lg="4" v-for="img in currentGroupImages" :key="img.absolutePath"
            class="image-col">
            <div class="image-wrapper" :class="{ 'is-selected': isSelected(img) }" @click="handleImageClick(img)"
              @contextmenu.prevent="openContextMenu($event, img, currentGroup)">
              <div v-if="isSelectMode" class="select-checkbox">
                <el-checkbox :model-value="isSelected(img)" @change="toggleSelection(img)"></el-checkbox>
              </div>
              <el-image :src="`/api/album/image?path=${encodeURIComponent(img.absolutePath)}`" fit="cover"
                class="album-image" :preview-src-list="isSelectMode ? [] : previewList"
                :initial-index="getPreviewIndex(img)" hide-on-click-modal>
                <template #placeholder>
                  <div class="image-placeholder"><el-icon><picture-rounded /></el-icon></div>
                </template>
                <template #error>
                  <div class="image-error"><el-icon>
                      <warning />
                    </el-icon></div>
                </template>
              </el-image>
              <div class="image-name" :title="img.name">{{ img.name }}</div>

              <!-- Action menu -->
              <el-dropdown trigger="click" class="action-dropdown"
                @command="(command) => handleCommand(command, img, currentGroup)">
                <el-button size="small" circle>
                  <el-icon>
                    <more />
                  </el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="sr">超分/增强</el-dropdown-item>
                    <el-dropdown-item command="face">人脸修复</el-dropdown-item>
                    <el-dropdown-item command="inpaint">图片修复</el-dropdown-item>
                    <el-dropdown-item command="move" divided>移动至...</el-dropdown-item>
                    <el-dropdown-item command="delete" style="color: #f56c6c;">删除相片</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </el-col>
        </el-row>

        <!-- Batch Actions Footer -->
        <transition name="el-zoom-in-bottom">
          <div v-if="isSelectMode" class="batch-footer">
            <div class="batch-info">
              已选择 {{ selectedImages.length }} 张照片
            </div>
            <div class="batch-actions">
              <el-button type="primary" link @click="selectAll">全选</el-button>
              <el-divider direction="vertical" />
              <el-button type="danger" plain :disabled="selectedImages.length === 0" @click="handleBatchDelete">
                <el-icon>
                  <delete />
                </el-icon> 批量删除
              </el-button>
              <el-button type="primary" plain :disabled="selectedImages.length === 0" @click="handleBatchMove">
                <el-icon>
                  <folder />
                </el-icon> 批量移动
              </el-button>
            </div>
          </div>
        </transition>
      </div>
    </el-card>

    <!-- Context Menu for Right Click -->
    <div v-show="contextMenuVisible" :style="{ left: contextMenuX + 'px', top: contextMenuY + 'px' }"
      class="context-menu" @mouseleave="contextMenuVisible = false">
      <ul>
        <li @click="handleRightClickCommand('sr')">超分/增强</li>
        <li @click="handleRightClickCommand('face')">人脸修复</li>
        <li @click="handleRightClickCommand('inpaint')">图片修复</li>
        <li class="divider"></li>
        <li @click="handleRightClickCommand('move')">移动至...</li>
        <li @click="handleRightClickCommand('delete')" class="danger-text">删除相片</li>
      </ul>
    </div>

    <!-- Add Group Dialog -->
    <el-dialog v-model="addGroupDialogVisible" title="新建分组" width="400px">
      <el-form label-position="top">
        <el-form-item label="分组名称">
          <el-input v-model="newGroupName" placeholder="输入分组名称（如：风景、人物）"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="addGroupDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmAddGroup" :loading="addingGroup">创建</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Move Image Dialog -->
    <el-dialog v-model="saveDialogVisible" :title="isMoving ? '移动图片' : '保存已处理相片'" width="400px" @close="resetSaveFlow">
      <el-form :model="saveForm">
        <el-form-item :label="isMoving ? '目标分组' : '保存分组'">
          <el-select v-model="saveForm.group" placeholder="选择或输入目标分组" filterable allow-create default-first-option
            style="width: 100%">
            <el-option v-for="(images, name) in groups" :key="name" :label="name" :value="name" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="saveDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmSave" :loading="saving">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Processing Full Screen Loading -->
    <div v-show="processing" class="processing-overlay">
      <div class="spinner"></div>
      <h3>{{ processingText }}</h3>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { MagicStick, Folder, FolderOpened, FolderAdd, PictureRounded, Warning, More, ArrowLeft, Delete, Finished } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from '@/api/axios'

// Global State
const groups = ref({})
const loading = ref(true)
const classifying = ref(false)
const isTrashView = ref(false)
const trashImages = ref([])

// View State
const currentGroup = ref(null)

// Batch Select State
const isSelectMode = ref(false)
const selectedImages = ref([])
const isBatchMoving = ref(false)

const currentGroupImages = computed(() => {
  if (!currentGroup.value) return []
  return groups.value[currentGroup.value] || []
})

const previewList = computed(() => {
  return currentGroupImages.value.map(img => `/api/album/image?path=${encodeURIComponent(img.absolutePath)}`)
})

const getPreviewIndex = (img) => {
  return currentGroupImages.value.findIndex(i => i.absolutePath === img.absolutePath)
}

// Add Group State
const addGroupDialogVisible = ref(false)
const newGroupName = ref('')
const addingGroup = ref(false)

// Context Menu State
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const activeImage = ref(null)
const activeGroup = ref('')

// Processing State
const processing = ref(false)
const processingText = ref('处理中...')

// Save/Move Dialog State
const saveDialogVisible = ref(false)
const isMoving = ref(false)
const saving = ref(false)
const processedImagePath = ref('')
const saveForm = ref({ group: '' })

// ----- Methods -----

const fetchGroups = async () => {
  loading.value = true
  try {
    const res = await axios.get('/album/groups')
    if (res.data && res.data.code === 200) {
      groups.value = res.data.data
    } else {
      ElMessage.error('获取相册数据失败')
    }
  } catch (error) {
    ElMessage.error('获取相册数据异常')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const fetchTrash = async () => {
  loading.value = true
  try {
    const res = await axios.get('/album/trash')
    if (res.data && res.data.code === 200) {
      trashImages.value = res.data.data || []
    } else {
      ElMessage.error(res.data.message || '获取回收站失败')
    }
  } catch (error) {
    ElMessage.error('获取回收站异常')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const openTrash = async () => {
  currentGroup.value = null
  isSelectMode.value = false
  selectedImages.value = []
  isTrashView.value = true
  await fetchTrash()
}

const backFromTrash = async () => {
  isTrashView.value = false
  trashImages.value = []
  await fetchGroups()
}

const openGroup = (groupName) => {
  currentGroup.value = groupName
}

const backToGroups = () => {
  currentGroup.value = null
  isSelectMode.value = false
  selectedImages.value = []
}

const showAddGroupDialog = () => {
  newGroupName.value = ''
  addGroupDialogVisible.value = true
}

const confirmAddGroup = async () => {
  if (!newGroupName.value.trim()) {
    ElMessage.warning('分组名称不能为空')
    return
  }
  addingGroup.value = true
  try {
    const res = await axios.post('/album/group/add', { name: newGroupName.value })
    if (res.data && res.data.code === 200) {
      ElMessage.success('创建成功')
      addGroupDialogVisible.value = false
      await fetchGroups()
    } else {
      ElMessage.error(res.data.message || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建异常')
  } finally {
    addingGroup.value = false
  }
}

const handleDeleteGroup = async (groupName) => {
  try {
    await ElMessageBox.confirm('确定删除该分组及其下所有照片吗？此操作不可恢复。', '提示', { type: 'warning' })
    const res = await axios.delete(`/album/group?name=${encodeURIComponent(groupName)}`)
    if (res.data && res.data.code === 200) {
      ElMessage.success('删除成功')
      backToGroups()
      await fetchGroups()
    } else {
      ElMessage.error(res.data.message || '删除失败')
    }
  } catch (cancel) {
    // Ignored
  }
}

const handleClassify = async () => {
  classifying.value = true
  try {
    const res = await axios.post('/album/classify')
    if (res.data && res.data.code === 200) {
      ElMessage.success(`自动分类成功，共整理 ${res.data.data.classifiedCount} 张图片。`)
      await fetchGroups()
    } else {
      ElMessage.error(res.data.message || '分类失败')
    }
  } catch (error) {
    ElMessage.error('分类发生异常')
    console.error(error)
  } finally {
    classifying.value = false
  }
}

const openContextMenu = (e, img, groupName) => {
  e.preventDefault()
  contextMenuVisible.value = true
  contextMenuX.value = e.clientX
  contextMenuY.value = e.clientY
  activeImage.value = img
  activeGroup.value = groupName
}

const handleCommand = (command, img, groupName) => {
  activeImage.value = img
  activeGroup.value = groupName
  processSelectedImage(command)
}

const handleRightClickCommand = (command) => {
  contextMenuVisible.value = false
  if (activeImage.value) {
    processSelectedImage(command)
  }
}

const processSelectedImage = async (command) => {
  if (!activeImage.value) return

  if (command === 'delete') {
    handleDeleteImage(activeImage.value)
    return
  }

  if (command === 'move') {
    isMoving.value = true
    saveForm.value.group = activeGroup.value
    processedImagePath.value = activeImage.value.absolutePath
    saveDialogVisible.value = true
    return
  }

  // Logic for sr, face, inpaint
  processing.value = true
  processingText.value = '正在处理图片，请稍等...'

  try {
    const res = await axios.post('/album/process', {
      path: activeImage.value.absolutePath,
      mode: command
    })

    if (res.data && res.data.code === 200) {
      processingText.value = '处理完成！'
      setTimeout(() => {
        processing.value = false
        processedImagePath.value = res.data.data
        isMoving.value = false
        saveForm.value.group = activeGroup.value || '未分类'
        saveDialogVisible.value = true
      }, 800)
    } else {
      processing.value = false
      ElMessage.error(res.data.message || '处理失败')
    }
  } catch (error) {
    processing.value = false
    ElMessage.error('处理发生异常')
    console.error(error)
  }
}

const handleDeleteImage = async (img) => {
  try {
    await ElMessageBox.confirm('确定要删除这张照片吗？', '警告', { type: 'warning' })
    const res = await axios.delete(`/album/image?path=${encodeURIComponent(img.absolutePath)}`)
    if (res.data && res.data.code === 200) {
      ElMessage.success('相片已删除')
      await fetchGroups()
    } else {
      ElMessage.error(res.data.message || '删除相片失败')
    }
  } catch (cancel) { }
}

const restoreTrashImage = async (img) => {
  try {
    const res = await axios.post('/album/trash/restore', { path: img.absolutePath })
    if (res.data && res.data.code === 200) {
      ElMessage.success('照片已恢复')
      await fetchTrash()
    } else {
      ElMessage.error(res.data.message || '恢复失败')
    }
  } catch (error) {
    ElMessage.error('恢复异常')
  }
}

const hardDeleteTrashImage = async (img) => {
  try {
    await ElMessageBox.confirm('确定要彻底删除这张照片吗？此操作不可恢复。', '彻底删除', { type: 'warning' })
    const res = await axios.delete(`/album/trash/hard-delete?path=${encodeURIComponent(img.absolutePath)}`)
    if (res.data && res.data.code === 200) {
      ElMessage.success('照片已彻底删除')
      await fetchTrash()
    } else {
      ElMessage.error(res.data.message || '彻底删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('彻底删除异常')
    }
  }
}

const toggleSelectMode = () => {
  isSelectMode.value = !isSelectMode.value
  selectedImages.value = []
}

const isSelected = (img) => {
  return selectedImages.value.some(i => i.absolutePath === img.absolutePath)
}

const toggleSelection = (img) => {
  const index = selectedImages.value.findIndex(i => i.absolutePath === img.absolutePath)
  if (index > -1) {
    selectedImages.value.splice(index, 1)
  } else {
    selectedImages.value.push(img)
  }
}

const handleImageClick = (img) => {
  if (isSelectMode.value) {
    toggleSelection(img)
  }
}

const selectAll = () => {
  if (selectedImages.value.length === currentGroupImages.value.length) {
    selectedImages.value = []
  } else {
    selectedImages.value = [...currentGroupImages.value]
  }
}

const handleBatchDelete = async () => {
  if (selectedImages.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedImages.value.length} 张照片吗？`, '批量删除警告', { type: 'warning' })

    saving.value = true // reuse loading state
    // Simple serial execution to avoid complex backend batch endpoint for now
    let successCount = 0
    for (const img of selectedImages.value) {
      try {
        await axios.delete(`/album/image?path=${encodeURIComponent(img.absolutePath)}`)
        successCount++
      } catch (e) { }
    }

    ElMessage.success(`成功删除 ${successCount} 张照片`)
    isSelectMode.value = false
    selectedImages.value = []
    await fetchGroups()
  } catch (cancel) {
  } finally {
    saving.value = false
  }
}

const handleBatchMove = () => {
  if (selectedImages.value.length === 0) return
  isBatchMoving.value = true
  isMoving.value = true
  saveForm.value.group = currentGroup.value
  saveDialogVisible.value = true
}

const confirmSave = async () => {
  if (!saveForm.value.group) {
    ElMessage.warning('请选择一个目标分组')
    return
  }

  saving.value = true
  try {
    if (isBatchMoving.value) {
      let successCount = 0
      for (const img of selectedImages.value) {
        try {
          await axios.post('/album/save', { path: img.absolutePath, group: saveForm.value.group })
          successCount++
        } catch (e) { }
      }
      ElMessage.success(`成功批量移动 ${successCount} 张相片`)
      isSelectMode.value = false
      selectedImages.value = []
    } else {
      const res = await axios.post('/album/save', {
        path: processedImagePath.value,
        group: saveForm.value.group
      })
      if (res.data && res.data.code === 200) {
        ElMessage.success(isMoving.value ? '移动成功' : '保存成功')
      } else {
        ElMessage.error(res.data.message || '操作失败')
      }
    }
    saveDialogVisible.value = false
    await fetchGroups()
  } catch (error) {
    ElMessage.error('发生异常')
    console.error(error)
  } finally {
    saving.value = false
  }
}

const resetSaveFlow = () => {
  processedImagePath.value = ''
  isMoving.value = false
  isBatchMoving.value = false
}

const handleClickOutsideMenu = () => {
  contextMenuVisible.value = false
}

onMounted(() => {
  fetchGroups()
  document.addEventListener('click', handleClickOutsideMenu)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutsideMenu)
})
</script>

<style lang="scss" scoped>
.album-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 18px;
    font-weight: bold;

    .header-left {
      display: flex;
      align-items: center;
      gap: 10px;

      .back-btn {
        font-size: 16px;
        font-weight: bold;
      }

      .breadcrumb-separator {
        color: #909399;
        font-size: 14px;
        margin: 0 4px;
      }
    }

    .header-actions {
      display: flex;
      gap: 10px;
    }
  }

  .loading-state,
  .empty-state {
    padding: 60px 0;
  }

  /* Folder View Styles */
  .groups-view {
    .folder-col {
      margin-bottom: 20px;
    }

    .folder-wrapper {
      position: relative;
      background: #fdfdfd;
      border: 1px solid #ebeef5;
      border-radius: 8px;
      padding: 20px;
      text-align: center;
      cursor: pointer;
      transition: all 0.3s ease;

      &:hover {
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
        transform: translateY(-2px);
        background: #f4f6fb;
        border-color: #d1dbe5;
      }

      .folder-icon {
        position: relative;
        font-size: 64px;
        color: #e6a23c;
        margin-bottom: 10px;
        display: inline-block;

        .folder-count {
          position: absolute;
          bottom: 2px;
          right: -5px;
          background: #f56c6c;
          color: white;
          font-size: 12px;
          font-weight: bold;
          padding: 2px 6px;
          border-radius: 10px;
          border: 2px solid white;
          line-height: 1;
        }
      }

      .folder-info {
        .folder-name {
          font-size: 15px;
          color: #303133;
          font-weight: 500;
        }
      }

      .folder-delete-btn {
        position: absolute;
        top: 8px;
        right: 8px;
        z-index: 2;
      }
    }
  }

  /* Images View Styles */
  .images-view {
    .image-col {
      margin-bottom: 20px;
    }

    .image-wrapper {
      position: relative;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
      transition: transform 0.3s, box-shadow 0.3s;
      background: #fff;
      border: 1px solid #ebeef5;

      &:hover {
        transform: translateY(-4px);
        box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.1);

        .action-dropdown {
          opacity: 1;
        }
      }

      .album-image {
        width: 100%;
        height: 160px;
        display: block;
      }

      .select-checkbox {
        position: absolute;
        top: 8px;
        left: 8px;
        z-index: 10;
        background: rgba(255, 255, 255, 0.9);
        border-radius: 4px;
        padding: 0 4px;
      }

      &.is-selected {
        border-color: #409eff;
        box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.4);
      }

      .image-placeholder,
      .image-error {
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        background: #f5f7fa;
        color: #c0c4cc;
        font-size: 24px;
      }

      .image-name {
        padding: 8px 12px;
        font-size: 13px;
        color: #606266;
        text-align: center;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        border-top: 1px solid #f0f2f5;
        background: #fafafa;
      }

      .trash-actions {
        display: flex;
        gap: 8px;
        justify-content: center;
        padding: 8px;
        border-top: 1px solid #f0f2f5;
        background: #fff;
      }

      .action-dropdown {
        position: absolute;
        top: 8px;
        right: 8px;
        opacity: 0;
        transition: opacity 0.2s;
        background: white;
        border-radius: 50%;
      }
    }

    .batch-footer {
      position: fixed;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: #fff;
      padding: 12px 24px;
      border-radius: 30px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
      display: flex;
      align-items: center;
      gap: 20px;
      z-index: 2000;
      border: 1px solid #ebeef5;

      .batch-info {
        font-weight: bold;
        color: #409eff;
        font-size: 14px;
      }

      .batch-actions {
        display: flex;
        align-items: center;
        gap: 10px;
      }
    }
  }

  // Right Click Context Menu
  .context-menu {
    position: fixed;
    z-index: 3000;
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    padding: 6px 0;
    min-width: 130px;

    ul {
      list-style: none;
      margin: 0;
      padding: 0;

      li {
        padding: 8px 16px;
        font-size: 13px;
        color: #606266;
        cursor: pointer;
        transition: background 0.2s;

        &:hover {
          background: #f5f7fa;
          color: #409EFF;
        }

        &.divider {
          height: 1px;
          background: #ebeef5;
          margin: 4px 0;
          padding: 0;
          pointer-events: none;
        }

        &.danger-text {
          color: #f56c6c;

          &:hover {
            background: #fef0f0;
          }
        }
      }
    }
  }

  // Processing Overlay
  .processing-overlay {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(255, 255, 255, 0.9);
    z-index: 4000;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;

    h3 {
      margin-top: 20px;
      color: #409eff;
      font-weight: normal;
    }

    .spinner {
      width: 50px;
      height: 50px;
      border: 4px solid #f3f3f3;
      border-top: 4px solid #409eff;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }
  }
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}
</style>
