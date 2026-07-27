# PDF Upload Retry Idempotency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make PDF upload retries reuse complete parsed chunks, recover from incomplete parsed state, and let the configured local administrator bypass PaiSmart's internal token budgets.

**Architecture:** `DocumentService` owns the persisted parsing-state decision because it already coordinates `file_upload`, parent chunks, child chunks, Elasticsearch, and cache cleanup. `FileProcessingConsumer` asks that service whether parsing is required before downloading and parsing the file. `UsageBalanceQuotaService` owns the local-admin bypass, guarded by an explicit local-only property plus database username and ADMIN role checks.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Data JPA, Spring Kafka, Redis, MySQL, JUnit 5, Mockito.

---

### Task 1: Persisted parsing-state decision

**Files:**
- Modify: `src/main/java/com/yizhaoqi/smartpai/repository/DocumentParentChunkRepository.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/DocumentService.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/service/DocumentParsingStateServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create Mockito tests that construct a `FileUpload` with `estimatedChunkCount = 481` and verify:

```java
assertFalse(documentService.prepareUploadParsing(FILE_MD5));
verify(documentVectorRepository, never()).deleteByFileMd5(FILE_MD5);
verify(documentParentChunkRepository, never()).deleteByFileMd5(FILE_MD5);
```

when actual child count is 481 and parent count is positive; and verify:

```java
assertTrue(documentService.prepareUploadParsing(FILE_MD5));
verify(documentVectorRepository).deleteByFileMd5(FILE_MD5);
verify(documentParentChunkRepository).deleteByFileMd5(FILE_MD5);
verify(elasticsearchService).deleteByFileMd5(FILE_MD5);
```

when the actual child count differs from the estimate.

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -q -Dtest=DocumentParsingStateServiceTest test
```

Expected: compilation failure because `prepareUploadParsing` and `countByFileMd5` do not exist.

- [ ] **Step 3: Implement the minimal persisted-state decision**

Add this repository method:

```java
long countByFileMd5(String fileMd5);
```

Add `DocumentService.prepareUploadParsing(String fileMd5)` which reads the latest upload row, child count, parent count, and expected count. Return `false` only when expected count is positive, child count equals expected count, and parent count is positive. When nonzero persisted data is incomplete, delete Elasticsearch data, child rows, parent rows, and invalidate the PDF preview cache before returning `true`.

- [ ] **Step 4: Run the tests and verify GREEN**

Run the Task 1 command again. Expected: all `DocumentParsingStateServiceTest` tests pass.

### Task 2: Kafka upload retry reuses complete parsing

**Files:**
- Modify: `src/main/java/com/yizhaoqi/smartpai/consumer/FileProcessingConsumer.java`
- Create: `src/test/java/com/yizhaoqi/smartpai/consumer/FileProcessingConsumerTest.java`

- [ ] **Step 1: Write the failing consumer test**

Mock `documentService.prepareUploadParsing(FILE_MD5)` to return `false`, return a successful `VectorizationUsageResult` from vectorization, call `processTask`, then assert:

```java
verify(parseService, never()).parseAndSave(anyString(), any(), anyString(), anyString(), anyBoolean());
verify(vectorizationService).vectorizeWithUsage(FILE_MD5, "1", "admin", false, "1");
verify(documentService).markVectorizationCompleted(eq(FILE_MD5), any());
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -q -Dtest=FileProcessingConsumerTest test
```

Expected: the current consumer tries to download/parse instead of reusing complete chunks.

- [ ] **Step 3: Implement stage-aware consumption**

For `UPLOAD_PROCESS`, call `prepareUploadParsing` before opening the source stream. Download and call `parseAndSave` only when it returns `true`; otherwise log that complete parsed chunks are being reused. Always continue to `vectorizeWithUsage`. Leave `REINDEX` behavior unchanged.

- [ ] **Step 4: Run the test and verify GREEN**

Run the Task 2 command again. Expected: `FileProcessingConsumerTest` passes.

### Task 3: Local administrator bypasses internal token controls

**Files:**
- Modify: `src/main/java/com/yizhaoqi/smartpai/config/UsageQuotaProperties.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/config/QuotaConfiguration.java`
- Modify: `src/main/java/com/yizhaoqi/smartpai/service/UsageBalanceQuotaService.java`
- Modify local ignored runtime config: `src/main/resources/application-local.yml`
- Create: `src/test/java/com/yizhaoqi/smartpai/service/UsageBalanceQuotaServiceTest.java`

- [ ] **Step 1: Write failing bypass-policy tests**

Configure `localAdminBypass.enabled = true`, `username = "admin"`, and mock user ID 1 as username `admin` with role `ADMIN`. Assert both global-budget reservation methods return noop bundles and do not read or consume balances. Add tests proving an ordinary user and a disabled bypass still invoke the existing balance check.

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -q -Dtest=UsageBalanceQuotaServiceTest test
```

Expected: compilation failure because the bypass properties and constructor dependency do not exist.

- [ ] **Step 3: Implement the guarded bypass**

Add nested configuration:

```java
private LocalAdminBypass localAdminBypass = new LocalAdminBypass();

@Data
public static class LocalAdminBypass {
    private boolean enabled;
    private String username = "admin";
}
```

Inject `UserRepository` into `UsageBalanceQuotaService`. A bypass matches only when enabled and the numeric user ID resolves to the configured username with role `ADMIN`. For a match, return noop reservations for LLM and Embedding global-budget methods and skip chat request accounting. Configure the ignored `application-local.yml` profile with `enabled: true`; the Java default remains false for every other environment.

- [ ] **Step 4: Run the tests and verify GREEN**

Run the Task 3 command again. Expected: all `UsageBalanceQuotaServiceTest` tests pass.

### Task 4: Compile, migrate runtime, and retry the real PDF

**Files:**
- Verify all files from Tasks 1-3.

- [ ] **Step 1: Run focused regression tests and compile**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -q -Dtest=DocumentParsingStateServiceTest,FileProcessingConsumerTest,UsageBalanceQuotaServiceTest,DocumentChunkSchemaMappingTest test
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -q -DskipTests compile
```

Expected: zero failures and compilation exit code 0.

- [ ] **Step 2: Restart the explicitly requested backend runtime**

Stop only the Java process listening on port 8081 after verifying its command is `SmartPaiApplication`, then start with Java 17 using `mvn spring-boot:run`. Wait until port 8081 is listening and both `dev` and `local` profiles are active.

- [ ] **Step 3: Retry the current PDF through the authenticated endpoint**

Authenticate using the ignored local `.env`, then call:

```text
POST /api/v1/documents/04c52be7527b2ef71de2329d4db8b767/vectorization/retry
```

Poll `/api/v1/documents/accessible` until this document reaches `COMPLETED` or a bounded timeout expires.

- [ ] **Step 4: Verify persistent and runtime evidence**

Confirm MySQL reports exactly 481 `document_vectors`, 175 `document_parent_chunks`, 481 distinct child indices, and 175 distinct `(file_md5,parent_index)` values. Confirm the file row is `COMPLETED`, `actual_chunk_count = 481`, and has nonzero actual embedding tokens. Confirm post-restart logs contain neither the duplicate-key error nor PaiSmart internal balance errors for this MD5.

- [ ] **Step 5: Verify the real browser page**

Open `http://localhost:9527/#/chat`, navigate to the knowledge-base document list, and confirm the PDF shows completed vectorization without a retry error.
