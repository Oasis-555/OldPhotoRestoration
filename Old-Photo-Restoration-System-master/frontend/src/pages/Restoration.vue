<template>
  <div class="restoration-page">
    <el-card>
      <template #header>
        <div class="header">
          <span>照片修复</span>
          <el-tag v-if="files.length" type="info">{{ files.length }} 张待处理</el-tag>
        </div>
      </template>

      <el-row :gutter="24">
        <el-col :xs="24" :md="11">
          <h3>上传原始照片</h3>
          <el-upload
            drag
            action="#"
            multiple
            accept="image/*"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleFileChange"
          >
            <el-icon size="46"><upload-filled /></el-icon>
            <div class="el-upload__text">拖放图片到这里，或点击选择</div>
            <template #tip>
              <div class="el-upload__tip">支持多选，建议单次不超过 10 张，每张不超过 50MB</div>
            </template>
          </el-upload>

          <div v-if="files.length" class="file-list">
            <button
              v-for="(item, index) in files"
              :key="item.id"
              class="file-row"
              :class="{ active: index === activeFileIndex }"
              type="button"
              @click="selectFile(index)"
            >
              <img :src="item.previewUrl" alt="" />
              <span>
                <strong>{{ item.file.name }}</strong>
                <small>{{ formatFileSize(item.file.size) }}</small>
              </span>
              <el-icon class="remove" @click.stop="removeFile(index)"><circle-close /></el-icon>
            </button>
          </div>

          <div v-if="files.length" class="options">
            <h4>修复选项</h4>
            <el-form label-width="82px">
              <el-form-item label="修复模式">
                <el-radio-group v-model="restorationMode">
                  <el-radio label="comprehensive">综合修复</el-radio>
                  <el-radio label="inpaint">缺损补全</el-radio>
                  <el-radio label="sr">超分增强</el-radio>
                  <el-radio label="face">人脸清晰化</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="破损标记">
                <div class="mask-controls">
                  <el-switch v-model="maskEnabled" active-text="手动涂抹" inactive-text="不使用" @change="syncMaskCanvas" />
                  <el-slider v-model="brushSize" :min="8" :max="80" :step="2" :disabled="!maskEnabled" />
                  <el-button :disabled="!maskEnabled" size="small" @click="clearMask">清除</el-button>
                </div>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="large" :loading="uploading" class="start-button" @click="startBatch">
                  <el-icon v-if="!uploading"><magic-stick /></el-icon>
                  {{ uploading ? `正在处理 ${currentFileIndex}/${files.length}` : `开始修复 ${files.length} 张照片` }}
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-col>

        <el-col :xs="24" :md="13">
          <h3>图片预览与破损标记</h3>
          <div class="preview">
            <div v-if="activeFile" class="mask-editor">
              <img ref="previewImageRef" :src="activeFile.previewUrl" alt="当前照片" @load="loadActiveMask" />
              <canvas
                v-show="maskEnabled"
                ref="maskCanvasRef"
                @pointerdown="startMaskDraw"
                @pointermove="drawMask"
                @pointerup="stopMaskDraw"
                @pointerleave="stopMaskDraw"
              />
            </div>
            <div v-else class="empty-preview">
              <el-icon size="48"><picture-filled /></el-icon>
              <span>请选择需要修复的照片</span>
            </div>
          </div>
          <p v-if="files.length > 1" class="mask-tip">
            可在左侧逐张切换并标记破损区域，每张照片会保存自己的遮罩。
          </p>
        </el-col>
      </el-row>

      <div v-if="uploading" class="progress">
        <div>
          <strong>AI 批量处理中</strong>
          <span>{{ progressTip }}</span>
        </div>
        <el-progress :percentage="uploadProgress" :stroke-width="12" />
      </div>

      <section v-if="results.length && !uploading" class="results">
        <el-divider><el-tag type="success">已完成 {{ results.length }} 张</el-tag></el-divider>

        <div class="result-actions">
          <el-button type="primary" @click="downloadBatch">
            <el-icon><download /></el-icon>批量下载 ZIP
          </el-button>
          <el-button type="success" plain @click="openSaveDialog(false)">保存当前到相册</el-button>
          <el-button type="success" @click="openSaveDialog(true)">全部保存到相册</el-button>
          <el-button @click="resetForm">继续修复</el-button>
        </div>

        <el-row :gutter="20">
          <el-col :xs="24" :lg="17">
            <div v-if="activeResult" class="comparison">
              <el-radio-group v-model="viewMode" size="large">
                <el-radio-button label="slider">滑动对比</el-radio-button>
                <el-radio-button label="side">并排对比</el-radio-button>
              </el-radio-group>

              <div
                v-if="viewMode === 'slider'"
                ref="sliderContainer"
                class="slider-view"
                @pointermove="moveSlider"
                @pointerup="isDragging = false"
                @pointerleave="isDragging = false"
              >
                <img :src="resultImageUrl(activeResult)" class="after" alt="修复后" />
                <img
                  :src="activeResult.sourcePreview"
                  class="before"
                  :style="{ clipPath: `inset(0 ${100 - sliderPosition}% 0 0)` }"
                  alt="原始图片"
                />
                <div class="slider-handle" :style="{ left: sliderPosition + '%' }" @pointerdown="isDragging = true">
                  <span><el-icon><caret-left /></el-icon><el-icon><caret-right /></el-icon></span>
                </div>
              </div>

              <div v-else class="side-view">
                <div><strong>原始图片</strong><img :src="activeResult.sourcePreview" alt="原始图片" /></div>
                <div><strong>修复后</strong><img :src="resultImageUrl(activeResult)" alt="修复后" /></div>
              </div>
            </div>
          </el-col>

          <el-col :xs="24" :lg="7">
            <h4>全部结果</h4>
            <div class="result-list">
              <button
                v-for="(result, index) in results"
                :key="result.restorationId"
                type="button"
                :class="{ active: index === activeResultIndex }"
                @click="activeResultIndex = index"
              >
                <img :src="resultImageUrl(result)" alt="" />
                <span>{{ result.sourceName }}</span>
              </button>
            </div>
          </el-col>
        </el-row>
      </section>
    </el-card>

    <el-dialog v-model="saveDialogVisible" :title="saveAllResults ? '全部保存到相册' : '保存当前到相册'" width="420px">
      <el-form label-position="top">
        <el-form-item label="相册分组">
          <el-select
            v-model="targetGroup"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入分组名称"
            style="width: 100%"
          >
            <el-option label="智能分类" value="智能分类" />
            <el-option v-for="group in albumGroups" :key="group" :label="group" :value="group" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveToAlbum">确定保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import {
  CaretLeft,
  CaretRight,
  CircleClose,
  Download,
  MagicStick,
  PictureFilled,
  UploadFilled
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from '@/api/axios'

