import * as admin from "firebase-admin";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { onCall } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2/options";

admin.initializeApp();

// Set global options for all functions - adjust region if needed
setGlobalOptions({ region: "europe-west1", maxInstances: 10 });

const COMPANY_ID = "NCORDINA";

/**
 * Deduplicate FCM tokens before sending.
 * The same physical device token can be registered under multiple user
 * profiles (shared devices, account switching) or appear in both the
 * manager and technician lists. Without dedup, sendEachForMulticast
 * delivers one notification per array entry - i.e. duplicates.
 */
function uniqueTokens(tokens: string[]): string[] {
  return [...new Set(tokens)];
}

async function getTokensForTechnicians(assigneeName: string): Promise<string[]> {
  const usersSnap = await admin.firestore()
    .collection("companies").doc(COMPANY_ID)
    .collection("users")
    .where("role", "==", "TECH")
    .where("assignedToName", "==", assigneeName)
    .where("notificationsEnabled", "==", true)
    .get();

  const tokens: string[] = [];
  for (const userDoc of usersSnap.docs) {
    const tokSnap = await userDoc.ref.collection("tokens")
      .where("active", "==", true).get();
    tokSnap.forEach((t) => {
      const tok = t.get("token");
      if (tok) tokens.push(tok);
    });
  }
  return tokens;
}

async function getTokensForManagers(): Promise<string[]> {
  const usersSnap = await admin.firestore()
    .collection("companies").doc(COMPANY_ID)
    .collection("users")
    .where("role", "==", "MANAGER")
    .where("notificationsEnabled", "==", true)
    .get();

  const tokens: string[] = [];
  for (const userDoc of usersSnap.docs) {
    const tokSnap = await userDoc.ref.collection("tokens")
      .where("active", "==", true).get();
    tokSnap.forEach((t) => {
      const tok = t.get("token");
      if (tok) tokens.push(tok);
    });
  }
  return tokens;
}

async function getTokensForAllTechnicians(): Promise<string[]> {
  const usersSnap = await admin.firestore()
    .collection("companies").doc(COMPANY_ID)
    .collection("users")
    .where("role", "==", "TECH")
    .where("notificationsEnabled", "==", true)
    .get();

  const tokens: string[] = [];
  for (const userDoc of usersSnap.docs) {
    const tokSnap = await userDoc.ref.collection("tokens")
      .where("active", "==", true).get();
    tokSnap.forEach((t) => {
      const tok = t.get("token");
      if (tok) tokens.push(tok);
    });
  }
  return tokens;
}

/**
 * v2 Firestore trigger for job changes
 * Path: companies/NCORDINA/tasks/{taskId}
 * 
 * Sends notifications:
 * - PENDING status → assigned TECH users
 * - DONE status → all MANAGER users
 */
export const onTaskWrite = onDocumentWritten(
  `companies/${COMPANY_ID}/tasks/{taskId}`,
  async (event) => {
    const after = event.data?.after?.data();
    if (!after) return;
    if (after.deleted === true) return;

    const status = String(after.status || "");
    const assignee = String(after.assignedToName || "");
    const clientId = String(after.clientId || "");
    const title = String(after.title || "Service visit");
    const taskId = event.params.taskId;

    // Fetch client name from Firestore
    let clientName = clientId;
    try {
      const clientDoc = await admin.firestore()
        .collection("companies").doc(COMPANY_ID)
        .collection("clients").doc(clientId)
        .get();
      
      if (clientDoc.exists) {
        clientName = String(clientDoc.get("name") || clientId);
      }
    } catch (error) {
      console.error("Error fetching client name:", error);
      // Fall back to clientId if fetch fails
    }

    if (status === "PENDING" && assignee) {
      // Notify the assigned TECH only
      const tokens = uniqueTokens(await getTokensForTechnicians(assignee));
      if (tokens.length === 0) return;
      
      await admin.messaging().sendEachForMulticast({
        tokens,
        notification: {
          title: "New job assigned",
          body: `${assignee}: ${title} for ${clientName}`,
        },
        data: {
          type: "TASK_ASSIGNED",
          taskId,
          clientId,
          status,
          click_action: "OPEN_TASKS",
        },
        android: { 
          priority: "high",
        },
      });
      return;
    }

    if (status === "DONE") {
      // Notify all MANAGERs
      const tokens = uniqueTokens(await getTokensForManagers());
      if (tokens.length === 0) return;
      
      await admin.messaging().sendEachForMulticast({
        tokens,
        notification: {
          title: "Job completed",
          body: `${title} for ${clientName} marked DONE`,
        },
        data: {
          type: "TASK_DONE",
          taskId,
          clientId,
          status,
          click_action: "OPEN_REPORTS",
        },
        android: { 
          priority: "high",
        },
      });
      return;
    }
  }
);

/**
 * v2 Firestore trigger for request changes
 * Path: companies/NCORDINA/requests/{requestId}
 * 
 * Sends notifications:
 * - OPEN status (new request) → all MANAGER and TECH users
 */
