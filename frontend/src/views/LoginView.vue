<template>
  <!-- 登录页根据北京时间切换晨间、午后和夜间背景场景。 -->
  <main class="login-page" :class="`login-scene--${timeScene}`">
    <div class="login-background" :style="{ '--login-scene-image': `url(${sceneBackground})` }" aria-hidden="true">
      <div class="login-atmosphere"></div>
      <canvas v-if="timeScene === 'morning'" ref="waveCanvas" class="login-wave-canvas"></canvas>
      <div class="login-stars" v-if="timeScene === 'night'">
        <i v-for="star in movingStars" :key="star.id" :style="star.style"></i>
      </div>
    </div>
    <RouterLink class="login-logo" to="/">
      <BrandMark />
    </RouterLink>

    <!-- 登录与注册共用账户卡片；两种模式都只使用账号，不收集邮箱。 -->
    <section class="login-card">
      <h1>{{ isRegister ? 'Create your account' : 'Sign in to your account' }}</h1>

      <button class="google-button" type="button" disabled>
        {{ isRegister ? 'Create your Account' : 'Sign in your Account' }}
      </button>

      <form @submit.prevent="submit">
        <label v-if="isRegister">
          Display name
          <input v-model.trim="displayName" autocomplete="name" placeholder="客户姓名" />
          <small>公开注册将创建客户账号；客服账号只能由系统管理员添加。</small>
        </label>

        <label>
          Account
          <input v-model.trim="username" autocomplete="username" placeholder="Enter account" />
          <small v-if="isRegister">账号支持字母、数字、下划线和短横线。</small>
        </label>

        <label>
          Your password
          <div class="password-field">
            <input v-model="password" :type="showPassword ? 'text' : 'password'" :autocomplete="isRegister ? 'new-password' : 'current-password'" placeholder="Enter password" />
            <button type="button" @click="showPassword = !showPassword">{{ showPassword ? 'Hide' : 'Show' }}</button>
          </div>
        </label>

        <label v-if="isRegister">
          Confirm password
          <input v-model="confirmPassword" :type="showPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="Enter password again" />
          <small>密码为 8-72 位，至少包含一个字母和一个数字，两次输入必须一致。</small>
        </label>

        <div v-if="!isRegister" class="form-options">
          <label class="check-line"><input type="checkbox" /> Keep me signed in</label>
          <a href="#">Forgot your password?</a>
        </div>

        <p v-if="errorMessage" role="alert">{{ errorMessage }}</p>
        <button class="continue-button" :disabled="!canContinue || submitting">
          {{ submitting ? (isRegister ? 'Creating…' : 'Signing in…') : (isRegister ? 'Create account' : 'Continue') }}
        </button>
      </form>
      <button class="auth-mode-switch" type="button" :disabled="submitting" @click="switchMode">
        {{ isRegister ? 'Already have an account? Sign in' : 'New to SupportOps? Create an account' }}
      </button>
    </section>

  </main>
</template>

<script setup>
// 登录页调用真实鉴权接口，JWT 由请求层保存到 sessionStorage。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import { login, register } from '../api/auth.js'
import morningSea from '../assets/login-backgrounds/morning-sea.png'
import afternoonSunset from '../assets/login-backgrounds/afternoon-sunset.png'
import nightSagittarius from '../assets/login-backgrounds/night-sagittarius.png'

const router = useRouter()
const route = useRoute()
const username = ref('')
const displayName = ref('')
const password = ref('')
const confirmPassword = ref('')
const isRegister = ref(false)
const showPassword = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const timeScene = ref(getBeijingScene())
const waveCanvas = ref(null)
// 夜景中的星点位置和动画参数，转换为 CSS 自定义属性供样式使用。
const movingStars = [
  [8, 18, 1.5, 8.4, 0], [17, 35, 2.2, 10.8, -2], [26, 12, 1.2, 7.2, -4],
  [37, 28, 2.4, 12.4, -1], [46, 9, 1.5, 9.6, -5], [55, 42, 1.8, 11.2, -3],
  [66, 17, 2.5, 8.8, -6], [75, 34, 1.4, 10.1, -2], [83, 12, 2.1, 12.7, -4],
  [91, 29, 1.5, 9.3, -7], [12, 67, 2, 11.7, -2], [31, 78, 1.4, 8.1, -5],
  [48, 70, 2.3, 13.1, -3], [61, 84, 1.4, 9.7, -6], [79, 73, 2.1, 10.5, -1],
  [94, 61, 1.6, 12.2, -4]
].map(([left, top, size, duration, delay], index) => ({
  id: index,
  style: { left: `${left}%`, top: `${top}%`, '--star-size': `${size}px`, '--star-duration': `${duration}s`, '--star-delay': `${delay}s` }
}))

// 仅做最小必填校验，账号合法性由后端统一判断。
const canContinue = computed(() => {
  if (!isRegister.value) return username.value.length > 0 && password.value.length >= 4
  return /^[A-Za-z0-9_-]{3,32}$/.test(username.value)
    && displayName.value.length >= 2
    && /^(?=.*[A-Za-z])(?=.*\d)[\x20-\x7E]{8,72}$/.test(password.value)
    && password.value === confirmPassword.value
})
const sceneBackground = computed(() => ({ morning: morningSea, afternoon: afternoonSunset, night: nightSagittarius })[timeScene.value])

