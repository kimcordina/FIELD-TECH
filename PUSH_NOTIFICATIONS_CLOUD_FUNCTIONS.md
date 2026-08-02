# Push Notifications Cloud Functions

## Overview
These Cloud Functions handle push notifications for task assignments and completions.

## Functions to Add/Update in `functions/src/index.ts`

```typescript
import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

// Initialize Firebase Admin if not already done
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const messaging = admin.messaging();
const COMPANY_ID = 'NCORDINA';

/**
 * Send push notification when a task is assigned (PENDING) or completed (DONE)
 */
export const onTaskWrite = functions.firestore
  .document(`companies/${COMPANY_ID}/tasks/{taskId}`)
  .onWrite(async (change, context) => {
    const taskId = context.params.taskId;
    
    // If document was deleted, skip
    if (!change.after.exists) {
      console.log(`Task ${taskId} was deleted, skipping notification`);
      return null;
    }
    
    const task = change.after.data();
    
    // Skip if task is marked as deleted
    if (task?.deleted === true) {
      console.log(`Task ${taskId} is soft-deleted, skipping notification`);
      return null;
    }
    
    const status = task?.status;
    const assignedToName = task?.assignedToName;
    
    console.log(`Task ${taskId} status: ${status}, assignedTo: ${assignedToName}`);
    
    // Handle PENDING status (task assigned)
    if (status === 'PENDING' && assignedToName) {
      return await sendTaskAssignedNotification(taskId, task);
    }
    
    // Handle DONE status (task completed)
    if (status === 'DONE') {
      return await sendTaskCompletedNotification(taskId, task);
    }
    
    return null;
  });

/**
 * Send notification to assigned technician when task becomes PENDING
 */
async function sendTaskAssignedNotification(taskId: string, task: any) {
  const assignedToName = task.assignedToName;
  const clientId = task.clientId;
  const title = task.title || 'Service visit';
  
  console.log(`Sending task assigned notification for ${taskId} to ${assignedToName}`);
  
  try {
    // Query users with matching role and assignedToName
    const usersSnapshot = await db.collection(`companies/${COMPANY_ID}/users`)
      .where('role', '==', 'TECH')
      .where('assignedToName', '==', assignedToName)
      .where('notificationsEnabled', '==', true)
      .get();
    
    if (usersSnapshot.empty) {
      console.log(`No TECH users found with assignedToName=${assignedToName} and notifications enabled`);
      return null;
    }
    
    // Collect all active tokens from matching users
    const tokens: string[] = [];
    for (const userDoc of usersSnapshot.docs) {
      const tokensSnapshot = await userDoc.ref.collection('tokens')
        .where('active', '==', true)
        .get();
      
      tokensSnapshot.forEach(tokenDoc => {
        tokens.push(tokenDoc.data().token);
      });
    }
    
    if (tokens.length === 0) {
      console.log(`No active tokens found for ${assignedToName}`);
      return null;
    }
    
    console.log(`Found ${tokens.length} active token(s) for ${assignedToName}`);
    
    // Get client name if available
    let clientName = clientId;
    try {
      const clientDoc = await db.collection(`companies/${COMPANY_ID}/clients`).doc(clientId).get();
      if (clientDoc.exists) {
        clientName = clientDoc.data()?.name || clientId;
      }
    } catch (e) {
      console.log(`Could not fetch client name: ${e}`);
    }
    
    // Send notification
    const message = {
      notification: {
        title: 'New task assigned',
        body: `${assignedToName}: ${title} for client ${clientName}`
      },
      data: {
        type: 'TASK_ASSIGNED',
        taskId: taskId,
        clientId: clientId,
        status: 'PENDING'
      },
      tokens: tokens
    };
    
    const response = await messaging.sendMulticast(message);
    console.log(`Task assigned notification sent: ${response.successCount} success, ${response.failureCount} failure`);
    
    return response;
  } catch (error) {
    console.error(`Error sending task assigned notification:`, error);
    return null;
  }
}

/**
 * Send notification to all managers when task is completed
 */
async function sendTaskCompletedNotification(taskId: string, task: any) {
  const clientId = task.clientId;
  const title = task.title || 'Service visit';
  
  console.log(`Sending task completed notification for ${taskId}`);
  
  try {
    // Query all manager users with notifications enabled
    const usersSnapshot = await db.collection(`companies/${COMPANY_ID}/users`)
      .where('role', '==', 'MANAGER')
      .where('notificationsEnabled', '==', true)
      .get();
    
    if (usersSnapshot.empty) {
      console.log(`No MANAGER users found with notifications enabled`);
      return null;
    }
    
    // Collect all active tokens from matching users
    const tokens: string[] = [];
    for (const userDoc of usersSnapshot.docs) {
      const tokensSnapshot = await userDoc.ref.collection('tokens')
        .where('active', '==', true)
        .get();
      
      tokensSnapshot.forEach(tokenDoc => {
        tokens.push(tokenDoc.data().token);
      });
    }
    
    if (tokens.length === 0) {
      console.log(`No active tokens found for MANAGER users`);
      return null;
    }
    
    console.log(`Found ${tokens.length} active token(s) for MANAGER users`);
    
    // Get client name if available
    let clientName = clientId;
    try {
      const clientDoc = await db.collection(`companies/${COMPANY_ID}/clients`).doc(clientId).get();
      if (clientDoc.exists) {
        clientName = clientDoc.data()?.name || clientId;
      }
    } catch (e) {
      console.log(`Could not fetch client name: ${e}`);
    }
    
    // Send notification
    const message = {
      notification: {
        title: 'Task completed',
        body: `${title} (client ${clientName}) marked DONE`
      },
      data: {
        type: 'TASK_DONE',
        taskId: taskId,
        clientId: clientId,
        status: 'DONE'
      },
      tokens: tokens
    };
    
    const response = await messaging.sendMulticast(message);
    console.log(`Task completed notification sent: ${response.successCount} success, ${response.failureCount} failure`);
    
    return response;
  } catch (error) {
    console.error(`Error sending task completed notification:`, error);
    return null;
  }
}

/**
 * Callable function to send a test notification to the current user
 */
export const sendTestToUid = functions.https.onCall(async (data, context) => {
  // Ensure user is authenticated
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'User must be authenticated');
  }
  
  const uid = context.auth.uid;
  console.log(`Sending test notification to user ${uid}`);
  
  try {
    // Get user profile
    const userDoc = await db.collection(`companies/${COMPANY_ID}/users`).doc(uid).get();
    
    if (!userDoc.exists) {
      console.log(`User ${uid} profile not found`);
      return { ok: false, success: 0, failure: 0, error: 'User profile not found' };
    }
    
    const userData = userDoc.data();
    const role = userData?.role || 'NONE';
    const assignedToName = userData?.assignedToName;
    const notificationsEnabled = userData?.notificationsEnabled || false;
    
    console.log(`User ${uid}: role=${role}, assignedToName=${assignedToName}, enabled=${notificationsEnabled}`);
    
    if (!notificationsEnabled) {
      console.log(`Notifications disabled for user ${uid}`);
      return { ok: false, success: 0, failure: 0, error: 'Notifications are disabled for this user' };
    }
    
    // Get active tokens
    const tokensSnapshot = await userDoc.ref.collection('tokens')
      .where('active', '==', true)
      .get();
    
    if (tokensSnapshot.empty) {
      console.log(`No active tokens found for user ${uid}`);
      return { ok: false, success: 0, failure: 0, error: 'No active tokens found' };
    }
    
    const tokens: string[] = [];
    tokensSnapshot.forEach(tokenDoc => {
      tokens.push(tokenDoc.data().token);
    });
    
    console.log(`Found ${tokens.length} active token(s) for user ${uid}`);
    
    // Send test notification
    const message = {
      notification: {
        title: 'Field Tech (test)',
        body: 'If you can see this, push is working.'
      },
      data: {
        type: 'TEST',
        timestamp: Date.now().toString()
      },
      tokens: tokens
    };
    
    const response = await messaging.sendMulticast(message);
    console.log(`Test notification sent to ${uid}: ${response.successCount} success, ${response.failureCount} failure`);
    
    return {
      ok: true,
      success: response.successCount,
      failure: response.failureCount,
      tokensCount: tokens.length,
      role: role,
      assignedToName: assignedToName
    };
  } catch (error) {
    console.error(`Error sending test notification:`, error);
    return {
      ok: false,
      success: 0,
      failure: 0,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
});
```

## Deployment Instructions

1. Update your `functions/src/index.ts` with the code above
2. Ensure you have the required dependencies in `functions/package.json`:
   ```json
   {
     "dependencies": {
       "firebase-admin": "^12.0.0",
       "firebase-functions": "^5.0.0"
     }
   }
   ```
3. Deploy the functions:
   ```bash
   cd functions
   npm install
   npm run build
   firebase deploy --only functions
   ```

## Testing

After deployment, you can test the functions:

1. **Task Assignment**: Create or update a task with `status: "PENDING"` and `assignedToName: "Jenson"` or `"Abubakar"`
2. **Task Completion**: Update a task to `status: "DONE"`
3. **Self-test**: Call the `sendTestToUid` function from the app

## Logs

View function logs in Firebase Console:
```bash
firebase functions:log
```

Or in the Firebase Console → Functions → Logs










