<template>
  <div class="profile-container">
    <el-tag v-if="isDemo" type="warning" style="margin-bottom: 16px">
      演示模式 - 以下为示例数据
    </el-tag>

    <el-row :gutter="20">
      <el-col :xs="24" :md="6">
        <el-card class="profile-card">
          <div class="profile-header">
            <el-avatar :size="80" :src="userInfo.avatarUrl">
              {{ userInfo.userName ? userInfo.userName.charAt(0).toUpperCase() : 'U' }}
            </el-avatar>
            <h2>{{ userInfo.userName || '--' }}</h2>
            <p class="user-type">
              <el-tag type="info">{{ userInfo.userType === 2 ? '机构用户' : '个人用户' }}</el-tag>
            </p>
            <p class="user-email">{{ userInfo.email || '' }}</p>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="18">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>个人信息</span>
              <el-button type="primary" plain @click="editMode = !editMode">
                {{ editMode ? '取消编辑' : '编辑资料' }}
              </el-button>
            </div>
          </template>

          <!-- 查看模式 -->
          <el-descriptions v-if="!editMode" :column="2" border>
            <el-descriptions-item label="邮箱">
              {{ userInfo.email || '未设置' }}
            </el-descriptions-item>
            <el-descriptions-item label="手机号">
              {{ userInfo.phone || '未设置' }}
            </el-descriptions-item>
            <el-descriptions-item label="个人简介" :span="2">
              {{ userInfo.bio || '暂无简介' }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ formatDate(userInfo.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="最近登录">
              {{ formatDate(userInfo.lastLogin) }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 编辑模式 -->
          <el-form
            v-else
            :model="editForm"
            label-width="80px"
            @submit.prevent="handleSaveProfile"
          >
            <el-form-item label="邮箱">
              <el-input v-model="editForm.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="editForm.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input
                v-model="editForm.bio"
                type="textarea"
                :rows="3"
                placeholder="请输入个人简介"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSaveProfile" :loading="saving">
                保存更改
              </el-button>
              <el-button @click="editMode = false" style="margin-left: 10px">取消</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 修改密码 -->
        <el-card style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span>修改密码</span>
            </div>
          </template>

          <el-form
            :model="passwordForm"
            label-width="100px"
            style="max-width: 480px"
            @submit.prevent="handleChangePassword"
          >
            <el-form-item label="当前密码">
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                placeholder="请输入当前密码"
                show-password
              />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                placeholder="请输入新密码（至少6位）"
                show-password
              />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                show-password
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleChangePassword" :loading="changingPwd">
                确认修改
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from '@/api/axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/userStore'

const userStore = useUserStore()
const userInfo = ref({})
const editMode = ref(false)
const editForm = ref({})
const saving = ref(false)
const changingPwd = ref(false)
const isDemo = ref(false)

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 演示用假数据
const mockUser = {
  id: 1,
  userId: 1617000000000,
  userName: userStore.userName || 'testuser',
  email: 'test@example.com',
  userType: 1,
  phone: '138****8888',
  bio: '热爱摄影，喜欢修复珍贵的老照片，留住历史记忆。',
  createTime: '2026-01-01T00:00:00',
  lastLogin: '2026-03-03T08:00:00',
  isActive: true
}

const formatDate = (dateStr) => {
  if (!dateStr) return '暂无记录'
  try {
    return new Date(dateStr).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit'
    })
  } catch {
    return dateStr
  }
}

const loadUserInfo = async () => {
  try {
    const response = await axios.get('/auth/me')
    userInfo.value = response.data.data
    editForm.value = { ...userInfo.value }
    isDemo.value = false
  } catch (error) {
    userInfo.value = { ...mockUser }
    editForm.value = { ...mockUser }
    isDemo.value = true
  }
}

const handleSaveProfile = async () => {
  if (isDemo.value) {
    userInfo.value = { ...editForm.value }
    editMode.value = false
    ElMessage.success('保存成功（演示模式）')
    return
  }
  saving.value = true
  try {
    await axios.put(`/users/${userInfo.value.id}`, editForm.value)
    userInfo.value = { ...editForm.value }
    editMode.value = false
    ElMessage.success('个人资料已更新')
  } catch (error) {
    ElMessage.error('更新资料失败：' + (error.response?.data?.message || error.message))
  } finally {
    saving.value = false
  }
}

const handleChangePassword = async () => {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    ElMessage.error('请填写完整的密码信息')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.error('新密码长度不能少于6位')
    return
  }

  if (isDemo.value) {
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    ElMessage.success('密码修改成功（演示模式）')
    return
  }

  changingPwd.value = true
  try {
    await axios.post('/users/change-password', {
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    ElMessage.success('密码修改成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '密码修改失败，请检查当前密码是否正确')
  } finally {
    changingPwd.value = false
  }
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style lang="scss" scoped>
.profile-container {
  .profile-card {
    text-align: center;

    .profile-header {
      padding: 10px 0;

      .el-avatar {
        margin-bottom: 12px;
        font-size: 24px;
        background-color: #667eea;
        color: white;
      }

      h2 {
        margin: 8px 0 6px;
        font-size: 18px;
      }

      .user-type {
        margin: 6px 0 4px;
      }

      .user-email {
        color: #999;
        font-size: 13px;
        margin: 4px 0 0;
        word-break: break-all;
      }
    }
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
    font-size: 16px;
    font-weight: bold;
  }
}
</style>
