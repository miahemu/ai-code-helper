package com.eastmoney.agent.repository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
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
 * @name: SqliteChatMemoryStore
 * @Date: 2026/09/14
 * @Description: LangChain4j 会话消息的 SQLite 持久化
 */
@Repository
public class SqliteChatMemoryStore implements ChatMemoryStore {

    private static final String CREATE_CONVERSATION_TABLE = """
            CREATE TABLE IF NOT EXISTS conversation (
                conversation_id TEXT PRIMARY KEY,
                created_time TEXT NOT NULL,
                updated_time TEXT NOT NULL
            )
            """;

    private static final String CREATE_MESSAGE_TABLE = """
            CREATE TABLE IF NOT EXISTS conversation_message (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id TEXT NOT NULL,
                message_index INTEGER NOT NULL,
                message_json TEXT NOT NULL,
                created_time TEXT NOT NULL,
                FOREIGN KEY (conversation_id) REFERENCES conversation(conversation_id) ON DELETE CASCADE,
                UNIQUE (conversation_id, message_index)
            )
            """;

    private static final String CREATE_MESSAGE_CONVERSATION_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_conversation_message_conversation_id
            ON conversation_message(conversation_id)
            """;

    private final String databasePath;

    public SqliteChatMemoryStore(@Value("${ai.storage.database-path}") String databasePath) {
        this.databasePath = databasePath;
        initializeDatabase();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String conversationId = requireConversationId(memoryId);
        String sql = "SELECT message_json FROM conversation_message "
                + "WHERE conversation_id = ? ORDER BY message_index";
        List<ChatMessage> result = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(ChatMessageDeserializer.messageFromJson(resultSet.getString("message_json")));
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("查询会话消息失败，conversationId=" + conversationId, exception);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String conversationId = requireConversationId(memoryId);
        List<ChatMessage> actualMessages = messages == null ? Collections.emptyList() : messages;
        LocalDateTime now = LocalDateTime.now();
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertConversation(connection, conversationId, now);
                deleteMessages(connection, conversationId);
                insertMessages(connection, conversationId, actualMessages, now);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("保存会话消息失败，conversationId=" + conversationId, exception);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String conversationId = requireConversationId(memoryId);
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM conversation WHERE conversation_id = ?")) {
            statement.setString(1, conversationId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("删除会话消息失败，conversationId=" + conversationId, exception);
        }
    }

    private void initializeDatabase() {
        Path path = Paths.get(databasePath).toAbsolutePath().normalize();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_CONVERSATION_TABLE);
                statement.executeUpdate(CREATE_MESSAGE_TABLE);
                statement.executeUpdate(CREATE_MESSAGE_CONVERSATION_INDEX);
            }
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("初始化会话 SQLite 数据库失败：" + path, exception);
        }
    }

    private Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private void upsertConversation(Connection connection, String conversationId, LocalDateTime now)
            throws SQLException {
        String sql = "INSERT INTO conversation (conversation_id, created_time, updated_time) VALUES (?, ?, ?) "
                + "ON CONFLICT(conversation_id) DO UPDATE SET updated_time = excluded.updated_time";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, conversationId);
            statement.setString(2, now.toString());
            statement.setString(3, now.toString());
            statement.executeUpdate();
        }
    }

    private void insertMessages(Connection connection, String conversationId, List<ChatMessage> messages,
                                LocalDateTime createdTime) throws SQLException {
        String sql = "INSERT INTO conversation_message "
                + "(conversation_id, message_index, message_json, created_time) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < messages.size(); index++) {
                statement.setString(1, conversationId);
                statement.setInt(2, index);
                statement.setString(3, ChatMessageSerializer.messageToJson(messages.get(index)));
                statement.setString(4, createdTime.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void deleteMessages(Connection connection, String conversationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM conversation_message WHERE conversation_id = ?")) {
            statement.setString(1, conversationId);
            statement.executeUpdate();
        }
    }

    private String requireConversationId(Object memoryId) {
        if (memoryId == null || memoryId.toString().isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        return memoryId.toString();
    }
}
