# institution-name-checker

A web application for reviewing and filling in the English names of Japanese research institutions (科研費機関コード対応機関名).

## What it does

- Loads institution data from a TSV file (`institutions_with_urls.tsv`)
- Displays institutions in a table, filterable by whether an English name is missing
- **Check button**: opens a headful Chromium browser with 3 tabs to help find the English name:
  - DuckDuckGo search for the Japanese name
  - The institution's website
  - DuckDuckGo search for "Japanese name English name"
- **Save button**: saves the English name back to the TSV file

## Requirements

- Java 17 or later (for the über-jar)
- A display (X11/Wayland on Linux, or macOS/Windows desktop) — required for the headful browser
- `institutions_with_urls.tsv` in the working directory (or specify with `-Dchecker.tsv-path=`)

## Running

### Download the binary

Download the latest release from the [Releases page](https://github.com/oogasawa/institution-name-checker/releases):

| File | Platform |
|------|----------|
| `institution-name-checker-linux-amd64` | Linux x86_64 (native) |
| `institution-name-checker-macos-amd64` | macOS x86_64 (native) |
| `institution-name-checker-windows-amd64.exe` | Windows x86_64 (native) |

### First run — Playwright browser installation

The Check button uses [Playwright](https://playwright.dev/java/) to open a Chromium browser.
On the first run, Playwright will automatically download the Chromium browser binary to
`~/.cache/ms-playwright/`. This requires an internet connection and takes a few minutes.

If automatic download fails, install manually:

```bash
# via npx (if Node.js is available)
npx playwright install chromium
```

### Start the application

Place `institutions_with_urls.tsv` in the current directory, then:

```bash
# Linux / macOS
chmod +x institution-name-checker-linux-amd64
./institution-name-checker-linux-amd64

# Windows
institution-name-checker-windows-amd64.exe
```

The application starts on port **8090** by default. Open `http://localhost:8090` in your browser.

On startup, the following is printed:

```
TSV path    : /path/to/institutions_with_urls.tsv
  (override : java -Dchecker.tsv-path=/path/to/file.tsv -jar ...)
HTTP port   : 8090
  (override : java -Dquarkus.http.port=9090 -jar ...)
```

### Override options

| Option | Default | Example |
|--------|---------|---------|
| `-Dchecker.tsv-path=` | `./institutions_with_urls.tsv` | `-Dchecker.tsv-path=/data/institutions.tsv` |
| `-Dquarkus.http.port=` | `8090` | `-Dquarkus.http.port=9090` |

**Note**: for native binaries, use environment variables instead of `-D` flags:

```bash
checker.tsv-path=/data/institutions.tsv ./institution-name-checker-linux-amd64
quarkus.http.port=9090 ./institution-name-checker-linux-amd64
```

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

Native binary (requires GraalVM 21+):

```bash
./mvnw package -Dnative
./target/institution-name-checker-1.0.0-runner
```

## License

Apache License 2.0
