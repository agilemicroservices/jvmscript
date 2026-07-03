# TODO

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
