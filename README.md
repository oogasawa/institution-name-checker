# institution-name-checker

A tool for reviewing and filling in the English names of Japanese research institutions (KAKENHI institution codes). A human reviewer edits English names in a web UI while looking up each institution via DuckDuckGo search.

## 1. Windows (PowerShell)

All commands in this section are intended to be run in **Windows PowerShell**. Open it from the Start menu by searching for "PowerShell".

### Prerequisites

- Java 21 or later
- Chrome, Edge, or Firefox
- A TSV data file (`institutions_with_urls.tsv`)

#### Installing Java via Scoop

Install Git (includes Git Bash):

```powershell
winget install Git.Git
```

Restart PowerShell, then install Scoop:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
Invoke-RestMethod -Uri https://get.scoop.sh | Invoke-Expression
```

Install GraalVM 21:

```powershell
scoop bucket add java
scoop install java/graalvm-oracle-21jdk
```

Verify:

```powershell
java -version
```

### Using the uber-jar

Download `institution-name-checker-1.0.0-runner.jar` from the [Releases page](https://github.com/oogasawa/institution-name-checker/releases).

#### Starting the app

The data file `institutions_with_urls.tsv` is included in this repository. Place it in the same directory as the jar, then run:

```powershell
java -jar institution-name-checker-1.0.0-runner.jar
```

To use a TSV file with a different name or in a different location, specify it with `-Dchecker.tsv-path`:

```powershell
java -Dchecker.tsv-path=C:\data\my_institutions.tsv -jar institution-name-checker-1.0.0-runner.jar
```

To change the HTTP port (default 8090):

```powershell
java -Dquarkus.http.port=9090 -jar institution-name-checker-1.0.0-runner.jar
```

Open `http://localhost:8090` in your browser.

#### Loading data

The TSV file name can be anything, as long as it follows the required format (see [TSV file format](#tsv-file-format)). The default name is `institutions_with_urls.tsv`.

On **first startup** (or when the H2 database is empty), the app reads the TSV file and loads all rows into an embedded H2 database stored at `./data/checker.mv.db` relative to the working directory. If the TSV file does not exist and the H2 database is also empty, the app starts with an empty table.

On **subsequent startups**, the app uses the existing H2 data and does **not** re-read the TSV file. This preserves edits you made in previous sessions.

To **reload** from a new or updated TSV file, click the **Load TSV** button in the web UI. This deletes all H2 data and re-reads the TSV file. You can also force a reload by deleting the `./data/` directory before starting the app.

**Warning**: If you click **Load TSV** when the TSV file does not exist at the configured path, the app will show an error. Check the startup log in the console to confirm the resolved file path.

#### Saving data

Edits you make in the Name (EN) column are auto-saved to the H2 database whenever the cursor leaves the edit field (the border flashes green to confirm). These edits persist across restarts because H2 stores data on disk.

To **export** the current state back to the TSV file, click the **Save TSV** button. This overwrites the TSV file at the configured path with all current H2 data.

### Using the native binary

Download `institution-name-checker-windows-amd64.exe` from the [Releases page](https://github.com/oogasawa/institution-name-checker/releases).

Place your TSV file in the same directory, then run:

```powershell
.\institution-name-checker-windows-amd64.exe
```

No Java installation is required for the native binary. The same loading/saving behavior described above applies.

## 2. Linux

### Prerequisites

- Java 21 or later
- Chrome or Chromium
- A TSV data file (`institutions_with_urls.tsv`)

### Using the uber-jar

Download `institution-name-checker-1.0.0-runner.jar` from the [Releases page](https://github.com/oogasawa/institution-name-checker/releases).

Place `institutions_with_urls.tsv` in the same directory as the jar, then run:

```bash
java -jar institution-name-checker-1.0.0-runner.jar
```

Open `http://localhost:8090` in your browser.

To specify a different TSV file or port:

```bash
java -Dchecker.tsv-path=/data/institutions.tsv -Dquarkus.http.port=9090 -jar institution-name-checker-1.0.0-runner.jar
```

#### Loading data

On first startup, the app reads the TSV file and loads all rows into an embedded H2 database (`./data/checker.mv.db`). On subsequent startups, it uses the existing H2 data without re-reading the TSV.

To reload from a new or updated TSV file, click the **Load TSV** button in the web UI, or delete the `./data/` directory before starting the app.

#### Saving data

Edits are auto-saved to the H2 database whenever the cursor leaves an edit field. To export the current state back to the TSV file, click the **Save TSV** button.

### Using the native binary

Download `institution-name-checker-linux-amd64` from the [Releases page](https://github.com/oogasawa/institution-name-checker/releases).

```bash
chmod +x institution-name-checker-linux-amd64
./institution-name-checker-linux-amd64
```

No Java installation is required for the native binary. Place `institutions_with_urls.tsv` in the working directory.

## 3. macOS (Apple Silicon)

### Prerequisites

- Java 21 or later
- Chrome or Safari
- A TSV data file (`institutions_with_urls.tsv`)

### Using the native binary

Download `institution-name-checker-macos-arm64` from the [Releases page](https://github.com/oogasawa/institution-name-checker/releases).

```bash
chmod +x institution-name-checker-macos-arm64
./institution-name-checker-macos-arm64
```

The uber-jar also works on macOS with Java 21 installed.

## 4. How it works

### Data flow

```
TSV file  ──(Load TSV)──>  H2 database  ──(display)──>  Web UI
                                │
                                ├──(auto-save on blur)──>  Update H2
                                │
TSV file  <──(Save TSV)──  H2 database
```

1. On first startup, the app reads `institutions_with_urls.tsv` into an embedded H2 database (file: `./data/checker.mv.db`).
2. On subsequent startups, the app uses existing H2 data and does not re-read the TSV file. This preserves edits from previous sessions.
3. The web UI groups institutions by 10000-range (10000, 20000, ...).

### UI controls

- **Check** button — Opens a new browser window with 3 tabs via the server launching Chrome/Edge/Firefox with `--new-window`:
  1. DuckDuckGo search for the Japanese name
  2. The institution's URL (if known)
  3. DuckDuckGo search for "Japanese name English name"
- **Name (EN)** field — Editable. Changes are auto-saved to H2 when the field loses focus (border flashes green on success).
- **Load TSV** button — Clears H2 and re-reads the TSV file. Use after replacing the TSV with a new version.
- **Save TSV** button — Exports the current H2 contents back to the TSV file.
- **Filter buttons** (All / Missing EN / Has EN) — Filter displayed rows.

### TSV file format

Tab-separated, UTF-8 (BOM optional), with a header row:

```
kakenhi_code	name_ja	url	name_en
10101	...	https://www.u-tokyo.ac.jp	The University of Tokyo
```

### Options

| Option | Default | Example |
|--------|---------|---------|
| `-Dchecker.tsv-path=` | `institutions_with_urls.tsv` | `-Dchecker.tsv-path=/data/inst.tsv` |
| `-Dquarkus.http.port=` | `8090` | `-Dquarkus.http.port=9090` |

## Building from source

```bash
git clone https://github.com/oogasawa/institution-name-checker.git
cd institution-name-checker
./mvnw package
java -jar target/institution-name-checker-1.0.0-runner.jar
```

## License

Apache License 2.0
