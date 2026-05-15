# Realtime Bus Web
## 结构

- `index.html`: 实时公交主页面
- `js/`, `styles/`, `imgs/`, `assets/`: 运行所需静态资源
- `server.py`: 本地 HTTP 服务和 `ibuscloud` 代理
- `start.sh`: 启动脚本
- `docs/`: 提取说明和接口清单

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
