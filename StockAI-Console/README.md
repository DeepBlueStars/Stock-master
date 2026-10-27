# StockAI-web

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd) 
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Compile and Minify for Production

```sh
npm run build
```

---

## 打包、传输与 Docker 部署

### 打包（Windows）

环境要求：Node.js 20.19+（或 22.12+）。

```powershell
cd d:\WorkSpace\MainProject\StockAI-master\StockAI-Console
npm install          # 首次或依赖变更时执行
.\deploy.bat
# 产物：dist.tar.gz（内容为 dist 目录下的 index.html、assets/ 等）
```

`deploy.bat` 会依次执行：清理 `dist` → `npm run build` → `tar -czvf dist.tar.gz -C dist .`。

> **运行时配置**：前端启动时先读取 `/env.js`，再回退到构建时的 `VITE_API_BASE_URL`。使用仓库根目录的 `deploy-stockai.sh` 部署时，脚本会按优先级生成 `/env.js`：① 与 `dist.tar.gz` 同级的服务器 `.env`；② tar 包内的 `.env`；③ 都没有时使用构建时配置。换服务器时通常只需修改服务器上的 `.env` 并重新执行部署脚本，无需重新打包。

### 需要传输的文件

| 上传 | 不上传 |
|---|---|
| `dist.tar.gz` | `node_modules/`、`dist/`、`src/`、`package*.json`（目标机不需要重新编译） |
| 可选：与 `dist.tar.gz` 同级的 `.env`（服务器端配置，优先级最高） | |

### 部署为 Docker 服务（Linux）

```bash
bash deploy-stockai.sh
```

说明：

- `proxy_pass` 末尾的 `/` 会去掉 `/srv` 前缀；后端 CORS 已允许所有来源，若前端直接用绝对地址访问后端（`VITE_API_BASE_URL=http://<backend-ip>:8888`），可以删除 `/srv/` 反代配置。
- 构建产物引用的是 `/assets/...` 绝对路径，必须部署在站点根路径下。

### 验证

```bash
docker ps | grep stockai-console
docker logs -f stockai-console
curl -I http://localhost:8889/
```

浏览器访问 `http://<服务器IP>:8889/`。
