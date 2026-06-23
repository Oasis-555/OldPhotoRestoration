<template>
  <div class="register-container">
    <div class="register-box">
      <div class="register-logo">📷</div>
      <h2>创建新账户</h2>
      <p class="subtitle">加入老照片修复系统</p>
      <el-form :model="form" @submit.prevent="handleRegister">
        <el-form-item>
          <el-input
            v-model="form.userName"
            placeholder="请输入用户名"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>

        <el-form-item>
          <el-input
            v-model="form.email"
            type="email"
            placeholder="请输入邮箱"
            size="large"
            prefix-icon="Message"
          />
        </el-form-item>

        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码（至少6位）"
            size="large"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            size="large"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item label="账户类型">
          <el-radio-group v-model="form.userType" style="width: 100%">
            <el-radio :label="1" border style="margin-right: 16px">个人用户</el-radio>
            <el-radio :label="2" border>机构用户</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            @click="handleRegister"
            :loading="loading"
            style="width: 100%"
            size="large"
          >
            立即注册
          </el-button>
        </el-form-item>

        <div class="links">
          <router-link to="/login">已有账号？立即登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const form = ref({
  userName: '',
  email: '',
  password: '',
  confirmPassword: '',
  userType: 1
})

const loading = ref(false)

const handleRegister = async () => {
  if (!form.value.userName || !form.value.email || !form.value.password) {
    ElMessage.error('请填写所有必填字段')
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)) {
    ElMessage.error('请输入有效的邮箱地址')
    return
  }
  if (form.value.password.length < 6) {
    ElMessage.error('密码长度不能少于6位')
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    await userStore.register(form.value)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error) {
    ElMessage.error(error.message || '注册失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 200px);
  padding: 20px;

  .register-box {
    width: 100%;
    max-width: 440px;
    background: white;
    border-radius: 12px;
    padding: 40px 36px;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
    text-align: center;

    .register-logo {
      font-size: 48px;
      margin-bottom: 10px;
    }

    h2 {
      font-size: 22px;
      font-weight: bold;
      margin: 0 0 6px 0;
      color: #333;
    }

    .subtitle {
      color: #999;
      font-size: 14px;
      margin-bottom: 28px;
    }

    :deep(.el-form-item) {
      margin-bottom: 18px;
      text-align: left;
    }

    .links {
      text-align: center;
      margin-top: 16px;

      a {
        color: #667eea;
        text-decoration: none;
        font-size: 14px;

        &:hover {
          text-decoration: underline;
        }
      }
    }
  }
}
</style>
