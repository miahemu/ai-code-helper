package com.eastmoney.agent.repository;

import com.eastmoney.agent.domain.KnowledgeChunk;
import com.eastmoney.agent.domain.KnowledgeDocument;
import com.eastmoney.agent.enums.DocumentTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Author: suyue
 * @name: KnowledgeRepository
 * @Date: 2026/09/14
 * @Description: 知识库文档、切片及 JSON 向量的 SQLite 持久化
 */
@Repository
public class KnowledgeRepository {

    private static final String CREATE_DOCUMENT_TABLE = """
            CREATE TABLE IF NOT EXISTS knowledge_document (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                filename TEXT,
                document_type TEXT NOT NULL,
                content TEXT NOT NULL,
                chunk_count INTEGER NOT NULL DEFAULT 0,
                create_time TEXT NOT NULL,
                update_time TEXT NOT NULL
            )
            """;

    private static final String CREATE_CHUNK_TABLE = """
            CREATE TABLE IF NOT EXISTS knowledge_chunk (
                id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                title TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                content TEXT NOT NULL,
                embedding_json TEXT NOT NULL,
                FOREIGN KEY (document_id) REFERENCES knowledge_document(id) ON DELETE CASCADE
            )
            """;

    private static final String CREATE_CHUNK_DOCUMENT_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_document_id
            ON knowledge_chunk(document_id)
            """;

    private final ObjectMapper objectMapper;

    private final String databasePath;

    public KnowledgeRepository(ObjectMapper objectMapper, @Value("${ai.storage.database-path}") String databasePath) {
        this.objectMapper = objectMapper;
        this.databasePath = databasePath;
        initializeDatabase();
    }

    /**
     * 初始化本地数据库结构；SQLite 文件不存在时会自动创建。
     */
    private void initializeDatabase() {
        Path path = Paths.get(databasePath).toAbsolutePath().normalize();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_DOCUMENT_TABLE);
                statement.executeUpdate(CREATE_CHUNK_TABLE);
                statement.executeUpdate(CREATE_CHUNK_DOCUMENT_INDEX);
            }
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("初始化知识库 SQLite 数据库失败：" + path, exception);
        }
    }

    public List<KnowledgeDocument> listDocuments() {
        String sql = "SELECT id, title, filename, document_type, content, chunk_count, "
                + "create_time, update_time FROM knowledge_document ORDER BY update_time DESC";
        List<KnowledgeDocument> result = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(readDocument(resultSet));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("查询知识库文档失败", exception);
        }
    }

    public KnowledgeDocument findDocument(String documentId) {
        String sql = "SELECT id, title, filename, document_type, content, chunk_count, "
                + "create_time, update_time FROM knowledge_document WHERE id = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? readDocument(resultSet) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("查询知识库文档失败，documentId=" + documentId, exception);
        }
    }

    public List<KnowledgeChunk> listChunks(List<String> documentIds) {
        List<String> actualDocumentIds = documentIds == null ? Collections.emptyList() : documentIds;
        StringBuilder sql = new StringBuilder("SELECT id, document_id, title, chunk_index, content, embedding_json "
                + "FROM knowledge_chunk");
        if (!actualDocumentIds.isEmpty()) {
            sql.append(" WHERE document_id IN (");
            sql.append("?, ".repeat(Math.max(0, actualDocumentIds.size() - 1)));
            sql.append("?)");
        }
        sql.append(" ORDER BY document_id, chunk_index");

        List<KnowledgeChunk> result = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < actualDocumentIds.size(); index++) {
                statement.setString(index + 1, actualDocumentIds.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(readChunk(resultSet));
                }
            }
            return result;
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("查询知识库切片失败", exception);
        }
    }

    public void saveDocument(KnowledgeDocument document) {
        try (Connection connection = getConnection()) {
            upsertDocument(connection, document);
        } catch (SQLException exception) {
            throw new IllegalStateException("保存知识库文档失败，documentId=" + document.getId(), exception);
        }
    }

    /**
     * 文档和切片在同一个事务中保存，避免只保存文档而没有保存对应向量。
     */
    public void saveDocumentAndChunks(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertDocument(connection, document);
                deleteChunks(connection, document.getId());
                insertChunks(connection, chunks);
                connection.commit();
            } catch (SQLException | IOException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("保存知识库文档和切片失败，documentId=" + document.getId(), exception);
        }
    }

    public void deleteChunks(String documentId) {
        try (Connection connection = getConnection()) {
            deleteChunks(connection, documentId);
        } catch (SQLException exception) {
            throw new IllegalStateException("删除知识库切片失败，documentId=" + documentId, exception);
        }
    }

    public void deleteDocument(String documentId) {
        String sql = "DELETE FROM knowledge_document WHERE id = ?";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, documentId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("删除知识库文档失败，documentId=" + documentId, exception);
        }
    }

    private Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private void upsertDocument(Connection connection, KnowledgeDocument document) throws SQLException {
        String sql = "INSERT INTO knowledge_document "
                + "(id, title, filename, document_type, content, chunk_count, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET title = excluded.title, filename = excluded.filename, "
                + "document_type = excluded.document_type, content = excluded.content, "
                + "chunk_count = excluded.chunk_count, update_time = excluded.update_time";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, document.getId());
            statement.setString(2, document.getTitle());
            statement.setString(3, document.getFilename());
            statement.setString(4, document.getDocumentType().name());
            statement.setString(5, document.getContent());
            statement.setInt(6, document.getChunkCount());
            statement.setString(7, document.getCreateTime().toString());
            statement.setString(8, document.getUpdateTime().toString());
            statement.executeUpdate();
        }
    }

    private void insertChunks(Connection connection, List<KnowledgeChunk> chunks) throws SQLException, IOException {
        String sql = "INSERT INTO knowledge_chunk "
                + "(id, document_id, title, chunk_index, content, embedding_json) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (KnowledgeChunk chunk : chunks) {
                statement.setString(1, chunk.getId());
                statement.setString(2, chunk.getDocumentId());
                statement.setString(3, chunk.getTitle());
                statement.setInt(4, chunk.getChunkIndex());
                statement.setString(5, chunk.getContent());
                statement.setString(6, objectMapper.writeValueAsString(chunk.getEmbedding().vectorAsList()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void deleteChunks(Connection connection, String documentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM knowledge_chunk WHERE document_id = ?")) {
            statement.setString(1, documentId);
            statement.executeUpdate();
        }
    }

    private KnowledgeDocument readDocument(ResultSet resultSet) throws SQLException {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(resultSet.getString("id"));
        document.setTitle(resultSet.getString("title"));
        document.setFilename(resultSet.getString("filename"));
        document.setDocumentType(DocumentTypeEnum.valueOf(resultSet.getString("document_type")));
        document.setContent(resultSet.getString("content"));
        document.setChunkCount(resultSet.getInt("chunk_count"));
        document.setCreateTime(LocalDateTime.parse(resultSet.getString("create_time")));
        document.setUpdateTime(LocalDateTime.parse(resultSet.getString("update_time")));
        return document;
    }

    private KnowledgeChunk readChunk(ResultSet resultSet) throws SQLException, IOException {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(resultSet.getString("id"));
        chunk.setDocumentId(resultSet.getString("document_id"));
        chunk.setTitle(resultSet.getString("title"));
        chunk.setChunkIndex(resultSet.getInt("chunk_index"));
        chunk.setContent(resultSet.getString("content"));
        float[] vector = objectMapper.readValue(resultSet.getString("embedding_json"), float[].class);
        chunk.setEmbedding(Embedding.from(vector));
        return chunk;
    }
}
