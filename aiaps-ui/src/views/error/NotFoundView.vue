<template>
  <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 60vh;">
    <h1 style="font-size: 72px; color: #e2e8f0; margin: 0;">404</h1>
    <p style="font-size: 16px; color: #64748b;">页面不存在</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>
