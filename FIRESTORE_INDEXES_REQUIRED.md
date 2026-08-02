# Required Firestore Indexes for Route Optimization

The route optimization feature requires client location pins to sync from Firestore. Since pins are stored as subcollections under clients, a **collection group index** is required.

## Required Index

**Collection ID:** `pins`  
**Query scope:** Collection group  
**Fields indexed:**
- (Any field, Ascending or Descending)

## How to Create the Index

### Option 1: Via Firebase Console
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Navigate to **Firestore Database**
4. Click on **Indexes** tab
5. Click **Create Index**
6. Set:
   - Collection ID: `pins`
   - Query scope: **Collection group**
   - Add a field (e.g., `updatedAt`) with direction **Descending**
7. Click **Create**
8. Wait for the index to build (usually 1-2 minutes)

### Option 2: Via Error Message
1. Try to create a route in the app
2. Check the app logs for an error message containing a URL
3. Click the URL to automatically create the required index

### Option 3: Via firestore.indexes.json
Add this to your `firestore.indexes.json` file:

```json
{
  "indexes": [
    {
      "collectionGroup": "pins",
      "queryScope": "COLLECTION_GROUP",
      "fields": [
        {
          "fieldPath": "updatedAt",
          "order": "DESCENDING"
        }
      ]
    }
  ]
}
```

Then deploy: `firebase deploy --only firestore:indexes`

## Verification

After creating the index:
1. Wait 1-2 minutes for it to build
2. Force sync: Go to Settings > Diagnostics > Sync Health > Sync All Data
3. Check logs for: "Sync received X remote pins" (should show a number > 0)
4. Try creating a route again

## Why This Is Required

Firestore requires explicit indexes for:
- Collection group queries (querying across all `pins` subcollections)
- Queries with multiple fields or ordering

Without this index, the `listenAllPins()` query will fail silently, and pins won't sync to the app.










