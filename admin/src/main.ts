import { createApp } from 'vue'
import { ElAlert, ElEmpty } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/message/style/css'
import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)
app.component(ElAlert.name!, ElAlert)
app.component(ElEmpty.name!, ElEmpty)
app.use(router).mount('#app')
