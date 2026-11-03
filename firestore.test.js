const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTaskPayload(uid, now = new Date()) {
  return {
    userId: uid,
    title: "Morning Deep Work",
    description: "Complete quarterly roadmap architecture",
    category: "Work",
    date: "2026-11-03",
    startTime: "09:00",
    endTime: "10:30",
    completed: false,
    priority: "High",
    recurrence: "Daily",
    recurrenceDays: "1,2,3,4,5",
    reminderEnabled: true,
    reminderMinutesBefore: 15,
    isImportantDate: true,
    createdAt: now,
    updatedAt: now,
  };
}

function validRoutinePayload(uid, now = new Date()) {
  return {
    userId: uid,
    title: "Hydration & Stretch",
    description: "500ml water and 10 min mobility",
    timeSlot: "Morning",
    startTime: "07:00",
    endTime: "07:20",
    category: "Health",
    frequency: "Daily",
    daysOfWeek: "1,2,3,4,5,6,7",
    active: true,
    reminderMinutesBefore: 10,
    createdAt: now,
    updatedAt: now,
  };
}

function validBackupPayload(uid, now = new Date()) {
  return {
    userId: uid,
    label: "Weekly Snapshot",
    taskCount: 5,
    routineCount: 3,
    payloadJson: "{\"tasks\":[],\"routines\":[]}",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set(validTaskPayload(ALICE_UID))
  );
});

test("Authenticated user can create, query, update, and delete their own tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");
  const initial = validTaskPayload(ALICE_UID);

  await assertSucceeds(docRef.set(initial));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    docRef.update({ completed: true, updatedAt: new Date() })
  );
  await assertSucceeds(docRef.delete());
});

test("Authenticated user cannot read or modify another user's tasks", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set(validTaskPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("tasks").where("userId", "==", ALICE_UID).get()
  );
});

test("Shadow update with ghost field or invalid enum is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");
  await assertSucceeds(docRef.set(validTaskPayload(ALICE_UID)));

  await assertFails(
    docRef.update({ isVerified: true, updatedAt: new Date() })
  );
  await assertFails(
    docRef.update({ priority: "INVALID_PRIORITY", updatedAt: new Date() })
  );
});

test("Authenticated user can manage routines and backups with strict isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const routineRef = aliceDb.collection("users").doc(ALICE_UID).collection("routines").doc("routine_1");
  await assertSucceeds(routineRef.set(validRoutinePayload(ALICE_UID)));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("routines").doc("routine_1").get());

  const backupRef = aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_1");
  await assertSucceeds(backupRef.set(validBackupPayload(ALICE_UID)));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("backups").doc("backup_1").get());
});