const MAX_FILES = 10
const files = ref([])
const activeFileIndex = ref(0)
const restorationMode = ref('comprehensive')
const maskEnabled = ref(false)
const brushSize = ref(32)
const previewImageRef = ref(null)
const maskCanvasRef = ref(null)
const drawing = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const progressTip = ref('')
const currentFileIndex = ref(0)
const results = ref([])
const activeResultIndex = ref(0)
const viewMode = ref('slider')
const sliderPosition = ref(50)
const sliderContainer = ref(null)
const isDragging = ref(false)
const saveDialogVisible = ref(false)
const saveAllResults = ref(false)
const albumGroups = ref([])
const targetGroup = ref('智能分类')
const saving = ref(false)

const activeFile = computed(() => files.value[activeFileIndex.value] || null)
const activeResult = computed(() => results.value[activeResultIndex.value] || null)

const fileKey = (file) => `${file.name}-${file.size}-${file.lastModified}`
const formatFileSize = (size) => `${(size / 1024 / 1024).toFixed(2)} MB`

const handleFileChange = (uploadFile) => {
  const raw = uploadFile.raw
  if (!raw || !raw.type.startsWith('image/')) return
  if (files.value.some(item => item.id === fileKey(raw))) return
  if (files.value.length >= MAX_FILES) {
    ElMessage.warning(`单次最多选择 ${MAX_FILES} 张照片`)
    return
  }
  files.value.push({
    id: fileKey(raw),
    file: raw,
    previewUrl: URL.createObjectURL(raw),
    maskDataUrl: ''
  })
  if (files.value.length === 1) {
    activeFileIndex.value = 0
  }
  results.value = []
  nextTick(loadActiveMask)
}

