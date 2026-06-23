<template>
    <div class="forgot-container">
        <div class="forgot-box">
            <div class="forgot-logo">🔐</div>
            <h2>找回密码</h2>
            <p class="subtitle">我们将发送验证码到您的注册邮箱</p>

            <el-form :model="form" @submit.prevent="handleSubmit" label-width="0">
                <!-- 步骤1：输入邮箱 -->
                <el-form-item v-if="step === 1">
                    <el-input
                            v-model="form.email"
                            placeholder="请输入注册时的邮箱"
                            size="large"
                            prefix-icon="Message"
                            :disabled="sendingCode"
                    />
                </el-form-item>
                <el-form-item v-if="step === 1">
                    <el-button
                            type="primary"
                            @click="sendCode"
                            :loading="sendingCode"
                            style="width: 100%"
                            size="large"
                    >
                        {{ sendingCode ? '发送中...' : '发送验证码' }}
                    </el-button>
                </el-form-item>

                <!-- 步骤2：输入验证码 -->
                <el-form-item v-if="step === 2">
                    <el-input
                            v-model="form.code"
                            placeholder="请输入6位验证码"
                            size="large"
                            prefix-icon="Lock"
                            maxlength="6"
                    />
                </el-form-item>
                <el-form-item v-if="step === 2">
                    <el-button
                            type="primary"
                            @click="verifyCode"
                            :loading="verifying"
                            style="width: 100%"
                            size="large"
                    >
                        验证并继续
                    </el-button>
                </el-form-item>

                <!-- 步骤3：重置密码 -->
                <el-form-item v-if="step === 3">
                    <el-input
                            v-model="form.newPassword"
                            type="password"
                            placeholder="请输入新密码（至少6位）"
                            size="large"
                            prefix-icon="Lock"
                            show-password
                    />
                </el-form-item>
                <el-form-item v-if="step === 3">
                    <el-input
                            v-model="form.confirmPassword"
                            type="password"
                            placeholder="请再次输入新密码"
                            size="large"
                            prefix-icon="Lock"
                            show-password
                    />
                </el-form-item>
                <el-form-item v-if="step === 3">
                    <el-button
                            type="primary"
                            @click="resetPassword"
                            :loading="resetting"
                            style="width: 100%"
                            size="large"
                    >
                        重置密码
                    </el-button>
                </el-form-item>
            </el-form>

            <div class="back-link">
                <router-link to="/login">返回登录页</router-link>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import axios from '@/api/axios'
import { ElMessage } from 'element-plus'

const router = useRouter()

// 当前步骤：1=输入邮箱，2=输入验证码，3=重置密码
const step = ref(1)

const sendingCode = ref(false)
const verifying = ref(false)
const resetting = ref(false)

const form = reactive({
    email: '',
    code: '',
    newPassword: '',
    confirmPassword: ''
})

const handleSubmit = () => {
    if (step.value === 1) {
        sendCode()
        return
    }
    if (step.value === 2) {
        verifyCode()
        return
    }
    resetPassword()
}

// 1. 发送验证码
const sendCode = async () => {
    if (!form.email) {
        ElMessage.error('请输入邮箱')
        return
    }
    sendingCode.value = true
    try {
        const res = await axios.post('/auth/forgot-password/send-code', {
            email: form.email
        })
        ElMessage.success('验证码已发送，请查收邮件')
        step.value = 2
    } catch (err) {
        ElMessage.error(err.response?.data?.message || '发送失败，请重试')
    } finally {
        sendingCode.value = false
    }
}

// 2. 验证验证码
const verifyCode = async () => {
    if (!form.code) {
        ElMessage.error('请输入验证码')
        return
    }
    verifying.value = true
    try {
        const res = await axios.post('/auth/forgot-password/verify-code', {
            email: form.email,
            code: form.code
        })
        ElMessage.success('验证成功，请设置新密码')
        step.value = 3
    } catch (err) {
        ElMessage.error(err.response?.data?.message || '验证码错误或已过期')
    } finally {
        verifying.value = false
    }
}

// 3. 重置密码
const resetPassword = async () => {
    if (!form.newPassword || !form.confirmPassword) {
        ElMessage.error('请填写完整密码信息')
        return
    }
    if (form.newPassword !== form.confirmPassword) {
        ElMessage.error('两次输入的密码不一致')
        return
    }
    if (form.newPassword.length < 6) {
        ElMessage.error('密码长度不能少于6位')
        return
    }

    resetting.value = true
    try {
        const res = await axios.post('/auth/forgot-password/reset', {
            email: form.email,
            code: form.code,
            newPassword: form.newPassword
        })
        ElMessage.success('密码重置成功，请重新登录')
        router.push('/login')
    } catch (err) {
        ElMessage.error(err.response?.data?.message || '重置失败，请重试')
    } finally {
        resetting.value = false
    }
}
</script>

<style lang="scss" scoped>
.forgot-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 200px);
  padding: 20px;

  .forgot-box {
    width: 100%;
    max-width: 420px;
    background: white;
    border-radius: 12px;
    padding: 40px 36px;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
    text-align: center;

    .forgot-logo {
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
  }

  .back-link {
    text-align: center;
    margin-top: 20px;

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
</style>