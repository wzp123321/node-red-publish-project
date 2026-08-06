# Node-RED Dashboard v1 to v2 Converter

A web-based tool for converting Node-RED Dashboard v1 UI components to Dashboard v2 format. This project uses the official [node-red-dashboard-2-migration](https://github.com/FlowFuse/node-red-dashboard-2-migration) library to handle the conversion.

## Features

- 🎨 Modern web interface
- 📋 Copy-paste conversion
- ✨ Real-time JSON validation
- 📤 One-click copy to clipboard
- 📱 Responsive design

## Supported Nodes

The converter supports the following Node-RED Dashboard v1 to v2 migrations:

### UI Nodes
- `ui_text` → `ui-text`
- `ui_form` → `ui-form`
- `ui_button` → `ui-button`
- `ui_dropdown` → `ui-dropdown`
- `ui_switch` → `ui-switch` (some properties not supported)
- `ui_slider` → `ui-slider`
- `ui_text_input` → `ui-text-input` (some properties not supported)
- `ui_numeric` → `ui-numeric-input`

### Config Nodes
- `ui_tab` → `ui-page`
- `ui_group` → `ui-group`

### Auto-added Nodes
- `ui-base` - Created automatically (not in Dashboard v1 exports)
- `ui-theme` - Created automatically (not in Dashboard v1 exports)

## Installation

1. Clone or download this repository
2. Install dependencies:

```bash
npm install
```

## Usage

### Quick Start (Recommended)

**On macOS/Linux:**
```bash
./run.sh
```

**On Windows:**
```cmd
run.bat
```

Or double-click `run.bat` in Windows Explorer.

This will automatically:
- Check and install dependencies if needed
- Start the server on port 3000 (or the port specified in `PORT` environment variable)
- Display the URL to access the web interface

### Alternative: Manual Start

1. Install dependencies (if not already installed):

```bash
npm install
```

2. Start the server:

```bash
npm start
```

Or with a custom port:

**On macOS/Linux:**
```bash
PORT=8080 npm start
```

**On Windows:**
```cmd
set PORT=8080 && npm start
```

3. Open your browser and navigate to:

```
http://localhost:3000
```

3. Paste your Dashboard v1 flow JSON in the input field
4. Click "Convert to v2" button
5. Copy the converted JSON from the output field
6. Import the converted JSON into your Node-RED instance

## API Endpoint

You can also use the conversion API directly:

```bash
POST /api/convert
Content-Type: application/json

{
  "nodes": [...],
  "flows": [...]
}
```

Response:
```json
{
  "success": true,
  "data": {
    "nodes": [...],
    "flows": [...]
  }
}
```

## Limitations

Some nodes and properties are not yet supported (官方迁移库限制；`ui_chart`、`ui_date_picker` 已由本项目扩展支持，见下方说明):
- `ui_colour_picker`
- `ui_gauge`
- `ui_audio`
- `ui_toast`
- `ui_control`
- `ui_template`

Additionally, some properties may not be fully migrated:
- `ui_switch`: `.tooltip`, `.decouple`, `.animate` are not supported
- `ui_text_input`: `.tooltip` is not supported

本项目在官方迁移基础上的扩展支持：
- `ui_chart` → `ui-chart`（字段改写，转换后标记禁用 d:true，需手动启用）
- `ui_date_picker` → `ui-text-input`（`mode: date`，输出时间戳行为与 v1 一致，转换后直接可用）

## Port Configuration

By default, the server runs on port 3000. You can change this by setting the `PORT` environment variable:

```bash
PORT=8080 npm start
```

## Technologies Used

- **Node.js** - Runtime environment
- **Express** - Web server framework
- **node-red-dashboard-2-migration** - Conversion library

## License

MIT

## Credits

This project uses the [node-red-dashboard-2-migration](https://github.com/FlowFuse/node-red-dashboard-2-migration) library developed by FlowFuse.

