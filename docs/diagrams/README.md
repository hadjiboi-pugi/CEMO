# CEMO Program Flowcharts

This directory contains the program flowcharts for all screens in the CEMO application.

## Flowcharts

- [Auth Screen](png/auth_flow.png) - User authentication and sign-in flow.
- [BLE Device Screen](png/ble_device_flow.png) - Bluetooth LE device scanning and connection.
- [Dashboard Screen](png/dashboard_flow.png) - Main metrics display and waste entry triggering.
- [Profile Screen](png/profile_flow.png) - User profile viewing and editing.
- [Reports Screen](png/reports_flow.png) - Analytics and historical data visualization.
- [Settings Screen](png/settings_flow.png) - App theme, language, and notification settings.
- [Waste History Screen](png/waste_history_flow.png) - Detailed waste log with filtering and pagination.

## Source Files

The source `.dot` files can be found in the root of this directory. These files use **Times New Roman** as the primary font for consistency with project documentation standards.

### Generation

To regenerate the PNG images, you can use the following command (requires Graphviz):

```bash
dot -Tpng <filename>.dot -o png/<filename>.png
```
