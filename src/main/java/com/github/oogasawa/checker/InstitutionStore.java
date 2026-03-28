package com.github.oogasawa.checker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

import javax.sql.DataSource;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class InstitutionStore {

    private static final Logger LOG = Logger.getLogger(InstitutionStore.class.getName());

    @ConfigProperty(name = "checker.tsv-path",
            defaultValue = "institutions_with_urls.tsv")
    String tsvPath;

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int httpPort;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url", defaultValue = "")
    String jdbcUrl;

    @Inject
    DataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        File tsvFile = new java.io.File(tsvPath);
        LOG.info("=== Institution Name Checker startup ===");
        LOG.info("Working directory : " + new java.io.File(".").getAbsolutePath());
        LOG.info("TSV path (config): " + tsvPath);
        LOG.info("TSV path (abs)   : " + tsvFile.getAbsolutePath());
        LOG.info("TSV file exists  : " + tsvFile.exists());
        if (tsvFile.exists()) {
            LOG.info("TSV file size    : " + tsvFile.length() + " bytes");
            LOG.info("TSV last modified: " + new java.util.Date(tsvFile.lastModified()));
        }
        LOG.info("JDBC URL         : " + jdbcUrl);
        LOG.info("HTTP port        : " + httpPort);
        LOG.info("  (override TSV : java -Dchecker.tsv-path=/path/to/file.tsv -jar ...)");
        LOG.info("  (override port: java -Dquarkus.http.port=<port> -jar ...)");

        initSchema();

        int existing = countAll();
        LOG.info("Existing rows in H2: " + existing);
        if (existing == 0) {
            LOG.info("H2 is empty, loading from TSV file...");
            loadFromTsv();
            LOG.info("Loaded " + countAll() + " rows from TSV into H2");
        } else {
            LOG.info("H2 already has data, skipping TSV load. Use Reload TSV button to force reload.");
        }
    }

    private void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS institutions (
                    kakenhi_code VARCHAR(10) PRIMARY KEY,
                    name_ja VARCHAR(500) NOT NULL,
                    url VARCHAR(1000) DEFAULT '',
                    name_en VARCHAR(500) DEFAULT ''
                )
                """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init schema", e);
        }
    }

    private int countAll() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM institutions")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Load TSV file into H2. Called on first start or on reload. */
    public void load() {
        File tsvFile = new java.io.File(tsvPath);
        LOG.info("=== Reload TSV requested ===");
        LOG.info("TSV path (abs)   : " + tsvFile.getAbsolutePath());
        LOG.info("TSV file exists  : " + tsvFile.exists());
        if (tsvFile.exists()) {
            LOG.info("TSV file size    : " + tsvFile.length() + " bytes");
            LOG.info("TSV last modified: " + new java.util.Date(tsvFile.lastModified()));
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM institutions");
            LOG.info("Cleared existing H2 data");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear table", e);
        }
        loadFromTsv();
        LOG.info("Reloaded " + countAll() + " rows from TSV into H2");
    }

    private void loadFromTsv() {
        String sql = "INSERT INTO institutions (kakenhi_code, name_ja, url, name_en) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             var reader = new BufferedReader(
                     new InputStreamReader(new FileInputStream(tsvPath), StandardCharsets.UTF_8))) {

            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                String[] parts = line.split("\t", -1);
                if (parts.length >= 4) {
                    ps.setString(1, parts[0].trim());
                    ps.setString(2, parts[1].trim());
                    ps.setString(3, parts[2].trim());
                    ps.setString(4, parts[3].trim());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load TSV: " + tsvPath, e);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert data", e);
        }
    }

    public List<Institution> getAll() {
        List<Institution> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT kakenhi_code, name_ja, url, name_en FROM institutions ORDER BY kakenhi_code")) {
            while (rs.next()) {
                list.add(new Institution(
                        rs.getString("kakenhi_code"),
                        rs.getString("name_ja"),
                        rs.getString("url"),
                        rs.getString("name_en")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query institutions", e);
        }
        return list;
    }

    /** Get institutions whose kakenhi_code falls in a specific 10000 range. */
    public List<Institution> getByRange(int rangeStart) {
        int rangeEnd = rangeStart + 9999;
        List<Institution> list = new ArrayList<>();
        String sql = "SELECT kakenhi_code, name_ja, url, name_en FROM institutions " +
                "WHERE CAST(kakenhi_code AS INT) BETWEEN ? AND ? ORDER BY kakenhi_code";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rangeStart);
            ps.setInt(2, rangeEnd);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Institution(
                            rs.getString("kakenhi_code"),
                            rs.getString("name_ja"),
                            rs.getString("url"),
                            rs.getString("name_en")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query by range", e);
        }
        return list;
    }

    /** Get distinct 10000-ranges that have data. */
    public List<Integer> getAvailableRanges() {
        List<Integer> ranges = new ArrayList<>();
        String sql = "SELECT DISTINCT (CAST(kakenhi_code AS INT) / 10000) * 10000 AS range_start " +
                "FROM institutions ORDER BY range_start";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ranges.add(rs.getInt("range_start"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get ranges", e);
        }
        return ranges;
    }

    public Institution findByCode(String code) {
        String sql = "SELECT kakenhi_code, name_ja, url, name_en FROM institutions WHERE kakenhi_code = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Institution(
                            rs.getString("kakenhi_code"),
                            rs.getString("name_ja"),
                            rs.getString("url"),
                            rs.getString("name_en"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find by code", e);
        }
        return null;
    }

    public void updateNameEn(String code, String newNameEn) {
        String sql = "UPDATE institutions SET name_en = ? WHERE kakenhi_code = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newNameEn);
            ps.setString(2, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update name_en", e);
        }
    }

    /** Export current DB contents back to TSV. */
    public void save() {
        List<Institution> all = getAll();
        try (var writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tsvPath), StandardCharsets.UTF_8))) {
            writer.write('\uFEFF');
            writer.write("kakenhi_code\tname_ja\turl\tname_en\n");
            for (Institution inst : all) {
                writer.write(String.join("\t",
                        inst.kakenhiCode, inst.nameJa, inst.url, inst.nameEn));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save TSV: " + tsvPath, e);
        }
    }

    public long countMissing() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM institutions WHERE name_en IS NULL OR name_en = ''")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            return 0;
        }
    }
}
