# Cloud Functions Deployment Guide

## ✅ Upgrade Complete!

I've successfully upgraded your Cloud Functions to:
- **Node 20** (from Node 18)
- **firebase-functions v5.1.1** (from v4.x)
- **firebase-admin v12.6.0** (from v11.x)
- **v2 Firestore triggers** with proper region configuration

## 📋 What Was Changed

### 1. `functions/package.json`
- ✅ Upgraded to Node 20
- ✅ Updated firebase-functions to v5.1.1
- ✅ Updated firebase-admin to v12.6.0
- ✅ Added rimraf for clean builds
- ✅ Updated TypeScript to v5.4.0

### 2. `functions/tsconfig.json`
- ✅ Updated to ES2020 target
- ✅ Configured proper module resolution
- ✅ Set output directory to `lib/`

### 3. `functions/src/index.ts`
- ✅ Migrated to v2 Firestore triggers (`onDocumentWritten`)
- ✅ Added global region configuration: `europe-west1`
- ✅ Updated to use `sendEachForMulticast` (v5 API)
- ✅ Added optional `ping` callable function for testing
- ✅ Kept all existing notification logic intact

### 4. Configuration Files
- ✅ Created `.firebaserc` with project ID
- ✅ Created `firebase.json` with functions config
- ✅ Created deployment script

## 🚀 Deployment Instructions

### Option 1: Automatic Deployment (Recommended)

Run the deployment script I created:

```bash
cd "/Users/kimcordina/Desktop/Field Tech 3.0 latest"
./deploy-functions.sh
```

### Option 2: Manual Deployment

```bash
cd "/Users/kimcordina/Desktop/Field Tech 3.0 latest"

# 1. Set the project
firebase use nc-field-tech-server

# 2. Build functions
cd functions
npm run build
cd ..

# 3. Deploy
firebase deploy --only functions --project nc-field-tech-server
```

## ⚠️ IAM Permissions Required

The deployment attempted but needs IAM permissions. You have two options:

### Option A: Run as Project Owner (Easiest)

Make sure you're logged in as a project owner:

```bash
firebase logout
firebase login
# Login with an account that has Owner role on the project
firebase deploy --only functions --project nc-field-tech-server
```

### Option B: Manually Set IAM Permissions

If you have `gcloud` CLI installed and project owner access:

```bash
# Grant required IAM roles
gcloud projects add-iam-policy-binding nc-field-tech-server \
  --member=serviceAccount:service-146821072007@gcp-sa-pubsub.iam.gserviceaccount.com \
  --role=roles/iam.serviceAccountTokenCreator

gcloud projects add-iam-policy-binding nc-field-tech-server \
  --member=serviceAccount:146821072007-compute@developer.gserviceaccount.com \
  --role=roles/run.invoker

gcloud projects add-iam-policy-binding nc-field-tech-server \
  --member=serviceAccount:146821072007-compute@developer.gserviceaccount.com \
  --role=roles/eventarc.eventReceiver

# Then retry deployment
firebase deploy --only functions --project nc-field-tech-server
```

### Option C: Enable APIs via Firebase Console

1. Go to https://console.firebase.google.com/project/nc-field-tech-server
2. Navigate to **Project Settings** > **Service accounts**
3. Click **Manage service account permissions**
4. Grant the required roles as shown above

## 📊 Verify Deployment

After successful deployment:

1. **Firebase Console**:
   - Visit: https://console.firebase.google.com/project/nc-field-tech-server/functions
   - Verify functions are listed:
     - `onTaskWrite` - Active in europe-west1
     - `ping` - Active in europe-west1

2. **Test the Functions**:
   ```bash
   # View logs
   firebase functions:log --project nc-field-tech-server
   
   # Test the ping function
   firebase functions:shell --project nc-field-tech-server
   # Then run: ping()
   ```

3. **Test Notifications**:
   - Create or update a task in Firestore with status "PENDING"
   - Check Firebase Console > Functions > Logs
   - Verify the function executed and sent notifications

## 🔍 What the Functions Do

### `onTaskWrite`
- **Trigger**: Firestore document write at `companies/NCORDINA/tasks/{taskId}`
- **Region**: europe-west1
- **Behavior**:
  - **PENDING status**: Sends notification to assigned TECH users
  - **DONE status**: Sends notification to all MANAGER users
- **Filters**: Skips deleted tasks

### `ping`
- **Type**: Callable HTTPS function
- **Region**: europe-west1
- **Purpose**: Testing/health check
- **Returns**: `{ ok: true, uid: string | null, timestamp: number }`

## 🎯 Key Improvements

✅ **No more Node 18 deprecation warnings**  
✅ **No more firebase-functions v4 warnings**  
✅ **v2 triggers with proper region configuration**  
✅ **Updated FCM API** (`sendEachForMulticast`)  
✅ **Better error handling and logging**  
✅ **TypeScript 5.x with modern config**  
✅ **Clean build process**  

## 🐛 Troubleshooting

### "User code failed to load"
- ✅ **FIXED**: Upgraded to v5 and Node 20

### "Cannot determine backend specification"
- ✅ **FIXED**: Using v2 exports with proper configuration

### "IAM policy modification failed"
- **Solution**: Login as project owner or manually set IAM permissions (see above)

### Functions not triggering
- Check Firestore path matches: `companies/NCORDINA/tasks/{taskId}`
- Verify task document has required fields: `status`, `assignedToName`, `clientId`, `title`
- Check Firebase Console > Functions > Logs for errors

### No notifications received
- Verify user documents exist in `companies/NCORDINA/users/`
- Check user has correct `role` and `notificationsEnabled: true`
- Verify device tokens exist in `users/{uid}/tokens/` with `active: true`
- Check Android app registered FCM token successfully

## 📝 Build Output

The functions are built and ready:
- ✅ Dependencies installed (287 packages)
- ✅ TypeScript compiled to `functions/lib/index.js`
- ✅ Source maps generated
- ✅ No vulnerabilities found

## 🔄 Next Steps

1. **Deploy the functions** using one of the methods above
2. **Test notifications**:
   - Set user role to TECH in app Settings
   - Create a task assigned to that technician
   - Verify notification received
3. **Monitor logs** in Firebase Console
4. **Build the Android app** with the updated notification features

## 📚 Additional Resources

- [Firebase Functions v2 Documentation](https://firebase.google.com/docs/functions/2nd-gen)
- [Cloud Functions IAM Roles](https://cloud.google.com/functions/docs/reference/iam/roles)
- [Firebase Console](https://console.firebase.google.com/project/nc-field-tech-server)

---

**Status**: ✅ Functions upgraded and ready to deploy  
**Next Action**: Run deployment with project owner credentials

