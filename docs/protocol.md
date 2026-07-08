# 通信协议草案

控制消息使用 UTF-8 JSON，每条消息前面加 4 字节大端长度。

## 握手

接收端发给手机端：

```json
{
  "type": "hello",
  "client": "receiver-app",
  "protocol": 1,
  "width": 1280,
  "height": 720,
  "densityDpi": 240
}
```

手机端返回：

```json
{
  "type": "ready",
  "protocol": 1,
  "mode": "mirror",
  "videoCodec": "h264"
}
```

## 触摸事件

```json
{
  "type": "touch",
  "action": "down",
  "pointerId": 0,
  "x": 642,
  "y": 318,
  "displayWidth": 1280,
  "displayHeight": 720,
  "timeMillis": 123456789
}
```

动作类型：

- `down`
- `move`
- `up`
- `cancel`

## 按键事件

```json
{
  "type": "key",
  "key": "back",
  "action": "down"
}
```

按键类型：

- `back`
- `home`
- `menu`
- `app_map`
- `app_music`
- `app_custom`

## 切换模式

```json
{
  "type": "set_mode",
  "mode": "extended",
  "bitrateMbps": 4,
  "fps": 25,
  "densityDpi": 240,
  "navigationBar": true
}
```

