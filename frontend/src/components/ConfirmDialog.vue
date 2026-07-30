<template>
  <Teleport to="body">
    <Transition name="confirm-fade">
      <div v-if="open" class="confirm-backdrop" @click.self="cancel">
        <section
          class="confirm-dialog"
          role="alertdialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          :aria-describedby="messageId"
        >
          <header>
            <span aria-hidden="true">!</span>
            <div>
              <small>{{ eyebrow }}</small>
              <h2 :id="titleId">{{ title }}</h2>
            </div>
          </header>
          <p :id="messageId">{{ message }}</p>
          <footer>
            <button type="button" :disabled="busy" @click="cancel">取消</button>
            <button
              ref="confirmButton"
              type="button"
              :class="{ danger }"
              :disabled="busy"
              @click="$emit('confirm')"
            >
              {{ busy ? '处理中…' : confirmLabel }}
            </button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  eyebrow: { type: String, default: '请确认操作' },
  title: { type: String, required: true },
  message: { type: String, required: true },
  confirmLabel: { type: String, default: '确认' },
  busy: { type: Boolean, default: false },
  danger: { type: Boolean, default: false }
})

const emit = defineEmits(['confirm', 'cancel'])
const confirmButton = ref(null)
const titleId = `confirm-title-${crypto.randomUUID()}`
const messageId = `confirm-message-${crypto.randomUUID()}`

watch(() => props.open, async (open) => {
  if (!open) return
  await nextTick()
  confirmButton.value?.focus()
})

function cancel() {
  if (!props.busy) emit('cancel')
}
</script>

<style scoped>
.confirm-backdrop {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(32, 40, 58, .42);
  backdrop-filter: blur(6px);
}

.confirm-dialog {
  width: min(430px, 100%);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, .9);
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 30px 90px rgba(41, 50, 72, .3);
}

.confirm-dialog header {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 22px 24px 15px;
}

.confirm-dialog header > span {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #fff0f1;
  color: #c94f5a;
  font-size: 20px;
  font-weight: 900;
}

.confirm-dialog small {
  color: #7b84d8;
  font-size: 11px;
  font-weight: 800;
}

.confirm-dialog h2 {
  margin: 4px 0 0;
  color: #222b40;
  font-size: 20px;
}

.confirm-dialog > p {
  margin: 0;
  padding: 0 24px 22px;
  color: #707b8f;
  font-size: 14px;
  line-height: 1.7;
}

.confirm-dialog footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 15px 24px;
  border-top: 1px solid #e8ebee;
  background: #fafbfb;
}

.confirm-dialog button {
  min-width: 82px;
  padding: 9px 14px;
  border: 1px solid #dfe4e8;
  border-radius: 9px;
  background: #fff;
  color: #596579;
  cursor: pointer;
}

.confirm-dialog button.danger {
  border-color: #c94f5a;
  background: #c94f5a;
  color: #fff;
}

.confirm-dialog button:disabled {
  cursor: wait;
  opacity: .6;
}

.confirm-fade-enter-active,
.confirm-fade-leave-active {
  transition: opacity .18s ease;
}

.confirm-fade-enter-from,
.confirm-fade-leave-to {
  opacity: 0;
}
</style>