export const onRequestWrite = onDocumentWritten(
  `companies/${COMPANY_ID}/requests/{requestId}`,
  async (event) => {
    console.log("🔔 onRequestWrite triggered for requestId:", event.params.requestId);
    
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    
    console.log("Before exists:", !!before, "After exists:", !!after);
    
    // Only notify on new requests
    if (!after || before) {
      console.log("❌ Exiting: Not a new document (before exists or after missing)");
      return;
    }
    if (after.deleted === true) {
      console.log("❌ Exiting: Document is deleted");
      return;
    }

    const status = String(after.status || "");
    const clientId = String(after.clientId || "");
    const requestedBy = String(after.requestedByName || "User");
    const requestId = event.params.requestId;
    
    console.log("📋 Request details:", { status, clientId, requestedBy, requestId });

    // Fetch client name from Firestore
    let clientName = clientId;
    try {
      const clientDoc = await admin.firestore()
        .collection("companies").doc(COMPANY_ID)
        .collection("clients").doc(clientId)
        .get();
      
      if (clientDoc.exists) {
        clientName = String(clientDoc.get("name") || clientId);
      }
    } catch (error) {
      console.error("Error fetching client name:", error);
      // Fall back to clientId if fetch fails
    }

    if (status === "OPEN") {
      console.log("✅ Status is OPEN, fetching tokens for managers and technicians...");
      
      // Fetch tokens for both managers and technicians
      const managerTokens = await getTokensForManagers();
      const techTokens = await getTokensForAllTechnicians();
      // Dedup: a device registered under both lists (or under multiple user
      // profiles) must only receive ONE notification
      const allTokens = uniqueTokens([...managerTokens, ...techTokens]);
      
      console.log(`📱 Found ${managerTokens.length} manager tokens and ${techTokens.length} tech tokens (total: ${allTokens.length})`);
      
      if (allTokens.length === 0) {
        console.log("❌ No tokens found, exiting");
        return;
      }
      
      console.log("📤 Sending notification to managers and technicians...");
      const result = await admin.messaging().sendEachForMulticast({
        tokens: allTokens,
        notification: {
          title: "New service request",
          body: `${requestedBy} requested service for ${clientName}`,
        },
        data: {
          type: "REQUEST_CREATED",
          requestId,
          clientId,
          status,
          click_action: "OPEN_REQUESTS",
        },
        android: { 
          priority: "high",
        },
      });
      
      console.log("✅ Notification sent successfully:", {
        successCount: result.successCount,
        failureCount: result.failureCount
      });
      
      return;
    } else {
      console.log(`❌ Status is not OPEN (${status}), skipping notification`);
    }
  }
);

/**
 * Optional callable function for testing
 */
export const ping = onCall(async (req) => {
  return { ok: true, uid: req.auth?.uid ?? null, timestamp: Date.now() };
});

/**
 * Callable function to send a test notification to the current user
 * This is used for diagnostics in the Settings screen
 */
export const sendTestToUid = onCall(async (req) => {
  const uid = req.auth?.uid;
  if (!uid) {
    throw new Error("Authentication required");
  }

  // Get user profile to check if notifications are enabled
  const userRef = admin.firestore()
    .collection("companies").doc(COMPANY_ID)
    .collection("users").doc(uid);
  
  const userDoc = await userRef.get();
  if (!userDoc.exists) {
    return { 
      success: false, 
      message: "User profile not found",
      tokenCount: 0
    };
  }

  const notificationsEnabled = userDoc.get("notificationsEnabled");
  if (!notificationsEnabled) {
    return { 
      success: false, 
      message: "Notifications are disabled in settings. Please enable them first.",
      tokenCount: 0
    };
  }
  
  // Get all active tokens for this user
  const tokensSnap = await userRef.collection("tokens")
    .where("active", "==", true)
    .get();
  
  const tokens: string[] = [];
  tokensSnap.forEach((doc) => {
    const token = doc.get("token");
    if (token) tokens.push(token);
  });

  if (tokens.length === 0) {
    return { 
      success: false, 
      message: "No active tokens found for this user",
      tokenCount: 0
    };
  }

  // Send test notification to all active tokens
  const result = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {
      title: "Test Notification",
      body: "Push notifications are working correctly! ✓",
    },
    data: {
      type: "TEST",
      timestamp: Date.now().toString(),
    },
    android: { priority: "high" },
  });

  return { 
    success: true, 
    message: `Test notification sent to ${tokens.length} device(s)`,
    tokenCount: tokens.length,
    successCount: result.successCount,
    failureCount: result.failureCount
  };
});

// ========================================
// Route Completion Notification
// ========================================
export const onRouteCompleted = onDocumentWritten(
  "companies/{companyId}/routes/{routeId}",
  async (event) => {
    const after = event.data?.after;
    const before = event.data?.before;

    // Only proceed if route was just completed
    if (!after || !after.exists || !before || !before.exists) return;
    
    const newData = after.data();
    const oldData = before.data();
    
    if (!newData || !oldData) return;
    
    if (!oldData.isCompleted && newData.isCompleted) {
      // Route was just completed
      const routeName = newData.name || "A route";
      const completedBy = newData.completedBy || "Unknown";
      
      console.log(`Route completed: ${routeName} by ${completedBy}`);
      
      // Send notification to all managers
      const managerTokens = uniqueTokens(await getTokensForManagers());
      
      if (managerTokens.length === 0) {
        console.log("No manager tokens found for route completion notification");
        return;
      }
      
      await admin.messaging().sendEachForMulticast({
        tokens: managerTokens,
        notification: {
          title: "Route Completed ✓",
          body: `${completedBy} completed route: ${routeName}`,
        },
        data: {
          type: "ROUTE_COMPLETED",
          routeId: after.id,
          routeName: routeName,
          completedBy: completedBy,
          timestamp: Date.now().toString(),
        },
        android: {
          priority: "high",
          notification: {
            channelId: "route_updates",
            clickAction: "OPEN_REPORTS",
          },
        },
      });
      
      console.log(`Route completion notification sent to ${managerTokens.length} manager(s)`);
    }
  }
);

