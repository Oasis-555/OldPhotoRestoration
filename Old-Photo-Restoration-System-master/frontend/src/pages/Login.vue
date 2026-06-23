<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-logo">📷</div>
      <h2>老照片修复系统</h2>
      <p class="subtitle">登录您的账户</p>
      <el-form :model="form" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input
            v-model="form.username"
            placeholder="请输入用户名或邮箱"
            size="large"
            prefix-icon="User"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            @click="handleLogin"
            :loading="loading"
            style="width: 100%"
            size="large"
          >
            登 录
          </el-button>
        </el-form-item>

        <div class="links">
          <div class="link-row">
            <router-link to="/forgot-password">忘记密码？</router-link>
            <span class="divider">|</span>
            <router-link to="/register">没有账号？立即注册</router-link>
          </div>
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
  username: '',
  password: ''
})

const loading = ref(false)

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    ElMessage.error('请填写用户名和密码')
    return
  }

  loading.value = true
  try {
    await userStore.login(form.value)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (error) {
    ElMessage.error(error.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 200px);
  padding: 20px;

  .login-box {
    width: 100%;
    max-width: 420px;
    background: white;
    border-radius: 12px;
    padding: 40px 36px;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
    text-align: center;

    .login-logo {
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
    }

    .links {
      text-align: center;
      margin-top: 16px;

      .link-row {
        display: flex;
        justify-content: center;
        gap: 8px;
      }

      .divider {
        color: #ccc;
      }
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
