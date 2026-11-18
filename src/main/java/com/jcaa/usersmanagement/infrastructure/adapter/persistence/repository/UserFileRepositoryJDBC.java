package com.jcaa.usersmanagement.infrastructure.adapter.persistence.repository;

import com.jcaa.usersmanagement.application.port.out.UserFileRepositoryPort;
import com.jcaa.usersmanagement.domain.exception.UserFileNotFoundException;
import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.exception.PersistenceException;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.mapper.UserFilePersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Repositorio JDBC crudo de la tabla {@code user_files} (sin JPA, igual que el de usuarios). */
@Repository
@RequiredArgsConstructor
public class UserFileRepositoryJDBC implements UserFileRepositoryPort {

  private static final String SQL_INSERT =
      "INSERT INTO user_files "
          + "(id, user_id, file_key, content_type, size_bytes, created_at) "
          + "VALUES (?, ?, ?, ?, ?, NOW())";

  private static final String SQL_SELECT_BY_ID =
      "SELECT id, user_id, file_key, content_type, size_bytes, created_at "
          + "FROM user_files "
          + "WHERE id = ? LIMIT 1";

  private static final String SQL_SELECT_BY_USER_ID =
      "SELECT id, user_id, file_key, content_type, size_bytes, created_at "
          + "FROM user_files "
          + "WHERE user_id = ? "
          + "ORDER BY created_at DESC, id ASC";

  private static final String SQL_DELETE_BY_ID = "DELETE FROM user_files WHERE id = ?";

  private final DataSource dataSource;

  @Override
  public UserFileModel save(final UserFileModel userFile) {
    executeInsert(userFile);
    return findById(userFile.id())
        .orElseThrow(() -> UserFileNotFoundException.becauseIdWasNotFound(userFile.id()));
  }

  @Override
  public List<UserFileModel> findByUserId(final UserId userId) {
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement statement = connection.prepareStatement(SQL_SELECT_BY_USER_ID)) {
      statement.setString(1, userId.value());
      final ResultSet resultSet = statement.executeQuery();
      return UserFilePersistenceMapper.fromResultSetToModelList(resultSet);
    } catch (final SQLException exception) {
      throw PersistenceException.becauseFileFindByUserIdFailed(userId.value(), exception);
    }
  }

  @Override
  public Optional<UserFileModel> findById(final String fileId) {
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement statement = connection.prepareStatement(SQL_SELECT_BY_ID)) {
      statement.setString(1, fileId);
      final ResultSet resultSet = statement.executeQuery();
      if (!resultSet.next()) {
        return Optional.empty();
      }
      return Optional.of(UserFilePersistenceMapper.fromResultSetToModel(resultSet));
    } catch (final SQLException exception) {
      throw PersistenceException.becauseFileFindByIdFailed(fileId, exception);
    }
  }

  @Override
  public void deleteById(final String fileId) {
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement statement = connection.prepareStatement(SQL_DELETE_BY_ID)) {
      statement.setString(1, fileId);
      statement.executeUpdate();
    } catch (final SQLException exception) {
      throw PersistenceException.becauseFileDeleteFailed(fileId, exception);
    }
  }

  private void executeInsert(final UserFileModel userFile) {
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement statement = connection.prepareStatement(SQL_INSERT)) {
      statement.setString(1, userFile.id());
      statement.setString(2, userFile.userId().value());
      statement.setString(3, userFile.fileKey());
      statement.setString(4, userFile.contentType());
      statement.setLong(5, userFile.sizeBytes());
      statement.executeUpdate();
    } catch (final SQLException exception) {
      throw PersistenceException.becauseFileSaveFailed(userFile.id(), exception);
    }
  }
}
