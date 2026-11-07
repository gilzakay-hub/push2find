# Push2find – Wristband BLE Protocol (proposal v0.1)

This is the protocol the Push2find apps (Android + iOS) implement today.
It is a **proposal for the hardware team** — every UUID and value lives in one
place in each app, so changes are cheap:

- Android: `android/app/src/main/java/com/push2find/app/ble/P2FProtocol.kt`
- iOS: `ios/Push2find/BLE/P2FProtocol.swift`

## 1. Roles

| Role | Device |
|---|---|
| Peripheral / GATT server | Wristband |
| Central / GATT client | Phone |

One wristband ↔ one phone. The phone keeps a persistent connection whenever
the band is in range.

## 2. Advertising

| Field | Value |
|---|---|
| Local name | `P2F-XXXX` (prefix `P2F` is required, `XXXX` = last 2 bytes of MAC, hex) |
| Service UUID in advertising packet | `7A1E0001-5C3D-4B8E-9F2A-1D6C0B7E9A10` (**required** – iOS can only reconnect in the background by filtering on a service UUID) |
| Connectable | Yes |
| Interval while disconnected | 100 ms for the first 30 s after disconnect / power-on / button press, then 1000 ms |
| Pairing mode | Long-press 3 s → fast advertising (100 ms) for 60 s + LED blink |

## 3. GATT table

### 3.1 Push2find Service — `7A1E0001-5C3D-4B8E-9F2A-1D6C0B7E9A10`

| Characteristic | UUID | Properties | Format |
|---|---|---|---|
| Button Event | `7A1E0002-5C3D-4B8E-9F2A-1D6C0B7E9A10` | Notify (+ CCCD) | 2 bytes: `[event, seq]` |
| Control | `7A1E0003-5C3D-4B8E-9F2A-1D6C0B7E9A10` | Write / Write Without Response | 1+ bytes: `[command, ...]` |

**Button Event payload**

| Byte | Meaning |
|---|---|
| 0 | Event: `0x01` single press, `0x02` double press, `0x03` long press (`0x00` reserved, ignored) |
| 1 | Sequence counter, `0x00–0xFF`, increments on every press, wraps around. Lets the phone drop duplicates (e.g. a press re-sent after a reconnect). Optional — a 1-byte payload is accepted. |

Phone behaviour (v1): **any** event while the phone is silent → start ringing;
**any** event while the phone is ringing → stop ringing.

**Control commands (phone → band)**

| Command | Meaning |
|---|---|
| `0x01` | Identify – blink LED / vibrate for ~2 s (used in pairing and the "Identify" button) |
| `0x02`–`0xFF` | Reserved |

### 3.2 Battery Service (standard, SIG) — `0x180F`

| Characteristic | UUID | Properties | Format |
|---|---|---|---|
| Battery Level | `0x2A19` | Read + Notify | uint8, 0–100 % |

Notify on change of ≥ 5 % (or once per hour) to save power.

### 3.3 Device Information Service (standard, recommended) — `0x180A`

Model Number `0x2A24`, Firmware Revision `0x2A26`, Manufacturer `0x2A29`.

## 4. Connection parameters (recommendations)

| Parameter | Value | Why |
|---|---|---|
| Connection interval | 30–50 ms requested; accept what the phone grants | button notification reaches the phone in < 100 ms |
| Peripheral latency | 4–8 | band can skip events when idle → lower power |
| Supervision timeout | 4–6 s | |

Expected end-to-end latency (press → sound) when connected: **< 0.5 s**.

## 5. Press while disconnected

If the button is pressed while there is no connection, the band should:
1. switch to fast advertising (100 ms) for 30 s;
2. keep the press in a buffer;
3. on reconnect + CCCD enabled, send the buffered event **only if it is
   younger than 10 s** (with the same `seq`).

## 6. Security / pairing

- v1: no bonding required ("Just Works" is allowed but not needed). The app
  identifies the band by its address (Android) / peripheral identifier (iOS).
- If the band requires encryption, use LE Secure Connections, Just Works.
  The OS shows its own pairing dialog the first time.

## 7. Open questions for the hardware team

1. Final UUIDs (keep these or replace with your own 128-bit base).
2. Is there a vibration motor / LED for the Identify command?
3. Battery notify policy and expected battery life targets.
4. Will the band support double / long press, or single press only?
