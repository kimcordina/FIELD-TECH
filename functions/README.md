# Field Tech Cloud Functions

This directory contains Firebase Cloud Functions for sending targeted push notifications.

## Setup

1. Install Firebase CLI if you haven't already:
   ```bash
   npm install -g firebase-tools
   ```

2. Login to Firebase:
   ```bash
   firebase login
   ```

3. Initialize functions (if not already done):
   ```bash
   firebase init functions
   ```
   - Select your Firebase project
   - Choose TypeScript
   - Install dependencies

## Install Dependencies

```bash
cd functions
npm install
```

## Build

```bash
npm run build
```

## Deploy

Deploy all functions:
```bash
firebase deploy --only functions
```

Or deploy from the root project directory:
```bash
cd functions
npm install
npm run build
firebase deploy --only functions
```

## Function Behavior

### `onTaskWrite`

This function triggers whenever a task document is created or updated in:
`companies/NCORDINA/tasks/{taskId}`

**Notification Logic:**

1. **PENDING Status** (New/Updated Task):
   - Finds all users with:
     - `role = "TECH"`
     - `assignedToName` matches the task's `assignedToName`
     - `notificationsEnabled = true`
   - Sends push notification to all active tokens for matching technicians
   - Message: "New task assigned: {assignee}: {title} for client {clientId}"

2. **DONE Status** (Completed Task):
   - Finds all users with:
     - `role = "MANAGER"`
     - `notificationsEnabled = true`
   - Sends push notification to all active tokens for managers
   - Message: "Task completed: {title} (client {clientId}) marked DONE"

**User Document Structure:**
```
companies/{COMPANY_ID}/users/{userId}
  - displayName: string
  - role: "TECH" | "MANAGER" | "REQUESTER" | "NONE"
  - assignedToName: string (only for TECH)
  - notificationsEnabled: boolean
  - updatedAt: timestamp
  
  tokens/{tokenId}
    - token: string
    - platform: "android"
    - active: boolean
    - createdAt: timestamp
```

## Testing

After deployment, test by:
1. Creating a task with status "PENDING" and an assigned technician
2. Marking a task as "DONE"
3. Check that the appropriate users receive notifications

## Logs

View function logs:
```bash
firebase functions:log
```

Or in Firebase Console: Functions > Logs

