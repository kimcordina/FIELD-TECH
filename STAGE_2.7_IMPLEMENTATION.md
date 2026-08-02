# Stage 2.7: Targeted Push Notifications + Roles Implementation

## Overview

This implementation adds role-based push notifications to the Field Tech app. Users can set their role (TECH, MANAGER, REQUESTER, or NONE) and receive targeted notifications based on task status changes.

## Company Configuration

- **Company ID**: `NCORDINA` (configured in `BuildConfig.COMPANY_ID`)
- **Firestore Path**: `companies/NCORDINA/`

## Implementation Summary

### 1. Android App Changes

#### 1.1 Dependencies Added

**File**: `app/build.gradle.kts`
- Added `firebase-messaging-ktx` dependency for Firebase Cloud Messaging

#### 1.2 Manifest Updates

**File**: `app/src/main/AndroidManifest.xml`
- Added `POST_NOTIFICATIONS` permission for Android 13+
- Registered `FtMessagingService` to handle incoming push notifications

#### 1.3 New Files Created

**`app/src/main/java/com/example/fieldtechv20kc/notifications/FtMessagingService.kt`**
- Handles incoming FCM messages
- Creates notification channels
- Displays notifications with high priority
- Uses custom notification icon

**`app/src/main/java/com/example/fieldtechv20kc/data/remote/firestore/UsersRemote.kt`**
- Manages user profile data in Firestore
- Methods:
  - `upsertProfile()`: Creates/updates user profile with role and tech identity
  - `setNotificationsEnabled()`: Toggles notification preference
  - `addToken()`: Registers FCM device token
  - `deactivateToken()`: Deactivates a device token

**`app/src/main/java/com/example/fieldtechv20kc/notifications/PushRegistrar.kt`**
- Handles FCM token registration
- Requests POST_NOTIFICATIONS permission on Android 13+
- Registers device token with Firestore

**`app/src/main/res/drawable/ic_notification.xml`**
- Bell icon for push notifications

#### 1.4 Settings Screen Updates

**File**: `app/src/main/java/com/example/fieldtechv20kc/ui/screens/SettingsScreen.kt`

Added new "Notifications" section with:

1. **Role Selector Dropdown**
   - Options: NONE, TECH, MANAGER, REQUESTER
   - Automatically updates notification preferences based on role
   - REQUESTER and NONE roles disable notifications by default

2. **Technician Identity Selector** (visible only when role = TECH)
   - Options: "Jenson", "Abubakar"
   - Links technician to their assigned tasks

3. **Notifications Toggle**
   - Allows TECH and MANAGER to enable/disable notifications
   - Disabled for REQUESTER and NONE roles
   - Persists preference to Firestore

#### 1.5 MainActivity Updates

**File**: `app/src/main/java/com/example/fieldtechv20kc/MainActivity.kt`

- Registers FCM token on app launch if user is signed in
- Listens for auth state changes to register token after sign-in
- Handles registration errors gracefully

### 2. Cloud Function

#### Files Created

**`functions/src/index.ts`**
- Main Cloud Function implementation
- Triggers on task document writes in `companies/NCORDINA/tasks/{taskId}`

**Notification Logic:**

1. **PENDING Status** (New/Updated Task):
   - Queries users with:
     - `role = "TECH"`
     - `assignedToName` matches task's `assignedToName`
     - `notificationsEnabled = true`
   - Sends notification: "New task assigned: {assignee}: {title} for client {clientId}"
   - Only notifies the specific assigned technician

2. **DONE Status** (Completed Task):
   - Queries users with:
     - `role = "MANAGER"`
     - `notificationsEnabled = true`
   - Sends notification: "Task completed: {title} (client {clientId}) marked DONE"
   - Notifies all managers with notifications enabled

**`functions/package.json`**
- Dependencies: firebase-admin, firebase-functions
- Build and deploy scripts

**`functions/tsconfig.json`**
- TypeScript configuration

**`functions/README.md`**
- Deployment instructions
- Function behavior documentation

### 3. Firestore Data Structure

#### User Profile Document
```
companies/NCORDINA/users/{userId}
  - displayName: string (user email)
  - role: "TECH" | "MANAGER" | "REQUESTER" | "NONE"
  - assignedToName: string | null (only for TECH role)
  - notificationsEnabled: boolean
  - updatedAt: timestamp
```

#### Device Token Subcollection
```
companies/NCORDINA/users/{userId}/tokens/{tokenId}
  - token: string (FCM token)
  - platform: "android"
  - active: boolean
  - createdAt: timestamp
```

## User Roles & Notification Behavior

| Role | Can Toggle Notifications | Receives Notifications For |
|------|-------------------------|---------------------------|
| **TECH** | ✅ Yes | Tasks assigned to them (PENDING status) |
| **MANAGER** | ✅ Yes | All completed tasks (DONE status) |
| **REQUESTER** | ❌ No (disabled) | Never receives notifications |
| **NONE** | ❌ No (disabled) | Never receives notifications |

## Deployment Instructions

### Android App

1. Sync Gradle dependencies
2. Build and install the app
3. The app will automatically register FCM tokens on sign-in

### Cloud Function

```bash
cd functions
npm install
npm run build
firebase deploy --only functions
```

## Testing Checklist

- [ ] Device A: Set role to TECH, identity to "Jenson", notifications ON
- [ ] Device B: Set role to MANAGER, notifications ON
- [ ] Device C: Set role to REQUESTER
- [ ] Create task assigned to "Jenson" with PENDING status → Device A receives notification
- [ ] Verify Device B and C do NOT receive notification
- [ ] Mark task as DONE → Device B receives notification
- [ ] Verify Device A and C do NOT receive notification
- [ ] Turn OFF notifications on Device A
- [ ] Create another task for "Jenson" → Device A does NOT receive notification
- [ ] Change Device A role to NONE → Create task for "Jenson" → Device A does NOT receive notification
- [ ] Test with multiple devices logged in as same user → Both receive notifications

## Key Features

✅ Role-based notification targeting
✅ Technician identity selection for task assignment
✅ Per-user notification preferences
✅ Multiple device support per user
✅ Automatic token registration on sign-in
✅ Permission handling for Android 13+
✅ High-priority notifications
✅ Graceful error handling

## Notes

- Notifications are non-critical; registration failures are silently handled
- REQUESTER role cannot receive notifications (toggle disabled)
- NONE role cannot receive notifications (toggle disabled)
- Only TECH role shows technician identity selector
- User profile is created/updated on first Settings screen load
- FCM tokens are automatically registered after sign-in
- Cloud Function filters out deleted tasks (`deleted !== true`)

## Firebase Console Verification

After deployment, verify in Firebase Console:

1. **Firestore**: Check `companies/NCORDINA/users/` for user profiles and tokens
2. **Functions**: Verify `onTaskWrite` function is deployed
3. **Cloud Messaging**: Monitor message delivery in Firebase Console

## Troubleshooting

- **No notifications received**: Check Firestore user document has correct role and `notificationsEnabled = true`
- **Token not registered**: Check app logs for permission issues or FCM errors
- **Function not triggering**: Check Firebase Functions logs for errors
- **Wrong users notified**: Verify role and assignedToName fields in Firestore

## Security

- Firestore rules allow authenticated users to read/write their company's data
- FCM tokens are stored securely in Firestore
- Cloud Function runs with admin privileges
- No sensitive data in notification payload (only IDs and titles)

