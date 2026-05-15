# Realtime Bus Web

从 `ctf.apk` 和线上 H5 提取整理出的“实时公交”本地可运行网页。

## About

这是一个把“实时公交”网页和 Android 壳一起整理后的发布仓库。

- Web 版可直接通过本地服务启动
- Android 版通过 WebView 加本地代理运行
- Release 中提供可直接安装的 APK

## 结构

- `index.html`: 实时公交主页面
- `js/`, `styles/`, `imgs/`, `assets/`: 运行所需静态资源
- `server.py`: 本地 HTTP 服务和 `ibuscloud` 代理
- `start.sh`: 启动脚本
- `docs/`: 提取说明和接口清单

## 安装包

- 最新 APK: 在 GitHub Releases 中下载 `app-release.apk`
- 当前 Android 壳源码位于 `android/`

## 运行

```bash
cd /opt/realtime-bus-web
bash start.sh
```

浏览器打开：

```text
http://127.0.0.1:8000/
```

## 特性

- 直接进入实时公交主页面，不再经过入口页
- 自动补齐阳泉实时公交所需查询参数
- 通过本地代理转发真实 `ibuscloud` 接口，绕过浏览器跨域限制
- 支持系统定位和地图选点切换位置

## 说明

- 真实公交数据仍来自线上接口，使用时需要联网
- 首次打开如果浏览器请求定位权限，允许即可
- Android 安装包是发布产物，不会直接提交到 git 仓库
