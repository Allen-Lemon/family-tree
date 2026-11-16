import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './assets/main.css'
import App from './App.vue'
import router from './router'
import { installPermissionDirective } from './utils/permission'

const app = createApp(App)
app.use(createPinia()).use(router).use(ElementPlus, { locale: zhCn })
installPermissionDirective(app)
app.mount('#app')
