<template>
  <div class="restoration-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>照片修复</span>
          <el-tag v-if="isDemo" type="warning">演示模式</el-tag>
        </div>
      </template>

      <el-row :gutter="24">
        <!-- 上传区域 -->
        <el-col :xs="24" :md="12">
          <h3>上传原始照片</h3>
          <div class="upload-box" v-if="!selectedFile">
            <el-upload drag action="#" :auto-upload="false" :on-change="handleFileChange" :show-file-list="false"
              accept="image/*">
              <el-icon class="el-icon--upload" size="48">
                <upload-filled />
              </el-icon>
              <div class="el-upload__text">
                将图片拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持格式：JPG、PNG、GIF，文件不超过 50MB
                </div>
              </template>
            </el-upload>
          </div>

          <div v-else class="file-info-card">
            <div class="file-detail">
              <el-icon size="32" color="#409eff"><picture-filled /></el-icon>
              <div class="file-meta">
                <span class="file-name">{{ selectedFile.name }}</span>
                <span class="file-size">{{ (selectedFile.size / 1024 / 1024).toFixed(2) }} MB</span>
              </div>
            </div>
            <el-button @click="resetForm" size="small" plain>重新选择图片</el-button>
          </div>

          <div class="options-box" v-if="selectedFile">
            <h4>修复选项</h4>
            <el-form label-width="80px">
              <el-form-item label="修复模式">
                <el-radio-group v-model="restorationMode">
                  <el-radio label="comprehensive">综合修复</el-radio>
                  <el-radio label="inpaint">缺损补全</el-radio>
                  <el-radio label="sr">超分增强</el-radio>
                  <el-radio label="face">人脸清晰化</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="破损标记">
                <div class="mask-tools">
                  <el-switch v-model="maskEnabled" active-text="手动涂抹" inactive-text="自动检测" @change="syncMaskCanvas" />
                  <el-slider v-model="brushSize" :min="8" :max="80" :step="2" :disabled="!maskEnabled" />
                  <el-button size="small" :disabled="!maskEnabled" @click="clearMask">清除标记</el-button>
                </div>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleUpload" :loading="uploading" size="large" style="width: 100%">
                  <el-icon v-if="!uploading" style="margin-right: 8px"><magic-stick /></el-icon>
                  {{ uploading ? 'AI 正在处理请稍候...' : '开始智能修复' }}
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-col>

        <!-- 预览区域 -->
        <el-col :xs="24" :md="12">
          <h3>图片预览</h3>
          <div class="preview-box">
            <div v-if="previewUrl" class="mask-editor">
              <img ref="previewImageRef" :src="previewUrl" class="preview-image" @load="syncMaskCanvas" />
              <canvas
                v-show="maskEnabled"
                ref="maskCanvasRef"
                class="mask-canvas"
                @pointerdown="startMaskDraw"
                @pointermove="drawMask"
                @pointerup="stopMaskDraw"
                @pointerleave="stopMaskDraw"
              ></canvas>
            </div>
            <div v-else class="preview-placeholder">
              <el-icon size="48" color="#ccc"><picture-filled /></el-icon>
              <p>暂未选择图片</p>
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 进度区域 -->
      <div v-if="uploading" class="progress-section">
        <h4>AI 修复处理中，请稍候...</h4>
        <el-progress :percentage="uploadProgress" :stroke-width="12"
          :status="uploadProgress === 100 ? 'success' : ''" />
        <p class="progress-tip">{{ progressTip }}</p>
      </div>

      <!-- 结果展示区域 -->
      <div v-if="restorationResult && !uploading" class="result-section">
        <el-divider>
          <el-tag type="success" size="large">修复完成</el-tag>
        </el-divider>

        <div class="view-toggle">
          <el-radio-group v-model="viewMode" size="large">
            <el-radio-button label="slider">滑动对比</el-radio-button>
            <el-radio-button label="side">并排对比</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="viewMode === 'slider'" class="image-comparison-slider" ref="sliderContainer"
          @mousemove="handleSliderMove" @touchmove="handleSliderMove" @mouseleave="isDragging = false"
          @mouseup="isDragging = false" @touchend="isDragging = false">
          <img :src="displayRestoredImageUrl" class="image-after" alt="经过修复" draggable="false" />
          <img :src="previewUrl" class="image-before" :style="{ clipPath: `inset(0 ${100 - sliderPosition}% 0 0)` }"
            alt="原始图片" draggable="false" />
          <div class="slider-handler" :style="{ left: sliderPosition + '%' }" @mousedown="isDragging = true"
            @touchstart="isDragging = true">
            <div class="slider-line"></div>
            <div class="slider-button">
              <el-icon><caret-left /></el-icon>
              <el-icon><caret-right /></el-icon>
            </div>
          </div>
          <el-tag type="info" class="label-before" effect="dark">原始图片</el-tag>
          <el-tag type="success" class="label-after" effect="dark">修复后</el-tag>
        </div>

        <div v-else class="side-by-side-view">
          <el-row :gutter="20">
            <el-col :span="12">
              <div class="comparison-card">
                <div class="card-title">原始图片</div>
                <el-image :src="previewUrl" fit="contain" class="comparison-image" :preview-src-list="[previewUrl]" />
              </div>
            </el-col>
            <el-col :span="12">
              <div class="comparison-card">
                <div class="card-title">修复后</div>
                <el-image :src="displayRestoredImageUrl" fit="contain" class="comparison-image"
                  :preview-src-list="[displayRestoredImageUrl]" />
              </div>
            </el-col>
          </el-row>
        </div>

        <el-card class="result-info" shadow="never">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="修复状态">
              <el-tag :type="getStatusType(restorationResult.status)">
                {{ getStatusText(restorationResult.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="质量评分">
              <el-rate v-if="restorationResult.qualityScore" :model-value="restorationResult.qualityScore * 5" disabled
                show-score score-template="{value}分" />
              <span v-else>暂无评分</span>
            </el-descriptions-item>
            <el-descriptions-item label="处理耗时">{{ restorationResult.restorationTime ? (restorationResult.restorationTime
              /
              1000).toFixed(1) + ' 秒' : '-- 秒' }}</el-descriptions-item>
            <el-descriptions-item label="损伤类型">
              {{ restorationResult.damageType || '综合修复' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <div class="action-buttons">
          <el-button type="primary" size="large" @click="downloadImage">
            下载修复后图片
          </el-button>
          <el-button type="success" size="large" @click="openSaveToAlbumDialog">
            保存到相册
          </el-button>
          <el-button size="large" @click="resetForm">继续修复其他照片</el-button>
        </div>

        <!-- Save to Album Dialog -->
        <el-dialog v-model="saveDialogVisible" title="保存到相册" width="400px">
          <el-form label-position="top">
            <el-form-item label="选择分组或输入新分组名称">
              <el-select v-model="targetGroup" placeholder="选择或输入目标分组" filterable allow-create default-first-option
                style="width: 100%">
                <el-option label="智能分类" value="智能分类" />
                <el-option v-for="group in albumGroups" :key="group" :label="group" :value="group" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <div class="dialog-footer">
              <el-button @click="saveDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="handleSaveToAlbum" :loading="savingToAlbum">确定保存</el-button>
            </div>
          </template>
        </el-dialog>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { UploadFilled, PictureFilled, CaretLeft, CaretRight, MagicStick } from '@element-plus/icons-vue'
import axios from '@/api/axios'
import { ElMessage } from 'element-plus'

const selectedFile = ref(null)
const previewUrl = ref('')
const restorationMode = ref('comprehensive')
const uploading = ref(false)
const uploadProgress = ref(0)
const restorationResult = ref(null)
const restoredImageUrl = ref('')
const displayRestoredImageUrl = computed(() => restoredImageUrl.value || previewUrl.value)
const isDemo = ref(false)
const progressTip = ref('正在分析图片...')
const maskEnabled = ref(false)
const brushSize = ref(32)
const previewImageRef = ref(null)
const maskCanvasRef = ref(null)
const isDrawingMask = ref(false)
const hasMaskStroke = ref(false)

// View Mode State
const viewMode = ref('slider') // slider or side

// Save to Album State
const saveDialogVisible = ref(false)
const targetGroup = ref('智能分类')
const albumGroups = ref([])
const savingToAlbum = ref(false)

const openSaveToAlbumDialog = async () => {
  try {
    const response = await axios.get('/album/groups')
    albumGroups.value = Object.keys(response.data.data).filter(group => group !== '智能分类')
    targetGroup.value = '智能分类'
    saveDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取相册分组失败')
  }
}

const handleSaveToAlbum = async () => {
  if (!targetGroup.value) {
    ElMessage.warning('请选择或输入分组名称')
    return
  }

  if (isDemo.value) {
    savingToAlbum.value = true
    setTimeout(() => {
      ElMessage.success('演示模式：模拟保存成功')
      savingToAlbum.value = false
      saveDialogVisible.value = false
    }, 1000)
    return
  }

  savingToAlbum.value = true
  try {
    // Extract path from restoredImageUrl
    let path = restorationResult.value?.restoredAbsolutePath

    if (!path && restoredImageUrl.value.includes('path=')) {
      const parts = restoredImageUrl.value.split('path=')
      if (parts.length > 1) {
        path = decodeURIComponent(parts[1].split('&')[0])
      }
    }

    if (!path) {
      ElMessage.error('无法确定修复后图片的路径')
      return
    }

    await axios.post('/album/save', {
      path: path,
      group: targetGroup.value
    }, {
      timeout: 300000
    })

    ElMessage.success('保存成功！可前往相册查看')
    saveDialogVisible.value = false
  } catch (error) {
    const msg = error.response?.data?.message || error.message
    if (error.response?.status === 401) {
      ElMessage.error('会话已过期或用户不存在，请重新登录后再试')
    } else {
      ElMessage.error('保存失败: ' + msg)
    }
  } finally {
    savingToAlbum.value = false
  }
}

// Slider State
const sliderPosition = ref(50)
const sliderContainer = ref(null)
const isDragging = ref(false)

const handleSliderMove = (e) => {
  if (!isDragging.value || !sliderContainer.value) return
  let clientX = e.clientX
  if (e.touches && e.touches.length > 0) {
    clientX = e.touches[0].clientX
  }
  const rect = sliderContainer.value.getBoundingClientRect()
  let x = clientX - rect.left
  if (x < 0) x = 0
  if (x > rect.width) x = rect.width
  sliderPosition.value = (x / rect.width) * 100
}

const progressTips = [
  '正在分析图片损伤程度...',
  '加载 AI 修复模型...',
  '正在修复划痕...',
  '正在去除噪点...',
  '正在恢复色彩...',
  '后处理优化中...',
  '质量评估中...',
  '修复即将完成...'
]

const syncMaskCanvas = async () => {
  await nextTick()
  const image = previewImageRef.value
  const canvas = maskCanvasRef.value
  if (!image || !canvas) return

  const rect = image.getBoundingClientRect()
  if (!rect.width || !rect.height) return

  const previous = document.createElement('canvas')
  previous.width = canvas.width || rect.width
  previous.height = canvas.height || rect.height
  const previousCtx = previous.getContext('2d')
  if (previousCtx && canvas.width && canvas.height) {
    previousCtx.drawImage(canvas, 0, 0)
  }

  canvas.width = Math.round(rect.width)
  canvas.height = Math.round(rect.height)
  canvas.style.width = `${rect.width}px`
  canvas.style.height = `${rect.height}px`

  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  if (hasMaskStroke.value && previous.width && previous.height) {
    ctx.drawImage(previous, 0, 0, canvas.width, canvas.height)
  }
}

const getMaskPoint = (event) => {
  const canvas = maskCanvasRef.value
  const rect = canvas.getBoundingClientRect()
  return {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top
  }
}

const paintMaskPoint = (event) => {
  const canvas = maskCanvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const point = getMaskPoint(event)
  ctx.fillStyle = 'rgba(255, 67, 67, 0.62)'
  ctx.beginPath()
  ctx.arc(point.x, point.y, brushSize.value / 2, 0, Math.PI * 2)
  ctx.fill()
  hasMaskStroke.value = true
}

const startMaskDraw = (event) => {
  if (!maskEnabled.value) return
  event.preventDefault()
  isDrawingMask.value = true
  event.currentTarget.setPointerCapture?.(event.pointerId)
  paintMaskPoint(event)
}

const drawMask = (event) => {
  if (!isDrawingMask.value || !maskEnabled.value) return
  event.preventDefault()
  paintMaskPoint(event)
}

const stopMaskDraw = (event) => {
  isDrawingMask.value = false
  event.currentTarget?.releasePointerCapture?.(event.pointerId)
}

const clearMask = () => {
  const canvas = maskCanvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  hasMaskStroke.value = false
}

const buildMaskBlob = () => {
  return new Promise((resolve) => {
    if (!maskEnabled.value || !hasMaskStroke.value || !maskCanvasRef.value || !previewImageRef.value) {
      resolve(null)
      return
    }

    const image = previewImageRef.value
    const displayCanvas = maskCanvasRef.value
    const sourceCanvas = document.createElement('canvas')
    sourceCanvas.width = image.naturalWidth
    sourceCanvas.height = image.naturalHeight
    const sourceCtx = sourceCanvas.getContext('2d')
    sourceCtx.drawImage(displayCanvas, 0, 0, sourceCanvas.width, sourceCanvas.height)

    const sourceData = sourceCtx.getImageData(0, 0, sourceCanvas.width, sourceCanvas.height)
    const targetCanvas = document.createElement('canvas')
    targetCanvas.width = sourceCanvas.width
    targetCanvas.height = sourceCanvas.height
    const ctx = targetCanvas.getContext('2d')
    const imageData = ctx.createImageData(targetCanvas.width, targetCanvas.height)
    const data = imageData.data
    const source = sourceData.data
    for (let i = 0; i < data.length; i += 4) {
      const marked = source[i + 3] > 0
      data[i] = marked ? 255 : 0
      data[i + 1] = marked ? 255 : 0
      data[i + 2] = marked ? 255 : 0
      data[i + 3] = 255
    }
    ctx.putImageData(imageData, 0, 0)
    targetCanvas.toBlob((blob) => resolve(blob), 'image/png')
  })
}

const handleFileChange = (file) => {
  if (file.raw) {
    selectedFile.value = file.raw
    const reader = new FileReader()
    reader.onload = (e) => {
      previewUrl.value = e.target.result
    }
    reader.readAsDataURL(file.raw)
    restorationResult.value = null
    restoredImageUrl.value = ''
    clearMask()
    nextTick(syncMaskCanvas)
  }
}

const handleUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.error('请先选择一张图片')
    return
  }
  if (restorationMode.value === 'inpaint' && (!maskEnabled.value || !hasMaskStroke.value)) {
    ElMessage.warning('缺损补全需要开启手动涂抹，并标记需要修复的破损区域')
    return
  }

  uploading.value = true
  uploadProgress.value = 0
  restorationResult.value = null

  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('mode', restorationMode.value)
  const maskBlob = await buildMaskBlob()
  if (maskBlob) {
    formData.append('mask', maskBlob, 'damage_mask.png')
  }

  try {
    const response = await axios.post('/restoration/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (event) => {
        if (event.total) {
          uploadProgress.value = Math.min(30, Math.round((event.loaded / event.total) * 30))
        }
      }
    })

    isDemo.value = false
    restorationResult.value = response.data.data
    ElMessage.success('图片上传成功，正在 AI 处理中...')

    // 轮询等待修复完成
    await pollForCompletion(response.data.data.restorationId)
  } catch (error) {
    console.error('上传失败:', error)
    const message = error.response?.data?.message || error.message || '服务遇到问题，请检查后端和模型服务'
    if (error.response?.status === 401) {
      ElMessage.error('会话已过期或用户不存在，请重新登录后再试')
    } else {
      ElMessage.error(message)
    }
    isDemo.value = false
    uploading.value = false
  }
}

// 后端可用时：轮询等待修复完成
const buildBackendImageUrl = (absolutePath) => {
  if (!absolutePath) return ''
  return `/api/album/image?path=${encodeURIComponent(absolutePath)}&t=${Date.now()}`
}

const withCacheBuster = (url) => {
  if (!url) return ''
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}t=${Date.now()}`
}

const resolveRestoredImageUrl = (data) => {
  if (data?.restoredAbsolutePath) {
    return buildBackendImageUrl(data.restoredAbsolutePath)
  }
  return withCacheBuster(data?.restoredImageUrl)
}

const applyCompletedResult = (data) => {
  restorationResult.value = data
  restoredImageUrl.value = resolveRestoredImageUrl(data)
}

const pollForCompletion = async (restorationId) => {
  const maxAttempts = 900
  for (let i = 0; i < maxAttempts; i++) {
    try {
      const pct = Math.min(30 + Math.round((i / maxAttempts) * 65), 95)
      uploadProgress.value = pct
      progressTip.value = progressTips[Math.floor(i / 8) % progressTips.length]

      const response = await axios.get(`/restoration/records/${restorationId}`)
      const data = response.data.data

      if (data.status === 2) {
        // 修复完成
        uploadProgress.value = 100
        applyCompletedResult(data)
        uploading.value = false
        ElMessage.success('修复完成！')
        return
      } else if (data.status === 3) {
        uploading.value = false
        ElMessage.error('修复失败，请重试')
        return
      }
    } catch (err) {
      console.error('轮询状态失败:', err)
    }
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  uploading.value = false
  ElMessage.warning('处理超时，请前往历史记录查看结果')
}

// 演示模式：模拟修复进度
const runDemoMode = async () => {
  const totalDuration = 4000
  const steps = 40
  const stepTime = totalDuration / steps

  for (let i = 0; i <= steps; i++) {
    uploadProgress.value = Math.round((i / steps) * 100)
    progressTip.value = progressTips[Math.floor(i / 5) % progressTips.length]
    await new Promise(resolve => setTimeout(resolve, stepTime))
  }

  // 演示结果
  restorationResult.value = {
    restorationId: Date.now(),
    status: 2,
    qualityScore: 0.85 + Math.random() * 0.1,
    restorationTime: 4000 + Math.round(Math.random() * 2000),
    damageType: ['划痕修复', '噪点去除', '色彩恢复', '综合修复'][Math.floor(Math.random() * 4)],
    restoredImageUrl: null,
    restoredAbsolutePath: '/demo/restored_image.jpg'
  }
  // 演示时使用原图作为"修复后"图片展示
  restoredImageUrl.value = previewUrl.value
  uploading.value = false
  ElMessage.success('演示修复完成！（实际使用需连接后端服务）')
}

const downloadImage = () => {
  if (isDemo.value) {
    // 演示模式：下载原图
    const link = document.createElement('a')
    link.href = previewUrl.value
    link.download = `restored_demo_${Date.now()}.jpg`
    link.click()
    ElMessage.info('演示模式：已下载原图，实际使用需连接后端服务')
    return
  }
  const link = document.createElement('a')
  link.href = `/api/restoration/download/${restorationResult.value.restorationId}`
  link.download = `restored_${Date.now()}.jpg`
  link.click()
}

const resetForm = () => {
  selectedFile.value = null
  previewUrl.value = ''
  restorationResult.value = null
  restoredImageUrl.value = ''
  uploadProgress.value = 0
  isDemo.value = false
  maskEnabled.value = false
  clearMask()
}

const getStatusText = (status) => {
  const statusMap = { 0: '待处理', 1: '处理中', 2: '已完成', 3: '失败' }
  return statusMap[status] || '未知'
}

const getStatusType = (status) => {
  const typeMap = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return typeMap[status] || 'info'
}
</script>

<style lang="scss" scoped>
.restoration-container {
  .card-header {
    font-size: 18px;
    font-weight: bold;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  h3 {
    margin-top: 0;
    margin-bottom: 15px;
    color: #333;
    font-size: 16px;
  }

  h4 {
    margin-bottom: 10px;
    color: #555;
  }

  .upload-box {
    margin-bottom: 20px;

    :deep(.el-upload-dragger) {
      width: 100%;
    }
  }

  .file-info-card {
    background: #fdfdfd;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    padding: 20px;
    margin-bottom: 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);

    .file-detail {
      display: flex;
      align-items: center;
      gap: 15px;

      .file-meta {
        display: flex;
        flex-direction: column;

        .file-name {
          font-weight: bold;
          color: #303133;
          font-size: 15px;
        }

        .file-size {
          color: #909399;
          font-size: 13px;
          margin-top: 4px;
        }
      }
    }
  }

  .options-box {
    padding: 16px;
    background: #f8f9fe;
    border-radius: 8px;
    border: 1px solid #e8eaf6;

    .mask-tools {
      width: 100%;
      display: grid;
      grid-template-columns: auto 1fr auto;
      align-items: center;
      gap: 12px;
    }
  }

  .preview-box {
    background: #f5f7fa;
    border-radius: 8px;
    padding: 20px;
    text-align: center;
    min-height: 280px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px dashed #d9d9d9;

    .mask-editor {
      position: relative;
      display: inline-block;
      max-width: 100%;

      .preview-image {
        display: block;
        max-width: 100%;
        max-height: 380px;
        border-radius: 6px;
      }

      .mask-canvas {
        position: absolute;
        inset: 0;
        z-index: 2;
        border-radius: 6px;
        cursor: crosshair;
        touch-action: none;
      }
    }

    .preview-placeholder {
      color: #999;

      p {
        margin: 12px 0 0 0;
        font-size: 14px;
      }
    }
  }

  .progress-section {
    margin-top: 30px;
    padding: 20px 24px;
    background: linear-gradient(135deg, #f0f9ff, #e8f4fd);
    border-radius: 8px;
    border: 1px solid #b3d8f0;

    h4 {
      margin: 0 0 12px 0;
      color: #409eff;
    }

    .progress-tip {
      margin: 10px 0 0 0;
      color: #666;
      font-size: 13px;
    }
  }

  .result-section {
    margin-top: 20px;

    .view-toggle {
      display: flex;
      justify-content: center;
      margin-bottom: 20px;
    }

    .image-comparison-slider {
      position: relative;
      width: 100%;
      height: 480px;
      overflow: hidden;
      border-radius: 8px;
      border: 1px solid #e0e0e0;
      background: #f5f7fa;
      user-select: none;

      .image-after,
      .image-before {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        object-fit: contain;
        pointer-events: none;
      }

      .image-after {
        z-index: 1;
      }

      .image-before {
        z-index: 2;
      }

      .slider-handler {
        position: absolute;
        top: 0;
        bottom: 0;
        width: 40px;
        transform: translateX(-50%);
        z-index: 3;
        cursor: ew-resize;
        display: flex;
        align-items: center;
        justify-content: center;

        .slider-line {
          position: absolute;
          top: 0;
          bottom: 0;
          left: 50%;
          width: 4px;
          background: white;
          transform: translateX(-50%);
          box-shadow: 0 0 4px rgba(0, 0, 0, 0.5);
        }

        .slider-button {
          position: absolute;
          width: 40px;
          height: 40px;
          background: white;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 0 4px;
          box-sizing: border-box;
          box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
          color: #666;
          font-size: 16px;
        }
      }

      .label-before,
      .label-after {
        position: absolute;
        top: 10px;
        z-index: 4;
        opacity: 0.8;
      }

      .label-before {
        left: 10px;
      }

      .label-after {
        right: 10px;
      }
    }

    .side-by-side-view {
      .comparison-card {
        background: #f5f7fa;
        border-radius: 8px;
        padding: 12px;
        border: 1px solid #e0e0e0;
        text-align: center;

        .card-title {
          font-size: 14px;
          color: #666;
          margin-bottom: 10px;
          font-weight: bold;
        }

        .comparison-image {
          width: 100%;
          height: 400px;
          background: #222;
          border-radius: 4px;
        }
      }
    }

    .result-info {
      margin: 20px 0;
      background: #fafafa;
    }

    .action-buttons {
      margin-top: 20px;
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }
  }
}
</style>