let sceneTimer
let waveFrame
let waveImage
let waveSource
let waveHorizon = 0

// 根据北京时间判断当前登录背景场景。
function getBeijingScene() {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Shanghai', hour: '2-digit', hourCycle: 'h23' }).format(new Date()))
  if (hour >= 6 && hour < 12) return 'morning'
  if (hour >= 12 && hour < 18) return 'afternoon'
  return 'night'
}

// 每分钟刷新一次场景，确保页面停留跨过时间段时背景能更新。
onMounted(() => {
  sceneTimer = window.setInterval(() => { timeScene.value = getBeijingScene() }, 60_000)
  startMorningWaves()
})

watch(timeScene, async (scene) => {
  stopMorningWaves()
  if (scene === 'morning') {
    await nextTick()
    startMorningWaves()
  }
})

onBeforeUnmount(() => {
  window.clearInterval(sceneTimer)
  stopMorningWaves()
})

// Canvas 直接位移背景图的海面像素，让浪尖、泡沫和水纹本身产生运动。
function startMorningWaves() {
  if (timeScene.value !== 'morning' || !waveCanvas.value) return
  waveImage = new Image()
  waveImage.decoding = 'async'
  waveImage.onload = () => {
    if (timeScene.value !== 'morning' || !waveCanvas.value) return
    resizeWaveCanvas()
    window.addEventListener('resize', resizeWaveCanvas)
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) drawWaveFrame(0, false)
    else waveFrame = window.requestAnimationFrame(drawWaveFrame)
  }
  waveImage.src = morningSea
}

function stopMorningWaves() {
  window.cancelAnimationFrame(waveFrame)
  window.removeEventListener('resize', resizeWaveCanvas)
  waveFrame = undefined
  waveImage = undefined
  waveSource = undefined
}

function resizeWaveCanvas() {
  const canvas = waveCanvas.value
  if (!canvas || !waveImage?.naturalWidth) return
  const ratio = Math.min(window.devicePixelRatio || 1, 1.35)
  const width = Math.max(1, Math.round(canvas.clientWidth * ratio))
  const height = Math.max(1, Math.round(canvas.clientHeight * ratio))
  canvas.width = width
  canvas.height = height

  waveSource = document.createElement('canvas')
  waveSource.width = width
  waveSource.height = height
  const sourceContext = waveSource.getContext('2d')
  const scale = Math.max(width / waveImage.naturalWidth, height / waveImage.naturalHeight)
  const drawnWidth = waveImage.naturalWidth * scale
  const drawnHeight = waveImage.naturalHeight * scale
  const offsetX = (width - drawnWidth) / 2
  const offsetY = (height - drawnHeight) / 2
  sourceContext.drawImage(waveImage, offsetX, offsetY, drawnWidth, drawnHeight)
  waveHorizon = Math.max(0, Math.min(height, offsetY + drawnHeight * .505))
}

function drawWaveFrame(timestamp, scheduleNext = true) {
  const canvas = waveCanvas.value
  if (!canvas || !waveSource) return
  const context = canvas.getContext('2d')
  const width = canvas.width
  const height = canvas.height
  const oceanHeight = Math.max(1, height - waveHorizon)
  const stripHeight = Math.max(4, Math.round(height / 230))
  const edgeBleed = Math.round(width * .018)

  context.clearRect(0, 0, width, height)
  context.drawImage(waveSource, 0, 0)
  context.save()
  context.beginPath()
  context.rect(0, waveHorizon, width, oceanHeight)
  context.clip()

  for (let y = waveHorizon; y < height; y += stripHeight) {
    const depth = (y - waveHorizon) / oceanHeight
    const horizontalShift = (
      Math.sin(timestamp * .00125 + depth * 10.5) * (2 + depth * 13)
      + Math.sin(timestamp * .00058 - depth * 18) * (1 + depth * 7)
    )
    const verticalShift = Math.sin(timestamp * .00105 + depth * 15) * (.5 + depth * 3.4)
    context.drawImage(
      waveSource,
      0, y, width, Math.min(stripHeight + 2, height - y),
      horizontalShift - edgeBleed, y + verticalShift,
      width + edgeBleed * 2, Math.min(stripHeight + 2, height - y) + 1
    )
  }
  context.restore()

  if (scheduleNext) waveFrame = window.requestAnimationFrame(drawWaveFrame)
}

async function submit() {
  if (!canContinue.value || submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    if (isRegister.value) await register(username.value, displayName.value, password.value)
    await login(username.value, password.value)
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    errorMessage.value = `${error.message}${error.requestId ? `（请求 ID：${error.requestId}）` : ''}`
  } finally {
    submitting.value = false
  }
}

/** 登录后只允许回到站内绝对路径，防止 redirect 参数被用于跳转外部站点。 */
function safeRedirect(value) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')
    ? value
    : '/personal-center'
}

function switchMode() {
  isRegister.value = !isRegister.value
  username.value = ''
  displayName.value = ''
  password.value = ''
  confirmPassword.value = ''
  errorMessage.value = ''
}
</script>
