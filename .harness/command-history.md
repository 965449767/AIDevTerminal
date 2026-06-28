# Command History

## 2026-06-14

Validation commands run:

```bash
bash scripts/harness_check.sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
```

Result:

```text
passed
```

## 2026-06-14 - 0.13.2-c

Commands:

```bash
git status --short
bash scripts/harness_check.sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
git diff --stat
```

Result:

```text
passed
```

## 2026-06-14 - 0.13.2-b

Commands:

```bash
git add .
git commit -m "refactor(terminal): extract ubuntu bootstrap scripts"
git status --short
bash scripts/harness_check.sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
git diff --stat
```

Result:

```text
passed
```

## 2026-06-14 - Git initialization

Commands:

```bash
git init
git branch -m main
git config user.name "AIDev Harness"
git config user.email "aidev-harness@example.local"
git status --short
```

Result:

```text
passed
```

## 2026-06-28 — 全量审计 + 修复

Commands:

```bash
./gradlew :app:testShellScripts --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
git log --oneline -5
git diff --stat cf076dc..4d5c41f
```

Result:

```text
testShellScripts: 47/47 PASS
compileDebugKotlin: BUILD SUCCESSFUL
testDebugUnitTest: 78/78 PASS
assembleDebug: BUILD SUCCESSFUL
```

Commands:

```bash
bash scripts/harness_check.sh
git add .
git commit -m "chore: initial aidev terminal snapshot"
git status --short
git log --oneline -1
```

Result:

```text
commit 9a1b229 created
```

## 2026-06-14 - 0.13.2-a

Commands:

```bash
git status --short
bash scripts/harness_check.sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
git diff --stat
```

Result:

```text
passed
```

## 2026-06-14 - 0.13.1

Command:

```bash
git status --short
```

Result:

```text
failed: not a Git repository
```

Command:

```bash
bash scripts/harness_check.sh
```

Result:

```text
passed
```
