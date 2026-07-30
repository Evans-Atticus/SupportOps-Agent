<template>
  <!-- 可复用的 SOA / Li 等距立方体品牌标识。 -->
  <div class="brand-mark brand-mark--diagnostic" :class="{ light }" aria-label="SOA brand mark">
    <span class="brand-mark__core">
      <span class="brand-mark__cube" aria-hidden="true">
        <!-- 顶面使用 SVG 绘制节点与连线，避免图片资源依赖。 -->
        <span class="brand-mark__cube-face brand-mark__cube-face--top" aria-hidden="true">
          <svg viewBox="0 0 60 66" role="presentation">
            <polyline points="19,31 28,18 38,27 34,35 25,34 19,31 15,36" />
            <polyline points="38,27 45,31 42,37" />
            <circle cx="19" cy="31" r="1.45" />
            <circle cx="28" cy="18" r="1.35" />
            <circle cx="38" cy="27" r="1.55" />
            <circle cx="34" cy="35" r="1.25" />
            <circle cx="25" cy="34" r="1.2" />
            <circle cx="15" cy="36" r="1.05" />
            <circle cx="45" cy="31" r="1.15" />
            <circle cx="42" cy="37" r="1" />
          </svg>
        </span>
        <b class="brand-mark__cube-face brand-mark__cube-face--li"><span>Li</span></b>
        <b class="brand-mark__cube-face brand-mark__cube-face--front">
          <strong>SOA</strong>
          <small>SUPPORT OPS</small>
        </b>
      </span>
    </span>
  </div>
</template>

<script setup>
// light 用于切换品牌标识在浅色背景上的显示样式。
defineProps({
  light: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
/*
 * The lettering belongs to the mark itself. These rules intentionally form a
 * hard component boundary so page-level readability rules cannot resize the
 * artwork and push its lettering outside a cube face.
 */
.brand-mark__cube-face {
  overflow: hidden;
}

.brand-mark__cube-face--front strong {
  display: block;
  margin: 0;
  color: #fff;
  font-size: 12px !important;
  font-weight: 800;
  letter-spacing: -.14em;
  line-height: 1;
  text-shadow: none;
  white-space: nowrap;
}

.brand-mark__cube-face--front small {
  display: block;
  margin: 2px 0 0;
  color: rgba(255, 255, 255, .78);
  font-size: 3.4px !important;
  font-weight: 700;
  letter-spacing: .08em;
  line-height: 1;
  white-space: nowrap;
}

.brand-mark__cube-face--li span {
  font-size: 18px !important;
  line-height: 1;
}
</style>
