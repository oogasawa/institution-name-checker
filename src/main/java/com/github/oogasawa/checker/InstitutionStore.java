package com.github.oogasawa.checker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import javax.sql.DataSource;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class InstitutionStore {

    @ConfigProperty(name = "checker.tsv-path",
            defaultValue = "institutions_with_urls.tsv")
    String tsvPath;

    @Inject
    DataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        System.out.println("TSV path: " + new java.io.File(tsvPath).getAbsolutePath());
        System.out.println("  (override: java -Dchecker.tsv-path=/path/to/file.tsv -jar ...)");
        initSchema();
        if (countAll() == 0) {
            loadFromTsv();
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
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM institutions");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear table", e);
        }
        loadFromTsv();
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
