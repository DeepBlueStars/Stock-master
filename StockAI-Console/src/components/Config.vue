<template>
  <div class="config-container">
    <el-card class="config-card">
      <template #header>
        <div class="flex justify-between items-center">
          <h2 class="text-xl font-bold">个人配置</h2>
        </div>
      </template>
      
      <el-form :model="userInfo" label-width="80px" class="mt-4">
        <el-form-item label="用户名">
          <el-input v-model="userInfo.username" disabled />
        </el-form-item>
        
        <el-form-item label="邮箱">
          <el-input v-model="userInfo.email" disabled />
        </el-form-item>
        
        <el-form-item label="现金" prop="cash">
          <el-input-number 
            v-model="userInfo.cash" 
            :min="0" 
            :precision="2" 
            :step="0.01" 
            class="w-full"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="updateCash" :loading="updatingCash">
            保存修改
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { ElMessage } from 'element-plus';

// 导入配置文件
import config from '../config/config';

// 使用配置文件中的API基础地址
const BASE_URL = config.baseUrl;

// 用户信息
const userInfo = ref({
  username: '',
  email: '',
  cash: 0
});

// 加载状态
const updatingCash = ref(false);

// 固定用户ID
const userId = '6928fca14f206ada1b7a9269';

// 获取用户信息
async function fetchUserInfo() {
  try {
    const response = await axios.post(`${BASE_URL}/config/userInfo`, {
      userId: userId
    });
    if (response.status === 200) {
      const res = response.data;
      if (res.state === 0) {
        userInfo.value = {
          username: res.data.username || '',
          email: res.data.email || '',
          cash: res.data.cash || 0
        };
      } else {
        ElMessage.error('获取用户信息失败:' + (res.message || '未知错误'));
      }
    } else {
      ElMessage.error('获取用户信息失败，请稍后重试');
    }
  } catch (error) {
    ElMessage.error('获取用户信息失败，请稍后重试');
    console.error('获取用户信息失败:', error.message);
  }
}

// 更新现金
async function updateCash() {
  try {
    updatingCash.value = true;
    const response = await axios.post(`${BASE_URL}/config/updateCash`, {
      userId: userId,
      cash: userInfo.value.cash
    });
    if (response.status === 200) {
      const res = response.data;
      if (res.state === 0) {
        ElMessage.success('现金更新成功');
      } else {
        ElMessage.error('现金更新失败:' + (res.message || '未知错误'));
      }
    } else {
      ElMessage.error('现金更新失败，请稍后重试');
    }
  } catch (error) {
    ElMessage.error('现金更新失败，请稍后重试');
    console.error('现金更新失败:', error.message);
  } finally {
    updatingCash.value = false;
  }
}

// 页面加载时获取用户信息
onMounted(() => {
  fetchUserInfo();
});
</script>

<style scoped>
.config-container {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.config-card {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
</style>