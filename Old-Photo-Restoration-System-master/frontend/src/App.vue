<template>
  <div id="app">
    <el-container>
      <el-header class="app-header">
        <div class="header-content">
          <router-link to="/" class="app-title-link">
            <h1 class="app-title">
              <span class="icon">📷</span> 老照片修复系统
            </h1>
          </router-link>
          <div class="header-nav" v-if="isAuthenticated">
            <router-link to="/" class="nav-link">首页</router-link>
            <router-link to="/restoration" class="nav-link">照片修复</router-link>
            <router-link to="/album" class="nav-link">我的相册</router-link>
            <router-link to="/history" class="nav-link">历史记录</router-link>
          </div>
          <div class="header-menu" v-if="isAuthenticated">
            <el-dropdown>
              <span class="el-dropdown-link">
                {{ userName }}
                <el-icon class="el-icon--right">
                  <arrow-down />
                </el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="goToProfile">个人中心</el-dropdown-item>
                  <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>

      <el-main>
        <router-view />
      </el-main>

      <el-footer class="app-footer">
        <p>© 2026 老照片修复系统. 保留所有权利.</p>
      </el-footer>
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const isAuthenticated = computed(() => userStore.isAuthenticated)
const userName = computed(() => userStore.userName)

const logout = () => {
  userStore.logout()
  ElMessage.success('已成功退出登录')
  router.push('/login')
}

const goToProfile = () => {
  router.push('/profile')
}

onMounted(async () => {
  if (userStore.isAuthenticated) {
    // token 存在时从服务端验证并刷新用户信息；失败则 loadUserInfo 内部会 logout
    await userStore.loadUserInfo().catch(() => {})
  }
})
</script>

<style lang="scss" scoped>
#app {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.el-container {
  min-height: 100vh;
}

.app-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  color: white;
  padding: 0 20px;
  height: 60px !important;

  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 100%;
  }

  .app-title-link {
    text-decoration: none;
  }

  .app-title {
    font-size: 22px;
    font-weight: bold;
    margin: 0;
    display: flex;
    align-items: center;
    gap: 8px;
    color: white;

    .icon {
      font-size: 26px;
    }
  }

  .header-nav {
    display: flex;
    gap: 20px;
    flex: 1;
    margin-left: 30px;

    .nav-link {
      color: rgba(255, 255, 255, 0.85);
      text-decoration: none;
      font-size: 15px;
      padding: 4px 0;
      border-bottom: 2px solid transparent;
      transition: all 0.2s;

      &:hover,
      &.router-link-active {
        color: white;
        border-bottom-color: white;
      }
    }
  }

  .header-menu {
    .el-dropdown-link {
      cursor: pointer;
      color: white;
      display: flex;
      align-items: center;
      gap: 5px;

      &:hover {
        opacity: 0.8;
      }
    }
  }
}

.el-main {
  background: #f5f7fa;
  padding: 20px;
}

.app-footer {
  background: rgba(0, 0, 0, 0.1);
  color: white;
  text-align: center;
  padding: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);

  p {
    margin: 0;
    font-size: 14px;
  }
}
</style>
