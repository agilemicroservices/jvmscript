# TODO

## Js7Utility (new - if/when migrating from JAMS)

- [ ] JS7 JobScheduler equivalent of JamsUtility, same stack (HttpUtility + Jackson, no new deps):
      - `js7Login(url, user, password)`: POST /joc/api/authentication/login -> X-Access-Token
        header on subsequent calls, 15-minute default timeout - reuse JamsUtility's
        token-cache-and-refresh pattern
      - `js7GetVariable(jobResource, name)` / `js7SetVariable(jobResource, name, value)`:
        JS7's equivalent of JAMS variables is the Job Resource (named inventory object;
        variables surface to shell jobs as env vars, to JVM jobs as arguments). Set =
        inventory update + deploy (JS7 auto-propagates to agents); hide the deploy inside
        the method. Granularity: one Job Resource per logical variable group, not per
        variable, to avoid deployment spam.
      - `js7AddOrder(workflow, variablesMap)`, `js7CancelOrder`, `js7GetOrderStatus`,
        `js7PostNotice(board)` for the operational verbs
      - add to groovyutil-lang default imports when built
- [ ] Main use case - nightly tradeDate roll (batch-completion-driven, not clock-driven):
      Job Resource `BusinessDate` with `tradeDate`; roll as final EOD workflow step (or
      PostNotice from EOD -> ExpectNotice roll workflow), either via the stock JITL
      SetJobResourceJob or js7SetVariable from a Groovy script (1:1 port of today's
      jamsSetVariable roll). Reads get simpler than JAMS: the variable arrives as an env
      var, so scripts read System.getenv("TRADE_DATE") - no scheduler API call at read time.
      Semantics note: in-flight jobs keep the old value (env captured at process start);
      new orders see the new value after auto-deploy (~seconds). JAMS reads-at-call-time
      differs subtly - a mid-run jamsGetVariable sees the new value.
      Reference: SOS KB "How to make a global Business Date variable available to jobs".
- [ ] Prototype against a real JOC Cockpit before freezing the API shape (inventory
      update/deploy call sequence is the part worth validating).

## ProcessUtility

- [ ] Rewrite on pure-JDK ProcessBuilder (Java 9+ process APIs cover everything used here;
      nothing new in Java 25) and drop the commons-exec dependency from the catalog.
      Keep the script-facing API (initializeProcess / addProcessArgument / executeProcess /
      getProcessOutput / getProcessExitValue) and fix while in there:
      - non-zero exit codes currently pass silently (check is `exitValue < 0` only) -
        throw with the process output in the message, with an opt-out for scripts that
        inspect exit codes deliberately
      - a watchdog timeout surfaces as a bare IllegalStateException - fail with a clear
        "timed out after Ns" error instead (and reconsider the short 60s default)
      - add setProcessWorkingDirectory(dir), addProcessEnvironment(key, value), and
        separate stderr capture (stdout/stderr currently merge silently)
      - add a logger (only utility with none); make the public static state private with
        accessors; remove the scratch main()
      - add unit tests - subprocess launching is trivially testable (echo/false/sleep)
      Minimal alternative if the rewrite is deferred: migrate off the commons-exec
      constructors deprecated in 1.4+ (DefaultExecutor/ExecuteWatchdog builders) and fix
      the exit-code and timeout behavior in place.

## S3Utility / S3Util

- [ ] Support the AWS default credentials provider chain: when no `accessKeyId` is configured,
      build the S3Client without a credentialsProvider so IAM roles (EC2/ECS/EKS), `AWS_*` env
      vars, `~/.aws/credentials`, and SSO all work. Static keys remain as the explicit fallback.
- [ ] Implement the missing core operations (delete stubs are currently empty, list-objects is
      absent entirely): `s3Ls(bucket, prefix)`, `s3DeleteFile`, `s3DeleteFolder`,
      `s3CopyFile`/`s3MoveFile` (copy + delete), `s3FileExists`.
- [ ] Fix `s3DoesBucketExist`: use `headBucket` and catch `NoSuchBucketException` specifically -
      today every exception (auth failure, network error, 403) reports as "bucket doesn't exist".
- [ ] Small cleanups: `s3CreateBucket` empty `CreateBucketConfiguration` only works in us-east-1
      (omit the config and let the SDK infer); remove unused request in `s3ListBuckets`, dead
      `emptyContent` in `s3CreateFolder`, and both scratch `main()` methods (one references an
      internal bucket name).
- [ ] Feature: presigned URL generation via `S3Presigner` - `s3GetPresignedUrl(bucket, key, minutes)`
      for temporary download links in notification emails.
- [ ] DEFERRED: `S3TransferManager` for multipart/parallel large-file transfers - adds the AWS CRT
      native dependency; revisit only if single-put performance on big files becomes a problem.
- [ ] Candidate for the toolkit's first integration test: LocalStack or S3Mock via Testcontainers.

## Record factories (DelimitedRecordFactory / FixedWidthRecordFactory)

- [ ] Replace the dead univocity-parsers dependency with FastCSV (already in the build):
      - `DelimitedRecordFactory` uses univocity only to tokenize (`parseAll` -> `List<String[]>`) -
        swap to FastCSV producing the same structure; `@DataField` mapping code is unchanged.
      - `SqlUtility` uses univocity's CSV/TSV writers for query exports - swap to FastCSV's `CsvWriter`.
      - Caveat: univocity is lenient with ragged rows/loose quoting, FastCSV is RFC-strict by
        default - configure leniency and test against a real-world messy file before removing.
      - Then delete univocity from gradle/libs.versions.toml.
- [ ] DECISION (2026-07-03): keep both record factories rather than migrate to an external library.
      Rationale: fixed-width has no vibrant maintained alternative (BeanIO is the only serious one);
      the factories are small, self-contained, and now covered by Spock tests; byte-exact output to
      downstream consumers makes migration risky for zero capability gain. Revisit BeanIO only if a
      requirement they can't handle appears (e.g. multi-record-type files, record-ordering rules).
      For *reading* delimited files, prefer the DataFrame schema loaders
      (`loadCsvWithYamlSchema`) in new scripts - DelimitedRecordFactory remains for bean-typed
      round trips and writing.

## FileUtility

- [ ] Add zstd single-file support: `zstdFile(filename)` / `zstdFile(filename, level)` -> `filename.zst`
      and `unZstdFile(filename)`, using commons-compress `ZstdCompressorOutputStream`/`InputStream`
      (requires adding the `com.github.luben:zstd-jni` runtime dependency, `implementation` scope).
- [ ] Add `.tar.zst` directory support: `tarZstdDirectory(directoryName)` / `unTarZstdFile(filename)`
      as the zstd equivalent of `zipDirectory`, using commons-compress tar + zstd streams.
      Intended for internal archiving (partner exchange stays zip/gz/pgp).
