<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  // 应用级初始化入口暂时留空；后续接入登录或埋点时统一从这里扩展。
})
</script>

<style>
page {
  min-height: 100%;
  background: #f4f5f0;
  color: #1d2922;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
}

view,
text,
button,
input,
textarea {
  box-sizing: border-box;
}

button {
  margin: 0;
  font: inherit;
}

button::after {
  border: none;
}
</style>
