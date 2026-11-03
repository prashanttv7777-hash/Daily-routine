# Security Specification — My Daily Life

## 1. Data Invariants

1. **Multi-User Isolation**: Every document in `/users/{userId}/tasks/{taskId}`, `/users/{userId}/routines/{routineId}`, and `/users/{userId}/backups/{backupId}` strictly belongs to `userId == request.auth.uid`, and its internal `userId` field must equal `request.auth.uid`.
2. **Schema & Bounds Integrity**: Every string field enforces strict character length bounds; enum fields (`priority`, `recurrence`, `timeSlot`, `frequency`) only permit defined values; timestamps (`createdAt`, `updatedAt`) must be valid Firestore `timestamp` objects `<= request.time`.
3. **Immutable Metadata**: On updates, `userId` and `createdAt` are strictly immutable (`incoming().userId == existing().userId && incoming().createdAt == existing().createdAt`).
4. **Path Variable Hardening**: Single-document operations (`get`, `create`, `update`, `delete`) verify `isValidId(userId)` and `isValidId(docId)`.

## 2. The "Dirty Dozen" Adversarial Payloads

1. **Unauthenticated Read/Write**: `auth = null` attempting `get` or `create` on `/users/alice/tasks/t1`.
2. **Cross-User Read**: `auth.uid = "bob"` attempting `get` or `list` on `/users/alice/tasks`.
3. **Identity Spoofing on Create**: `auth.uid = "alice"` creating `/users/alice/tasks/t1` with `{"userId": "bob", ...}`.
4. **Shadow Update (Ghost Field Injection)**: `auth.uid = "alice"` updating `/users/alice/tasks/t1` with `{"isVerifiedAdmin": true}`.
5. **Immutable Field Tampering (`createdAt`)**: `auth.uid = "alice"` updating `createdAt` to a newer timestamp.
6. **Immutable Field Tampering (`userId`)**: `auth.uid = "alice"` updating `userId` to `"bob"` on an existing task.
7. **Oversized Title / Denial of Wallet**: `title` with 5000 characters (`size() > 150`).
8. **Invalid Enum Injection (`priority`)**: `priority: "SUPER_URGENT"` instead of `"High" | "Medium" | "Low"`.
9. **Invalid Time Format**: `startTime: "9am"` instead of 5-char `"09:00"`.
10. **Future Timestamp Spoofing**: `createdAt` set to `request.time + 10 hours`.
11. **Type Poisoning on Update**: Updating `completed` to `"yes"` (string) instead of `boolean`.
12. **Oversized Backup Payload**: `payloadJson` empty or exceeding 500,000 characters.
