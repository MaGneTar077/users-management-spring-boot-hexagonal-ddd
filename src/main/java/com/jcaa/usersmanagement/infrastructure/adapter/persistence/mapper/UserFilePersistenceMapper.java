package com.jcaa.usersmanagement.infrastructure.adapter.persistence.mapper;

import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.valueobject.UserId;
import com.jcaa.usersmanagement.infrastructure.adapter.persistence.entity.UserFileEntity;
import lombok.experimental.UtilityClass;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@UtilityClass
public class UserFilePersistenceMapper {

  private static final String COLUMN_ID = "id";
  private static final String COLUMN_USER_ID = "user_id";
  private static final String COLUMN_FILE_KEY = "file_key";
  private static final String COLUMN_CONTENT_TYPE = "content_type";
  private static final String COLUMN_SIZE_BYTES = "size_bytes";
  private static final String COLUMN_CREATED_AT = "created_at";

  public UserFileEntity fromResultSetToEntity(final ResultSet resultSet) throws SQLException {
    final Timestamp createdAt = resultSet.getTimestamp(COLUMN_CREATED_AT);
    return new UserFileEntity(
        resultSet.getString(COLUMN_ID),
        resultSet.getString(COLUMN_USER_ID),
        resultSet.getString(COLUMN_FILE_KEY),
        resultSet.getString(COLUMN_CONTENT_TYPE),
        resultSet.getLong(COLUMN_SIZE_BYTES),
        Objects.isNull(createdAt) ? Instant.now() : createdAt.toInstant());
  }

  public UserFileModel fromEntityToModel(final UserFileEntity entity) {
    return new UserFileModel(
        entity.id(),
        new UserId(entity.userId()),
        entity.fileKey(),
        entity.contentType(),
        entity.sizeBytes(),
        entity.createdAt());
  }

  public UserFileModel fromResultSetToModel(final ResultSet resultSet) throws SQLException {
    return fromEntityToModel(fromResultSetToEntity(resultSet));
  }

  public List<UserFileModel> fromResultSetToModelList(final ResultSet resultSet)
      throws SQLException {
    final List<UserFileModel> files = new ArrayList<>();
    while (resultSet.next()) {
      files.add(fromResultSetToModel(resultSet));
    }
    return files;
  }
}
