# Citaq H10-3 Internal Printer (ESC/POS over /dev/ttyS1)

Android Studio project that opens the Citaq H10-3 built-in thermal printer serial device (`/dev/ttyS1`) at **115200** baud and prints an ESC/POS **QR code** when the user taps *Print QR*.

- **Target Android**: 5.1.1 (API 22)
- **Paper width**: default **80 mm** (~576 dots @ 203 dpi); configurable in *Settings*.
- **Device path**: default `/dev/ttyS1`; configurable in *Settings*.
- **Baud rate**: default `115200`; configurable in *Settings*.

## Flow
- Initialize printer (ESC @)
- Center
- ESC/POS QR (Model 2, size=6, ECC=H)
- Feed lines and try full cut

## Build
1. Open in **Android Studio** (install **NDK**).
2. Build & run on the device.

> If you get `Permission denied` opening `/dev/ttyS1`, adjust device permissions in firmware; Android runtime permissions do not cover tty devices.
