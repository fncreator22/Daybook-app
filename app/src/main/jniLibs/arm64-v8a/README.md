# sqlite-vec native library

Place the precompiled ARM64 shared library here:

```
jniLibs/arm64-v8a/libsqlitevec.so
```

## Download

1. Go to https://github.com/asg017/sqlite-vec/releases
2. Download the latest `sqlite-vec-*-android-arm64-v8a.tar.gz`
3. Extract `libsqlitevec.so` and place it in this directory.

## Why not Maven?

sqlite-vec does not publish to Maven Central. The `.so` is ~1 MB and is
placed in `jniLibs/` per AGENTS.md spec. It is gitignored to avoid
committing a binary blob — each developer downloads it from the release page.

The `.gitignore` entry is: `app/src/main/jniLibs/arm64-v8a/libsqlitevec.so`

## Version used

0.1.6 (or latest at time of developer setup). Check releases for 16KB-page-size
support if targeting Android 15+ devices.

## Loading

`VectorStore.kt` loads the extension via:
```kotlin
database.execSQL("SELECT load_extension('libsqlitevec')")
```
This is called once after opening the database. If the `.so` is absent,
`VectorStore` catches the exception and degrades gracefully (no semantic search).
