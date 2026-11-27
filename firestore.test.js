const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function validProfile(uid, ts = pastDate()) {
  return {
    userId: uid,
    nickname: "김매니저",
    email: "alice@example.com",
    biometricEnabled: false,
    darkMode: "SYSTEM",
    oilAlertEnabled: true,
    oilAlertThresholdKm: 1000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validCompany(ts = pastDate()) {
  return {
    name: "두지물류",
    code: "DJ",
    accentColorHex: "#007AFF",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validVehicle(companyId, ts = pastDate()) {
  return {
    companyId,
    plateNumber: "123가 4567",
    modelName: "제네시스 G80",
    year: 2025,
    parkingLocation: "본사 B2 A-14",
    mileageKm: 24500,
    status: "ACTIVE",
    statusUpdatedBy: "김매니저",
    statusUpdatedAt: ts,
    lastOilChangeKm: 15000,
    oilChangeIntervalKm: 10000,
    oilAlertEnabled: true,
    insuranceCompany: "삼성화재",
    insurancePolicyNo: "SF-2026-88412",
    insuranceExpiryDate: "2027-05-31",
    insuranceCoverage: "종합 · 대인무한 · 대물 10억",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validMaintenance(vehicleId, ts = pastDate()) {
  return {
    vehicleId,
    serviceDate: "2026-10-15",
    category: "ENGINE_OIL",
    title: "엔진 오일 및 필터 교체",
    mileageKm: 15000,
    costKrw: 145000,
    shopName: "블루핸즈 강남점",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validHistoryLog(vehicleId, ts = pastDate()) {
  return {
    vehicleId,
    editorNickname: "박팀장",
    changeType: "STATUS",
    changes: ["상태: 운행 중 → 정비 중", "주행거리: 24,500 → 25,100 km"],
    createdAt: ts,
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

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("companies").get());
  await assertFails(unauthDb.collection("vehicles").get());
  await assertFails(unauthDb.collection("companies").doc("comp_1").set(validCompany()));
  await assertFails(unauthDb.collection("vehicles").doc("veh_1").set(validVehicle("comp_1")));
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
});

test("UserProfile: Google owner create with nickname, updates, and strict owner isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, created)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Cross-user profile get/update denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ darkMode: "DARK", updatedAt: pastDate(1000) })
  );
  // List users denied
  await assertFails(aliceDb.collection("users").get());

  // Valid preferences update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      darkMode: "DARK",
      biometricEnabled: true,
      oilAlertThresholdKm: 1500,
      updatedAt: pastDate(1000),
    })
  );

  // Valid nickname update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      nickname: "박팀장",
      updatedAt: pastDate(500),
    })
  );

  // Oversized nickname rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      nickname: "a".repeat(30),
      updatedAt: pastDate(400),
    })
  );

  // Ghost field / immutable createdAt / future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      darkMode: "LIGHT",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Shared Fleet (Companies, Vehicles, Maintenance, History): real-time multi-user collaboration & schema guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(aliceDb.collection("companies").get());
  await assertSucceeds(bobDb.collection("vehicles").get());

  // Orphaned vehicle creation fails when parent company does not exist
  await assertFails(
    aliceDb.collection("vehicles").doc("veh_orphan").set(validVehicle("missing_comp", created))
  );

  // Alice creates a company and vehicle
  await assertSucceeds(aliceDb.collection("companies").doc("comp_1").set(validCompany(created)));
  await assertSucceeds(aliceDb.collection("vehicles").doc("veh_1").set(validVehicle("comp_1", created)));

  // Bob (another Google user) can list and read Alice's company & vehicle
  await assertSucceeds(bobDb.collection("companies").get());
  await assertSucceeds(bobDb.collection("companies").doc("comp_1").get());
  await assertSucceeds(bobDb.collection("vehicles").get());
  await assertSucceeds(bobDb.collection("vehicles").doc("veh_1").get());

  // Bob updates company background photo and clears it
  await assertSucceeds(
    bobDb.collection("companies").doc("comp_1").update({
      name: "두지물류",
      code: "DJ",
      accentColorHex: "#007AFF",
      backgroundPhotoPath: "/data/user/0/com.aistudio.doojicar.k7m2px/files/parking_photos/comp_1.jpg",
      backgroundPhotoBase64: "/9j/4AAQSkZJRgABAQAAAQABAAD/",
      updatedAt: pastDate(3000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("companies").doc("comp_1").update({
      backgroundPhotoPath: null,
      backgroundPhotoBase64: null,
      updatedAt: pastDate(2500),
    })
  );

  // Bob updates vehicle mileage & status with his nickname
  await assertSucceeds(
    bobDb.collection("vehicles").doc("veh_1").update({
      mileageKm: 25100,
      status: "MAINTENANCE",
      statusUpdatedBy: "박팀장",
      statusUpdatedAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Future statusUpdatedAt rejected
  await assertFails(
    bobDb.collection("vehicles").doc("veh_1").update({
      mileageKm: 25200,
      status: "ACTIVE",
      statusUpdatedBy: "박팀장",
      statusUpdatedAt: new Date(Date.now() + 60000),
      updatedAt: pastDate(1800),
    })
  );

  // Bob updates parking location & photo
  await assertSucceeds(
    bobDb.collection("vehicles").doc("veh_1").update({
      parkingLocation: "지하 3층 C-08",
      parkingPhotoPath: "/data/user/0/com.aistudio.doojicar.k7m2px/files/parking_photos/veh_1.jpg",
      parkingPhotoBase64: "/9j/4AAQSkZJRgABAQAAAQABAAD/",
      statusUpdatedBy: "박팀장",
      statusUpdatedAt: pastDate(1500),
      updatedAt: pastDate(1500),
    })
  );

  // Bob updates vehicle representative photo & Alice clears it
  await assertSucceeds(
    bobDb.collection("vehicles").doc("veh_1").update({
      vehiclePhotoPath: "/data/user/0/com.aistudio.doojicar.k7m2px/files/parking_photos/veh_profile_1.jpg",
      vehiclePhotoBase64: "/9j/4AAQSkZJRgABAQAAAQABAAD/",
      statusUpdatedBy: "박팀장",
      statusUpdatedAt: pastDate(1400),
      updatedAt: pastDate(1400),
    })
  );
  await assertSucceeds(
    aliceDb.collection("vehicles").doc("veh_1").update({
      vehiclePhotoPath: null,
      vehiclePhotoBase64: null,
      statusUpdatedBy: "김매니저",
      statusUpdatedAt: pastDate(1300),
      updatedAt: pastDate(1300),
    })
  );

  // Maintenance record creation by Alice & read/delete by Bob
  await assertSucceeds(
    aliceDb.collection("vehicles").doc("veh_1").collection("maintenance").doc("rec_1").set(validMaintenance("veh_1", created))
  );
  await assertSucceeds(
    bobDb.collection("vehicles").doc("veh_1").collection("maintenance").get()
  );

  // Vehicle change history creation by Bob & reading by Alice
  await assertSucceeds(
    bobDb.collection("vehicles").doc("veh_1").collection("history").doc("log_1").set(validHistoryLog("veh_1", pastDate(1000)))
  );
  await assertSucceeds(
    aliceDb.collection("vehicles").doc("veh_1").collection("history").get()
  );

  // Change history is immutable (update denied for everyone)
  await assertFails(
    aliceDb.collection("vehicles").doc("veh_1").collection("history").doc("log_1").update({ editorNickname: "위조자" })
  );

  // Empty changes list rejected
  await assertFails(
    bobDb.collection("vehicles").doc("veh_1").collection("history").doc("log_empty").set({
      ...validHistoryLog("veh_1", pastDate(900)),
      changes: [],
    })
  );

  // Legacy private paths reject new creates and deny cross-user reads
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("companies").doc("legacy_1").set(validCompany(created))
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("companies").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("companies").get()
  );
});

test("AppReleaseConfig: signed-in read, admin-only write, HTTPS URL enforcement", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  const validRelease = {
    adminUid: ALICE_UID,
    latestVersionCode: 2,
    latestVersionName: "1.1",
    downloadUrl: "https://appdistribution.firebase.dev/i/example123",
    releaseNotes: "수정 내역 조회 기능 추가",
    forceUpdate: false,
    updatedBy: "김매니저",
    createdAt: created,
    updatedAt: created,
  };

  // Unauthenticated denied
  await assertFails(unauthDb.collection("app_config").doc("release").get());
  await assertFails(unauthDb.collection("app_config").doc("release").set(validRelease));

  // Non-HTTPS URL rejected
  await assertFails(
    aliceDb.collection("app_config").doc("release").set({
      ...validRelease,
      downloadUrl: "http://insecure.example.com/app.apk",
    })
  );

  // Admin creates release config
  await assertSucceeds(aliceDb.collection("app_config").doc("release").set(validRelease));

  // Another signed-in employee can read release config
  await assertSucceeds(bobDb.collection("app_config").doc("release").get());

  // Collection listing denied
  await assertFails(bobDb.collection("app_config").get());

  // Another employee cannot overwrite downloadUrl
  await assertFails(
    bobDb.collection("app_config").doc("release").update({
      downloadUrl: "https://evil.example.com/fake.apk",
      updatedAt: pastDate(1000),
    })
  );

  // Admin updates release config
  await assertSucceeds(
    aliceDb.collection("app_config").doc("release").update({
      latestVersionCode: 3,
      latestVersionName: "1.2",
      forceUpdate: true,
      updatedBy: "김매니저",
      updatedAt: pastDate(1000),
    })
  );
});