const persistActiveMask = () => {
  const canvas = maskCanvasRef.value
  const item = activeFile.value
  if (canvas && item) {
    item.maskDataUrl = canvas.dataset.hasMask === 'true' ? canvas.toDataURL('image/png') : ''
  }
}

const selectFile = async (index) => {
  persistActiveMask()
  activeFileIndex.value = index
  await nextTick()
  loadActiveMask()
}

const removeFile = (index) => {
  URL.revokeObjectURL(files.value[index].previewUrl)
  files.value.splice(index, 1)
  activeFileIndex.value = Math.min(activeFileIndex.value, Math.max(0, files.value.length - 1))
  results.value = []
  nextTick(loadActiveMask)
}

const syncMaskCanvas = () => nextTick(loadActiveMask)

const loadActiveMask = () => {
  const image = previewImageRef.value
  const canvas = maskCanvasRef.value
  if (!image || !canvas || !image.clientWidth || !image.clientHeight) return
  canvas.width = image.naturalWidth
  canvas.height = image.naturalHeight
  canvas.style.width = `${image.clientWidth}px`
  canvas.style.height = `${image.clientHeight}px`
  canvas.dataset.hasMask = 'false'
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  if (activeFile.value?.maskDataUrl) {
    const mask = new Image()
    mask.onload = () => {
      ctx.drawImage(mask, 0, 0, canvas.width, canvas.height)
      canvas.dataset.hasMask = 'true'
    }
    mask.src = activeFile.value.maskDataUrl
  }
}

const canvasPoint = (event) => {
  const canvas = maskCanvasRef.value
  const rect = canvas.getBoundingClientRect()
  return {
    x: (event.clientX - rect.left) * canvas.width / rect.width,
    y: (event.clientY - rect.top) * canvas.height / rect.height
  }
}

const paint = (event) => {
  const canvas = maskCanvasRef.value
  if (!canvas) return
  const point = canvasPoint(event)
  const scale = canvas.width / canvas.getBoundingClientRect().width
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = 'rgba(255, 70, 70, 0.72)'
  ctx.beginPath()
  ctx.arc(point.x, point.y, brushSize.value * scale / 2, 0, Math.PI * 2)
  ctx.fill()
  canvas.dataset.hasMask = 'true'
}

const startMaskDraw = (event) => {
  if (!maskEnabled.value) return
  drawing.value = true
  event.currentTarget.setPointerCapture?.(event.pointerId)
  paint(event)
}
const drawMask = (event) => {
  if (drawing.value && maskEnabled.value) paint(event)
}
const stopMaskDraw = (event) => {
  drawing.value = false
  event.currentTarget?.releasePointerCapture?.(event.pointerId)
}
const clearMask = () => {
  const canvas = maskCanvasRef.value
  if (!canvas) return
  canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height)
  canvas.dataset.hasMask = 'false'
  if (activeFile.value) activeFile.value.maskDataUrl = ''
}

const maskBlobFor = async (item) => {
  if (!item.maskDataUrl) return null
  const response = await fetch(item.maskDataUrl)
  const coloredMask = await createImageBitmap(await response.blob())
  const canvas = document.createElement('canvas')
  canvas.width = coloredMask.width
  canvas.height = coloredMask.height
  const ctx = canvas.getContext('2d')
  ctx.drawImage(coloredMask, 0, 0)
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
  for (let i = 0; i < imageData.data.length; i += 4) {
    const marked = imageData.data[i + 3] > 0
    imageData.data[i] = marked ? 255 : 0
    imageData.data[i + 1] = marked ? 255 : 0
    imageData.data[i + 2] = marked ? 255 : 0
    imageData.data[i + 3] = 255
  }
  ctx.putImageData(imageData, 0, 0)
  return await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
}

