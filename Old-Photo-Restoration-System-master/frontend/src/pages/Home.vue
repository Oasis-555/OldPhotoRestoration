<template>
  <div class="home-container">
    <!-- 欢迎横幅 -->
    <el-card class="welcome-banner mb-20">
      <div class="welcome-content">
        <div class="welcome-text">
          <h2>欢迎回来，{{ userName }} 👋</h2>
          <p>使用 AI 技术智能修复您的珍贵老照片</p>
        </div>
        <router-link to="/restoration">
          <el-button type="primary" size="large">开始修复照片</el-button>
        </router-link>
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="mb-20">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card success">
          <div class="stat-content">
            <div class="stat-icon">✅</div>
            <div class="stat-number">{{ stats.successCount || 0 }}</div>
            <div class="stat-label">修复成功</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card info">
          <div class="stat-content">
            <div class="stat-icon">📋</div>
            <div class="stat-number">{{ stats.totalCount || recentRecords.length }}</div>
            <div class="stat-label">总记录数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card warning">
          <div class="stat-content">
            <div class="stat-icon">⏳</div>
            <div class="stat-number">{{ stats.processingCount || 0 }}</div>
            <div class="stat-label">处理中</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card purple">
          <div class="stat-content">
            <div class="stat-icon">📊</div>
            <div class="stat-number">{{ stats.avgScore || '--' }}</div>
            <div class="stat-label">平均质量分</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快速操作 -->
    <el-row :gutter="20" class="mb-20">
      <el-col :xs="24" :md="12">
        <el-card class="action-card">
          <div class="action-content">
            <div class="action-icon">🖼️</div>
            <div class="action-info">
              <h3>开始新的修复</h3>
              <p>上传照片，使用 AI 算法智能修复划痕、噪点、褪色等问题</p>
            </div>
          </div>
          <router-link to="/restoration">
            <el-button type="primary" size="large" style="width: 100%">上传照片</el-button>
          </router-link>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="action-card">
          <div class="action-content">
            <div class="action-icon">🕐</div>
            <div class="action-info">
              <h3>查看历史记录</h3>
              <p>查看之前的修复记录、对比效果、下载修复结果</p>
            </div>
          </div>
          <router-link to="/history">
            <el-button type="info" size="large" style="width: 100%">查看历史</el-button>
          </router-link>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近修复记录 -->
    <el-card class="recent-card">
      <template #header>
        <div class="card-header">
          <span>最近修复记录</span>
          <router-link to="/history">
            <el-button link type="primary">查看全部</el-button>
          </router-link>
        </div>
      </template>

      <el-table :data="recentRecords" stripe v-if="recentRecords.length > 0">
        <el-table-column prop="restorationId" label="ID" width="100" />
        <el-table-column label="日期" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="damageType" label="损伤类型">
          <template #default="{ row }">
            {{ row.damageType || '综合修复' }}
          </template>
        </el-table-column>
        <el-table-column prop="restorationMode" label="修复模式" width="100">
          <template #default="{ row }">
            {{ row.restorationMode === 'auto' ? '自动' : '手动' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.restorationId)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty description="暂无修复记录，快去上传第一张照片吧！" v-else />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/userStore'
import axios from '@/api/axios'
import { useRouter } from 'vue-router'

const router = useRouter()
const userStore = useUserStore()
const userName = computed(() => userStore.userName || '用户')

const stats = ref({
  successCount: 0,
  totalCount: 0,
  processingCount: 0,
  avgScore: '--'
})

const recentRecords = ref([])

// 演示用假数据
const mockRecords = [
  {
    restorationId: 1001,
    damageType: '划痕修复',
    restorationMode: 'auto',
    status: 2,
    qualityScore: 0.92,
    createTime: '2026-03-01T10:30:00'
  },
  {
    restorationId: 1002,
    damageType: '噪点去除',
    restorationMode: 'auto',
    status: 2,
    qualityScore: 0.88,
    createTime: '2026-02-28T15:20:00'
  },
  {
    restorationId: 1003,
    damageType: '色彩恢复',
    restorationMode: 'manual',
    status: 2,
    qualityScore: 0.95,
    createTime: '2026-02-25T09:10:00'
  }
]

const mockStats = {
  successCount: 3,
  totalCount: 5,
  processingCount: 0,
  avgScore: '91分'
}

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

const viewDetail = (restorationId) => {
  router.push(`/history`)
}

const loadStats = async () => {
  try {
    const response = await axios.get('/restoration/stats')
    const data = response.data.data
    stats.value.successCount = data.successCount || 0
    stats.value.totalCount = data.totalCount || data.successCount || 0
    stats.value.processingCount = data.processingCount || 0
    if (data.avgScore) stats.value.avgScore = Math.round(data.avgScore * 100) + '分'
  } catch (error) {
    stats.value = {
      successCount: 0,
      totalCount: 0,
      processingCount: 0,
      avgScore: '--'
    }
  }
}

const loadRecentRecords = async () => {
  try {
    const response = await axios.get('/restoration/records?page=0&size=5')
    recentRecords.value = response.data.data.content || []
  } catch (error) {
    recentRecords.value = []
  }
}

onMounted(() => {
  loadStats()
  loadRecentRecords()
})
</script>

<style lang="scss" scoped>
.home-container {
  .mb-20 {
    margin-bottom: 20px;
  }

  .welcome-banner {
    :deep(.el-card__body) {
      padding: 20px 24px;
    }

    .welcome-content {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .welcome-text {
        h2 {
          margin: 0 0 6px 0;
          font-size: 20px;
          color: #333;
        }
        p {
          margin: 0;
          color: #666;
          font-size: 14px;
        }
      }
    }
  }

  .stat-card {
    border-left: 4px solid #667eea;

    &.success { border-left-color: #67c23a; }
    &.info { border-left-color: #409eff; }
    &.warning { border-left-color: #e6a23c; }
    &.purple { border-left-color: #764ba2; }

    .stat-content {
      text-align: center;
      padding: 10px 0;

      .stat-icon {
        font-size: 28px;
        margin-bottom: 8px;
      }

      .stat-number {
        font-size: 30px;
        font-weight: bold;
        color: #333;
        margin-bottom: 4px;
      }

      .stat-label {
        color: #999;
        font-size: 13px;
      }
    }
  }

  .action-card {
    .action-content {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      margin-bottom: 16px;

      .action-icon {
        font-size: 36px;
        flex-shrink: 0;
      }

      .action-info {
        h3 {
          margin: 0 0 6px 0;
          font-size: 16px;
          color: #333;
        }
        p {
          margin: 0;
          color: #666;
          font-size: 13px;
          line-height: 1.5;
        }
      }
    }

    a {
      text-decoration: none;
    }
  }

  .recent-card {
    margin-top: 0;

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    a {
      text-decoration: none;
    }
  }
}
</style>
