#!/bin/bash

# Node-RED Dashboard Converter - Run Script
# This script starts the server which serves both the API and web UI

set -e

echo "🚀 Starting Node-RED Dashboard Converter..."
echo ""

# Check if node_modules exists, if not install dependencies
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
    echo ""
fi

# Check if node is available
if ! command -v node &> /dev/null; then
    echo "❌ Error: Node.js is not installed or not in PATH"
    echo "Please install Node.js from https://nodejs.org/"
    exit 1
fi

# Get the port from environment or use default
PORT=${PORT:-3000}

echo "✅ Dependencies installed"
echo "🌐 Starting server on port $PORT..."
echo ""
echo "📝 Access the web interface at: http://localhost:$PORT"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Start the server
node server.js

