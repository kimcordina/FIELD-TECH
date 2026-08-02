#!/bin/bash

# Deploy Cloud Functions Script
# This script deploys the Cloud Functions to Firebase

set -e

echo "🚀 Deploying Cloud Functions to Firebase..."
echo ""

# Navigate to project root (directory containing this script)
cd "$(dirname "$0")"

# Verify we're in the right directory
if [ ! -f "firebase.json" ]; then
    echo "❌ Error: firebase.json not found. Are you in the right directory?"
    exit 1
fi

# Set the Firebase project
echo "📋 Setting Firebase project: nc-field-tech-server"
firebase use nc-field-tech-server

echo ""
echo "🔨 Building functions..."
cd functions
# Use node directly - OneDrive strips the executable bit from node_modules/.bin
node node_modules/typescript/bin/tsc -p .
cd ..

echo ""
echo "🚀 Deploying functions..."
firebase deploy --only functions --project nc-field-tech-server

echo ""
echo "✅ Deployment complete!"
echo ""
echo "📊 Verify deployment:"
echo "   1. Visit: https://console.firebase.google.com/project/nc-field-tech-server/functions"
echo "   2. Check that 'onRequestWrite', 'onTaskWrite', 'onRouteCompleted' are Active in europe-west1"
echo "   3. Create a test request and confirm only ONE notification arrives per device"
echo ""
