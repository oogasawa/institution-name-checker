# institution-name-checker

Japanese research institutions (科研費申請機関) have official codes (機関番号) and Japanese names, but many lack official English names. This tool helps a human reviewer look up and fill in the English names one by one.

## How it works

### Data flow

```
TSV file  ──(initial load)──>  H2 database  ──(display)──>  Web UI
                                    │
                                    ├──(Save button)──>  Update H2 + write back to TSV
                                    │
TSV file  <──(Reload TSV)─────  H2 database
```

1. On first startup, the app reads `institutions_with_urls.tsv` and loads all rows into an embedded H2 database (file: `./data/checker.mv.db`).
2. On subsequent startups, the app uses the existing H2 data and **does not re-read the TSV file**. This preserves edits you made in previous sessions.
3. The web UI displays institutions grouped by 10000-range (10000番台, 20000番台, ...).

### Buttons

- **Check** — Opens a new browser window with 3 tabs (server launches Chrome/Edge/Firefox via `--new-window`):
  1. DuckDuckGo search for the Japanese name
  2. The institution's URL (if known)
  3. DuckDuckGo search for "Japanese name English name"
- **Save** — Writes the edited English name to H2, then exports the entire H2 contents back to the TSV file. The TSV file is always kept in sync with H2.
- **Reload TSV** — Deletes all H2 data and re-reads from the TSV file. Use this when you have replaced the TSV file with a new version.
- **Filter buttons** (All / Missing EN / Has EN) — Filter the displayed rows.

### H2 database behavior

- H2 data file: `./data/checker.mv.db` (created in the working directory)
- If `./data/checker.mv.db` already exists and contains data, the TSV file is **not** read on startup.
- To force a fresh start, either:
  - Click the **Reload TSV** button in the web UI, or
  - Delete `./data/` directory before starting the app.

## Requirements

- Java 21 or later (GraalVM recommended)
- Chrome, Edge, or Firefox installed (for the Check button)
- `institutions_with_urls.tsv` in the working directory (or specify path with `-Dchecker.tsv-path=`)

## Installing Java on Windows (PowerShell)

### 1. Install Git (includes Git Bash)

```powershell
winget install Git.Git
```

After installation, restart PowerShell.

### 2. Install Scoop

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
Invoke-RestMethod -Uri https://get.scoop.sh | Invoke-Expression
```

### 3. Add the Java bucket and install GraalVM

```powershell
scoop bucket add java
scoop install java/graalvm-oracle-21jdk
```

### 4. Verify

```powershell
java -version
```

`oracle-graalvm 21` と表示されればOKです。

## Running

### Using the uber-jar (recommended for all platforms)

Download `institution-name-checker-1.0.0-runner.jar` from the [Releases page](https://github.com/oogasawa/institution-name-checker/releases).

Place `institutions_with_urls.tsv` in the same directory as the jar, then:

```bash
java -jar institution-name-checker-1.0.0-runner.jar
```

The application starts on port **8090**. Open `http://localhost:8090` in your browser.

### Using native binaries

Download from the [Releases page](https://github.com/oogasawa/institution-name-checker/releases):

| File | Platform |
|------|----------|
| `institution-name-checker-1.0.0-runner.jar` | All platforms (requires Java 21+) |
| `institution-name-checker-linux-amd64` | Linux x86_64 (native) |
| `institution-name-checker-macos-arm64` | macOS Apple Silicon (native) |
| `institution-name-checker-windows-amd64.exe` | Windows x86_64 (native) |

```bash
# Linux
chmod +x institution-name-checker-linux-amd64
./institution-name-checker-linux-amd64

# macOS (Apple Silicon)
chmod +x institution-name-checker-macos-arm64
./institution-name-checker-macos-arm64

# Windows (PowerShell)
.\institution-name-checker-windows-amd64.exe
```

### Startup log

On startup, the console shows:

```
Working directory : C:\Users\you\checker
TSV path (config): institutions_with_urls.tsv
TSV path (abs)   : C:\Users\you\checker\institutions_with_urls.tsv
TSV file exists  : true
TSV file size    : 234567 bytes
TSV last modified: Fri Mar 28 12:00:00 JST 2026
JDBC URL         : jdbc:h2:file:./data/checker;...
HTTP port        : 8090
Existing rows in H2: 0
H2 is empty, loading from TSV file...
Loaded 1837 rows from TSV into H2
```

If H2 already has data:

```
Existing rows in H2: 1837
H2 already has data, skipping TSV load. Use Reload TSV button to force reload.
```

### Override options

| Option | Default | Example |
|--------|---------|---------|
| `-Dchecker.tsv-path=` | `institutions_with_urls.tsv` | `-Dchecker.tsv-path=C:\data\institutions.tsv` |
| `-Dquarkus.http.port=` | `8090` | `-Dquarkus.http.port=9090` |

### TSV file format

Tab-separated, UTF-8 (BOM optional), with a header row:

```
kakenhi_code	name_ja	url	name_en
10101	東京大学	https://www.u-tokyo.ac.jp	The University of Tokyo
...
```

## Building from source

```bash
git clone https://github.com/oogasawa/institution-name-checker.git
cd institution-name-checker
./mvnw package
java -jar target/institution-name-checker-1.0.0-runner.jar
```

## License

Apache License 2.0
