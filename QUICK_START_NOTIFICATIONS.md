# Quick Start: Push Notifications Setup

## 🚀 Quick Deploy Guide

### Step 1: Build & Install Android App

The Android app is ready to go! Just build and install:

```bash
./gradlew assembleDebug
```

The app will automatically:
- Request notification permissions (Android 13+)
- Register FCM tokens on sign-in
- Show the new Notifications section in Settings

### Step 2: Deploy Cloud Function

```bash
cd functions
npm install
npm run build
firebase deploy --only functions
```

That's it! The function will automatically start listening for task changes.

## 📱 Using the App

### For Technicians

1. Open the app and sign in
2. Go to **Settings** (bottom navigation)
3. Scroll to the **Notifications** section
4. Select **Role**: `TECH`
5. Select **I am (technician)**: Choose your name (`Jenson` or `Abubakar`)
6. Ensure **Allow notifications** is ON

Now you'll receive notifications when tasks are assigned to you!

### For Managers

1. Open the app and sign in
2. Go to **Settings**
3. Scroll to the **Notifications** section
4. Select **Role**: `MANAGER`
5. Ensure **Allow notifications** is ON

Now you'll receive notifications when tasks are completed!

### For Requesters

1. Open the app and sign in
2. Go to **Settings**
3. Scroll to the **Notifications** section
4. Select **Role**: `REQUESTER`

Requesters don't receive notifications (toggle is disabled).

## 🧪 Quick Test

1. **Test Tech Notification**:
   - Set Device A role to TECH, identity to "Jenson"
   - Create a task assigned to "Jenson" with status PENDING
   - Device A should receive: "New task assigned: Jenson: [title] for client [id]"

2. **Test Manager Notification**:
   - Set Device B role to MANAGER
   - Mark any task as DONE
   - Device B should receive: "Task completed: [title] (client [id]) marked DONE"

## 🔧 Troubleshooting

**Not receiving notifications?**
- Check Settings > Notifications section
- Verify your role is set correctly
- Ensure "Allow notifications" toggle is ON
- For TECH: Verify your technician identity matches the task assignment

**Function not deploying?**
- Ensure Firebase CLI is installed: `npm install -g firebase-tools`
- Login to Firebase: `firebase login`
- Check you're in the functions directory when deploying

**Permission denied?**
- Android 13+: App will request POST_NOTIFICATIONS permission
- If denied, go to Android Settings > Apps > Field Tech > Notifications > Allow

## 📊 What Gets Notified

| Event | Who Gets Notified | Message |
|-------|-------------------|---------|
| Task created/updated with PENDING status | Assigned TECH only | "New task assigned: {tech}: {title} for client {id}" |
| Task marked as DONE | All MANAGERs | "Task completed: {title} (client {id}) marked DONE" |

## 🎯 Key Points

- ✅ Multiple devices per user are supported
- ✅ Notifications work even when app is closed
- ✅ Each user controls their own notification preferences
- ✅ REQUESTER and NONE roles never receive notifications
- ✅ TECH users must select their identity to receive task notifications
- ✅ Notifications are high-priority (appear immediately)

## 📝 Next Steps

After setup:
1. Configure user roles in Settings
2. Test with real tasks
3. Monitor Firebase Console > Functions > Logs for any issues
4. Check Firestore > companies > NCORDINA > users to see registered tokens

Need help? Check `STAGE_2.7_IMPLEMENTATION.md` for detailed documentation.

