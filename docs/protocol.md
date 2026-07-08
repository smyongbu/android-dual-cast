# Wire Protocol Draft

All control packets are UTF-8 JSON messages with a 4-byte big-endian length
prefix.

## Hello

Receiver to phone:

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

Phone to receiver:

```json
{
  "type": "ready",
  "protocol": 1,
  "mode": "mirror",
  "videoCodec": "h264"
}
```

## Touch

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

Actions:

- `down`
- `move`
- `up`
- `cancel`

## Key

```json
{
  "type": "key",
  "key": "back",
  "action": "down"
}
```

Keys:

- `back`
- `home`
- `menu`
- `app_map`
- `app_music`
- `app_custom`

## Mode Change

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

