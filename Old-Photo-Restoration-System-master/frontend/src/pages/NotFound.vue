<template>
  <div class="not-found-container">
    <div class="not-found-content">
      <div class="error-code">404</div>
      <h1>页面不存在</h1>
      <p>您访问的页面不存在或已被移除。</p>
      <router-link to="/">
        <el-button type="primary" size="large">返回首页</el-button>
      </router-link>
    </div>
  </div>
</template>

<script setup>
</script>

<style lang="scss" scoped>
.not-found-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 200px);

  .not-found-content {
    text-align: center;

    .error-code {
      font-size: 100px;
      font-weight: bold;
      color: #667eea;
      margin-bottom: 20px;
      line-height: 1;
    }

    h1 {
      font-size: 28px;
      margin-bottom: 10px;
      color: #333;
    }

    p {
      color: #666;
      margin-bottom: 30px;
      font-size: 16px;
    }

    a {
      text-decoration: none;
    }
  }
}
</style>
