<template>
  <div class="profile-page">
    <el-row :gutter="20">
      <el-col :xs="24" :md="7">
        <el-card>
          <div class="identity">
            <el-avatar :size="82" :src="userInfo.avatarUrl">
              {{ userInfo.userName?.charAt(0)?.toUpperCase() || 'U' }}
            </el-avatar>
            <h2>{{ userInfo.userName || '--' }}</h2>
            <el-tag type="info">{{ userInfo.userType === 2 ? '机构用户' : '个人用户' }}</el-tag>
            <p>{{ userInfo.email || '未设置邮箱' }}</p>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="17">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>个人信息</span>
              <el-button type="primary" plain @click="toggleEdit">
                {{ editMode ? '取消编辑' : '编辑资料' }}
              </el-button>
            </div>
          </template>

          <el-descriptions v-if="!editMode" :column="2" border>
            <el-descriptions-item label="邮箱">{{ userInfo.email || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userInfo.phone || '未设置' }}</el-descriptions-item>
            <el-descriptions-item label="邮件通知">
              <el-tag :type="userInfo.emailNotification ? 'success' : 'info'">
                {{ userInfo.emailNotification ? '已开启' : '已关闭' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="站内通知">
              <el-tag :type="userInfo.wsNotification ? 'success' : 'info'">
                {{ userInfo.wsNotification ? '已开启' : '已关闭' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="个人简介" :span="2">{{ userInfo.bio || '暂无简介' }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatDate(userInfo.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="最近登录">{{ formatDate(userInfo.lastLogin) }}</el-descriptions-item>
          </el-descriptions>

          <el-form v-else :model="editForm" label-width="92px" @submit.prevent="saveProfile">
            <el-form-item label="邮箱">
              <el-input v-model="editForm.email" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="editForm.phone" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input v-model="editForm.bio" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="邮件通知">
              <el-switch v-model="editForm.emailNotification" />
              <span class="hint">修复完成后发送邮件</span>
            </el-form-item>
            <el-form-item label="站内通知">
              <el-switch v-model="editForm.wsNotification" />
              <span class="hint">修复完成后显示实时提醒</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveProfile">保存更改</el-button>
              <el-button @click="toggleEdit">取消</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="password-card">
          <template #header><strong>修改密码</strong></template>
          <el-form :model="passwordForm" label-width="100px" class="password-form">
            <el-form-item label="当前密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changingPassword" @click="changePassword">确认修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import axios from '@/api/axios'
import { useUserStore } from '@/stores/userStore'

const userStore = useUserStore()
const userInfo = ref({})
const editForm = ref({})
const editMode = ref(false)
const saving = ref(false)
const changingPassword = ref(false)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const formatDate = value => value ? new Date(value).toLocaleString('zh-CN') : '暂无记录'

const loadProfile = async () => {
  try {
    const response = await axios.get('/auth/me')
    userInfo.value = {
      emailNotification: true,
      wsNotification: true,
      ...response.data.data
    }
    editForm.value = { ...userInfo.value }
  } catch (error) {
    ElMessage.error('获取个人信息失败，请重新登录')
  }
}

const toggleEdit = () => {
  editMode.value = !editMode.value
  editForm.value = { ...userInfo.value }
}

const saveProfile = async () => {
  saving.value = true
  try {
    const response = await axios.put(`/users/${userInfo.value.userId}`, editForm.value)
    userInfo.value = response.data.data
    editForm.value = { ...userInfo.value }
    editMode.value = false
    ElMessage.success('个人资料和通知设置已保存')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const changePassword = async () => {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    ElMessage.warning('请填写完整密码信息')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码不能少于 6 位')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPassword.value = true
  try {
    await axios.post('/users/change-password', {
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    ElMessage.success('密码修改成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '密码修改失败')
  } finally {
    changingPassword.value = false
  }
}

onMounted(loadProfile)
</script>

<style lang="scss" scoped>
.profile-page { max-width: 1180px; margin: 0 auto; }
.identity {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
  h2 { margin: 14px 0 8px; }
  p { color: #909399; margin: 12px 0 0; }
}
.card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 700; }
.hint { margin-left: 10px; color: #909399; font-size: 13px; }
.password-card { margin-top: 20px; }
.password-form { max-width: 520px; }
@media (max-width: 768px) {
  .hint { display: block; width: 100%; margin: 6px 0 0; }
}
</style>
