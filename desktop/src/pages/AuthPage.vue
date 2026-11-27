<script setup lang="ts">
import { ref } from 'vue';
import { ElButton, ElInput } from 'element-plus';
const props = defineProps<{ mode: 'login' | 'register'; message: string }>();
const emit = defineEmits<{ switch: []; submit: [phone: string, password: string, confirm: string] }>();
const phone = ref(''), password = ref(''), confirm = ref('');
function submit() { emit('submit', phone.value, password.value, confirm.value); }
</script>
<template>
  <main class="auth"><span class="eyebrow">GOLDPRICE MONITOR</span><h1>{{ props.mode === 'login' ? '登录' : '注册' }}</h1><p>实时关注金价变化</p>
    <form @submit.prevent="submit"><label>手机号<ElInput v-model="phone" type="tel" required autocomplete="tel" placeholder="13800138000" /></label><label>密码<ElInput v-model="password" type="password" required :minlength="8" :autocomplete="props.mode === 'login' ? 'current-password' : 'new-password'" /></label><label v-if="props.mode === 'register'">确认密码<ElInput v-model="confirm" type="password" required :minlength="8" autocomplete="new-password" /></label><ElButton native-type="submit" type="primary">{{ props.mode === 'login' ? '登录' : '注册' }}</ElButton></form>
    <ElButton class="link" @click="emit('switch')">{{ props.mode === 'login' ? '没有账号？注册' : '已有账号？登录' }}</ElButton><div class="message" role="status">{{ props.message }}</div>
  </main>
</template>
