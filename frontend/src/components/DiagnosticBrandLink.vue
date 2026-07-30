<template>
  <RouterLink class="diagnostic-brand-link" :to="to" :aria-label="`${title}首页`">
    <BrandMark class="diagnostic-brand-link__mark" />
    <span class="diagnostic-brand-link__copy">
      <strong>{{ title }}</strong>
      <small>{{ subtitle }}</small>
    </span>
  </RouterLink>
</template>

<script setup>
import BrandMark from './BrandMark.vue'

defineProps({
  to: {
    type: [String, Object],
    default: '/'
  },
  title: {
    type: String,
    default: '工单诊断智能体'
  },
  subtitle: {
    type: String,
    default: 'Ticket Diagnostic Agent'
  }
})
</script>

<style scoped>
.diagnostic-brand-link {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 12px;
  min-width: 242px;
  color: inherit;
  text-decoration: none;
}

.diagnostic-brand-link__mark {
  flex: 0 0 62px;
  width: 62px;
  height: 58px;
  overflow: visible;
  transform: none;
}

.diagnostic-brand-link__copy,
.diagnostic-brand-link__copy strong,
.diagnostic-brand-link__copy small {
  display: block;
}

.diagnostic-brand-link__copy strong {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -.05em;
  line-height: 1.2;
}

.diagnostic-brand-link__copy small {
  margin-top: 3px;
  color: #68738b;
  font-size: 11px;
  line-height: 1.2;
}
</style>
