<template>
  <div class="history-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>修复历史记录</span>
          <div class="filter-controls">
            <el-button type="success" plain :disabled="selectedRecordIds.length === 0" @click="batchDownloadImages">
              批量下载
            </el-button>
            <el-input v-model="searchKeyword" placeholder="搜索损伤类型..." style="width: 180px" clearable
              @input="handleSearch" />
            <el-select v-model="filterStatus" placeholder="按状态筛选" style="width: 130px" clearable>
              <el-option label="全部" value="" />
              <el-option label="待处理" :value="0" />
              <el-option label="处理中" :value="1" />
              <el-option label="已完成" :value="2" />
              <el-option label="失败" :value="3" />
            </el-select>
          </div>
        </div>
      </template>

      <el-tag v-if="isDemo" type="warning" style="margin-bottom: 12px">
        演示模式 - 以下为示例数据
      </el-tag>

      <el-table :data="filteredRecords" stripe style="width: 100%" v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="48" :selectable="row => !!row.restoredImageUrl && row.status === 2" />
        <el-table-column prop="restorationId" label="ID" width="90" />
        <el-table-column label="日期" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="损伤类型" width="120">
          <template #default="{ row }">
            {{ row.damageType || '综合修复' }}
          </template>
        </el-table-column>
        <el-table-column label="修复模式" width="100">
          <template #default="{ row }">
            {{ row.restorationMode === 'auto' ? '自动' : '手动' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="质量评分" width="100">
          <template #default="{ row }">
            <span v-if="row.qualityScore">{{ Math.round(row.qualityScore * 100) }}分</span>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">
            <span v-if="row.restorationTime">{{ (row.restorationTime / 1000).toFixed(1) }}秒</span>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.restorationId)">
              查看
            </el-button>
            <el-button v-if="row.restoredImageUrl" link type="success" @click="downloadImage(row.restorationId)">
              下载
            </el-button>
            <el-popconfirm title="确认删除该条修复记录？" confirm-button-text="确认删除" cancel-button-text="取消"
              @confirm="deleteRecord(row.restorationId)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && filteredRecords.length === 0" description="暂无修复记录" />

      <el-pagination v-if="total > 0" v-model:current-page="currentPage" v-model:page-size="pageSize"
        :page-sizes="[5, 10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px" @change="loadRecords" />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="showDetail" title="修复详情" width="80%" top="5vh">
      <div v-if="selectedRecord" class="detail-content">
        <div class="view-toggle">
          <el-radio-group v-model="viewMode" size="large">
            <el-radio-button label="slider">滑动对比</el-radio-button>
            <el-radio-button label="side">并排对比</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="viewMode === 'slider'" class="image-comparison-slider" ref="sliderContainer"
          @mousemove="handleSliderMove" @touchmove="handleSliderMove" @mouseleave="isDragging = false"
          @mouseup="isDragging = false" @touchend="isDragging = false">
          <img :src="selectedRecord.restoredImageUrl || selectedRecord.originalImageUrl" class="image-after" alt="经过修复"
            draggable="false" />
          <img :src="selectedRecord.originalImageUrl" class="image-before"
            :style="{ clipPath: `inset(0 ${100 - sliderPosition}% 0 0)` }" alt="原始图片" draggable="false" />
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
                <el-image :src="selectedRecord.originalImageUrl" fit="contain" class="comparison-image"
                  :preview-src-list="[selectedRecord.originalImageUrl]" />
              </div>
            </el-col>
            <el-col :span="12">
              <div class="comparison-card">
                <div class="card-title">修复后</div>
                <el-image :src="selectedRecord.restoredImageUrl || selectedRecord.originalImageUrl" fit="contain"
                  class="comparison-image"
                  :preview-src-list="[selectedRecord.restoredImageUrl || selectedRecord.originalImageUrl]" />
              </div>
            </el-col>
          </el-row>
        </div>

        <el-descriptions :column="2" border class="detail-info">
          <el-descriptions-item label="修复 ID">
            {{ selectedRecord.restorationId }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(selectedRecord.status)">
              {{ getStatusText(selectedRecord.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="损伤类型">
            {{ selectedRecord.damageType || '综合修复' }}
          </el-descriptions-item>
          <el-descriptions-item label="修复模式">
            {{ selectedRecord.restorationMode === 'auto' ? '自动模式' : '手动模式' }}
          </el-descriptions-item>
          <el-descriptions-item label="质量评分">
            {{ selectedRecord.qualityScore ? Math.round(selectedRecord.qualityScore * 100) + '分' : '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="处理耗时">
            {{ selectedRecord.restorationTime ? (selectedRecord.restorationTime / 1000).toFixed(1) + ' 秒' : '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDate(selectedRecord.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="文件大小">
            {{ formatFileSize(selectedRecord.fileSize) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { CaretLeft, CaretRight } from '@element-plus/icons-vue'
import axios from '@/api/axios'
import { ElMessage } from 'element-plus'

const records = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchKeyword = ref('')
const filterStatus = ref('')
const showDetail = ref(false)
const selectedRecord = ref(null)
const isDemo = ref(false)
const selectedRecordIds = ref([])

// View Mode State
const viewMode = ref('slider') // slider or side

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

// 演示用假数据
const mockRecords = [
  {
    restorationId: 1001,
    userId: 1,
    originalImageUrl: null,
    restoredImageUrl: null,
    damageType: '划痕修复',
    restorationMode: 'auto',
    qualityScore: 0.92,
    fileSize: 1245678,
    restorationTime: 3241,
    status: 2,
    createTime: '2026-03-01T10:30:00'
  },
  {
    restorationId: 1002,
    userId: 1,
    originalImageUrl: null,
    restoredImageUrl: null,
    damageType: '噪点去除',
    restorationMode: 'auto',
    qualityScore: 0.88,
    fileSize: 987654,
    restorationTime: 2860,
    status: 2,
    createTime: '2026-02-28T15:20:00'
  },
  {
    restorationId: 1003,
    userId: 1,
    originalImageUrl: null,
    restoredImageUrl: null,
    damageType: '色彩恢复',
    restorationMode: 'manual',
    qualityScore: 0.95,
    fileSize: 2345678,
    restorationTime: 4120,
    status: 2,
    createTime: '2026-02-25T09:10:00'
  },
  {
    restorationId: 1004,
    userId: 1,
    originalImageUrl: null,
    restoredImageUrl: null,
    damageType: '模糊增强',
    restorationMode: 'auto',
    qualityScore: 0.79,
    fileSize: 1567890,
    restorationTime: 5300,
    status: 2,
    createTime: '2026-02-20T14:05:00'
  },
  {
    restorationId: 1005,
    userId: 1,
    originalImageUrl: null,
    restoredImageUrl: null,
    damageType: '综合修复',
    restorationMode: 'auto',
    qualityScore: null,
    fileSize: 876543,
    restorationTime: null,
    status: 3,
    createTime: '2026-02-15T11:30:00'
  }
]

const filteredRecords = computed(() => {
  return records.value.filter(record => {
    const matchSearch = !searchKeyword.value ||
      String(record.restorationId).includes(searchKeyword.value) ||
      (record.damageType && record.damageType.includes(searchKeyword.value))
    const matchStatus = filterStatus.value === '' ||
      filterStatus.value === null ||
      record.status === filterStatus.value
    return matchSearch && matchStatus
  })
})

const getStatusText = (status) => {
  const statusMap = { 0: '待处理', 1: '处理中', 2: '已完成', 3: '失败' }
  return statusMap[status] || '未知'
}

const getStatusType = (status) => {
  const typeMap = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return typeMap[status] || 'info'
}

const formatDate = (dateStr) => {
  if (!dateStr) return '--'
  try {
    return new Date(dateStr).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit'
    })
  } catch {
    return dateStr
  }
}

const formatFileSize = (bytes) => {
  if (!bytes) return '--'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i]
}

const loadRecords = async () => {
  loading.value = true
  try {
    const response = await axios.get('/restoration/records', {
      params: {
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    records.value = response.data.data.content || []
    total.value = response.data.data.totalElements || 0
    isDemo.value = false
  } catch (error) {
    records.value = []
    total.value = 0
    isDemo.value = false
    ElMessage.error(error.message || '加载修复历史失败')
  } finally {
    loading.value = false
  }
}

const viewDetail = async (restorationId) => {
  sliderPosition.value = 50
  if (isDemo.value) {
    selectedRecord.value = records.value.find(r => r.restorationId === restorationId)
    showDetail.value = true
    return
  }
  try {
    const response = await axios.get(`/restoration/records/${restorationId}`)
    selectedRecord.value = response.data.data
    showDetail.value = true
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

const downloadImage = (restorationId) => {
  const link = document.createElement('a')
  link.href = `/api/restoration/download/${restorationId}`
  link.download = `restored_${restorationId}.jpg`
  link.click()
}

const handleSelectionChange = (selection) => {
  selectedRecordIds.value = selection
    .filter(record => record.restoredImageUrl && record.status === 2)
    .map(record => record.restorationId)
}

const batchDownloadImages = async () => {
  if (selectedRecordIds.value.length === 0) {
    ElMessage.warning('请先选择已完成的修复记录')
    return
  }

  try {
    const response = await axios.post('/restoration/batch-download', selectedRecordIds.value, {
      responseType: 'blob'
    })
    const blob = new Blob([response.data], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `restored_images_${Date.now()}.zip`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('批量下载已开始')
  } catch (error) {
    selectedRecordIds.value.forEach((restorationId, index) => {
      setTimeout(() => downloadImage(restorationId), index * 300)
    })
    ElMessage.warning('压缩包下载失败，已改为逐张下载')
  }
}

const deleteRecord = async (restorationId) => {
  if (isDemo.value) {
    records.value = records.value.filter(r => r.restorationId !== restorationId)
    total.value = records.value.length
    ElMessage.success('记录已删除（演示模式）')
    return
  }
  try {
    await axios.delete(`/restoration/records/${restorationId}`)
    ElMessage.success('记录已删除')
    loadRecords()
  } catch (error) {
    ElMessage.error('删除记录失败')
  }
}

const handleSearch = () => {
  currentPage.value = 1
}

onMounted(() => {
  loadRecords()
})
</script>

<style lang="scss" scoped>
.history-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
    font-size: 16px;
    font-weight: bold;

    .filter-controls {
      display: flex;
      gap: 10px;
    }
  }

  .detail-content {
    .view-toggle {
      display: flex;
      justify-content: center;
      margin-bottom: 20px;
    }

    h4 {
      margin-bottom: 8px;
      color: #555;
      font-size: 14px;
    }

    .image-comparison-slider {
      position: relative;
      width: 100%;
      height: 480px;
      background: #fdfdfd;
      overflow: hidden;
      border-radius: 8px;
      margin-bottom: 20px;
      user-select: none;
      border: 1px solid #eee;

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

    .detail-info {
      margin-top: 16px;
    }
  }
}
</style>
