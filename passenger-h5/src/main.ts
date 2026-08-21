import { createApp } from 'vue'
import { Button, CellGroup, Field, Form, Stepper } from 'vant'
import 'vant/lib/index.css'
import './styles.css'
import './map.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(router)
app.use(Button)
app.use(CellGroup)
app.use(Field)
app.use(Form)
app.use(Stepper)
app.mount('#app')