const startBatch = async () => {
  if (!files.value.length) {
    ElMessage.warning('请先选择照片')
    return
  }
  persistActiveMask()
  if (restorationMode.value === 'inpaint' && files.value.some(item => !item.maskDataUrl)) {
    ElMessage.warning('缺损补全模式下，请逐张标记需要修复的区域')
    return
  }

  uploading.value = true
  results.value = []
  uploadProgress.value = 0

  for (let index = 0; index < files.value.length; index++) {
    currentFileIndex.value = index + 1
    const item = files.value[index]
    progressTip.value = `正在处理：${item.file.name}`
    try {
      const formData = new FormData()
      formData.append('file', item.file)
      formData.append('mode', restorationMode.value)
      const maskBlob = await maskBlobFor(item)
      if (maskBlob) formData.append('mask', maskBlob, `mask_${index + 1}.png`)

      const uploadResponse = await axios.post('/restoration/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      const completed = await pollForCompletion(uploadResponse.data.data.restorationId, index)
      if (completed) {
        results.value.push({
          ...completed,
          sourcePreview: item.previewUrl,
          sourceName: item.file.name
        })
      }
    } catch (error) {
      ElMessage.error(`${item.file.name} 处理失败：${error.response?.data?.message || error.message}`)
    }
  }

  uploading.value = false
  uploadProgress.value = 100
  activeResultIndex.value = 0
  if (results.value.length) {
    ElMessage.success(`批量修复完成，成功 ${results.value.length} 张`)
  } else {
    ElMessage.error('本批次没有成功生成修复结果')
  }
}

const pollForCompletion = async (restorationId, fileIndex) => {
  const maxAttempts = 900
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const response = await axios.get(`/restoration/records/${restorationId}`)
    const record = response.data.data
    const itemPercent = Math.min(95, 5 + attempt / maxAttempts * 90)
    uploadProgress.value = Math.round((fileIndex * 100 + itemPercent) / files.value.length)
    if (record.status === 2) return record
    if (record.status === 3) {
      throw new Error(record.errorMessage || '模型处理失败')
    }
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error('处理超时，请前往修复历史查看')
}

const resultImageUrl = (result) => {
  if (!result) return ''
  if (result.restoredAbsolutePath) {
    return `/api/album/image?path=${encodeURIComponent(result.restoredAbsolutePath)}&t=${result.updateTime || Date.now()}`
  }
  return result.restoredImageUrl || result.sourcePreview
}

const moveSlider = (event) => {
  if (!isDragging.value || !sliderContainer.value) return
  const rect = sliderContainer.value.getBoundingClientRect()
  sliderPosition.value = Math.max(0, Math.min(100, (event.clientX - rect.left) / rect.width * 100))
}

const downloadBatch = async () => {
  const ids = results.value.map(item => item.restorationId)
  try {
    const response = await axios.post('/restoration/batch-download', ids, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `restored_photos_${Date.now()}.zip`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error('批量下载失败')
  }
}

const openSaveDialog = async (saveAll) => {
  saveAllResults.value = saveAll
  try {
    const response = await axios.get('/album/groups')
    albumGroups.value = Object.keys(response.data.data || {}).filter(group => group !== '智能分类')
    saveDialogVisible.value = true
  } catch {
    ElMessage.error('获取相册分组失败')
  }
}

const saveToAlbum = async () => {
  if (!targetGroup.value.trim()) {
    ElMessage.warning('请选择或输入相册分组')
    return
  }
  const targets = saveAllResults.value ? results.value : [activeResult.value]
  saving.value = true
  let successCount = 0
  for (const result of targets.filter(Boolean)) {
    try {
      await axios.post('/album/save', {
        path: result.restoredAbsolutePath,
        group: targetGroup.value.trim()
      }, { timeout: 300000 })
      successCount++
    } catch (error) {
      console.error('保存到相册失败', result.restorationId, error)
    }
  }
  saving.value = false
  saveDialogVisible.value = false
  if (successCount === targets.length) {
    ElMessage.success(`已保存 ${successCount} 张照片到相册`)
  } else {
    ElMessage.warning(`成功保存 ${successCount}/${targets.length} 张，请检查模型结果文件是否存在`)
  }
}

const resetForm = () => {
  files.value.forEach(item => URL.revokeObjectURL(item.previewUrl))
  files.value = []
  results.value = []
  activeFileIndex.value = 0
  activeResultIndex.value = 0
  uploadProgress.value = 0
  maskEnabled.value = false
}
</script>

<style lang="scss" scoped>
.restoration-page {
  .header { display: flex; align-items: center; gap: 10px; font-size: 18px; font-weight: 700; }
  h3 { margin: 0 0 14px; font-size: 16px; color: #303133; }
  h4 { margin: 0 0 12px; color: #303133; }
}

.file-list {
  display: grid;
  gap: 6px;
  max-height: 230px;
  overflow: auto;
  margin: 16px 0;
}
.file-row {
  width: 100%;
  height: 58px;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 28px;
  align-items: center;
  gap: 10px;
  border: 1px solid #e4e7ed;
  background: #fff;
  padding: 6px;
  text-align: left;
  cursor: pointer;
  border-radius: 6px;
  &.active { border-color: #409eff; background: #ecf5ff; }
  img { width: 44px; height: 44px; object-fit: cover; border-radius: 4px; }
  span { min-width: 0; display: flex; flex-direction: column; }
  strong { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  small { color: #909399; margin-top: 3px; }
  .remove { color: #f56c6c; font-size: 18px; }
}
.options { padding: 16px; background: #f7f8fc; border: 1px solid #e4e7ed; border-radius: 6px; }
.mask-controls { width: 100%; display: grid; grid-template-columns: auto 1fr auto; gap: 12px; align-items: center; }
.start-button { width: 100%; }

.preview {
  height: 470px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
}
.mask-editor {
  position: relative;
  display: inline-block;
  max-width: 100%;
  max-height: 100%;
  img { display: block; max-width: 100%; max-height: 468px; object-fit: contain; }
  canvas { position: absolute; inset: 0; cursor: crosshair; touch-action: none; }
}
.empty-preview { display: flex; flex-direction: column; align-items: center; gap: 10px; color: #909399; }
.mask-tip { margin: 8px 0 0; color: #909399; font-size: 13px; }

.progress {
  margin-top: 24px;
  padding: 18px;
  background: #ecf5ff;
  border: 1px solid #b3d8ff;
  border-radius: 6px;
  div { display: flex; justify-content: space-between; margin-bottom: 10px; }
  span { color: #606266; }
}
.results { margin-top: 22px; }
.result-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 20px; }
.comparison > .el-radio-group { display: flex; justify-content: center; margin-bottom: 14px; }
.slider-view {
  position: relative;
  height: 500px;
  overflow: hidden;
  background: #222;
  border-radius: 6px;
  user-select: none;
  img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; pointer-events: none; }
  .after { z-index: 1; }
  .before { z-index: 2; }
}
.slider-handle {
  position: absolute;
  z-index: 3;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #fff;
  cursor: ew-resize;
  span {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 42px;
    height: 42px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fff;
    color: #606266;
    border-radius: 50%;
    box-shadow: 0 2px 8px rgba(0,0,0,.3);
  }
}
.side-view {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  div { background: #f5f7fa; padding: 10px; text-align: center; border: 1px solid #e4e7ed; border-radius: 6px; }
  strong { display: block; margin-bottom: 8px; }
  img { width: 100%; height: 440px; object-fit: contain; background: #222; }
}
.result-list {
  display: grid;
  gap: 8px;
  max-height: 500px;
  overflow: auto;
  button {
    display: grid;
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 10px;
    align-items: center;
    min-height: 64px;
    padding: 6px;
    border: 1px solid #e4e7ed;
    background: #fff;
    border-radius: 6px;
    cursor: pointer;
    text-align: left;
    &.active { border-color: #409eff; background: #ecf5ff; }
  }
  img { width: 64px; height: 52px; object-fit: cover; border-radius: 4px; }
  span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}

@media (max-width: 768px) {
  .preview { height: 360px; }
  .mask-editor img { max-height: 358px; }
  .slider-view { height: 380px; }
  .side-view { grid-template-columns: 1fr; }
  .side-view img { height: 320px; }
}
</style>
