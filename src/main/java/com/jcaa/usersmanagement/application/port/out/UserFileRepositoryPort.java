package com.jcaa.usersmanagement.application.port.out;

import com.jcaa.usersmanagement.domain.model.UserFileModel;
import com.jcaa.usersmanagement.domain.valueobject.UserId;

import java.util.List;
import java.util.Optional;

/** Puerto de salida de la tabla {@code user_files}. */
public interface UserFileRepositoryPort {

  UserFileModel save(UserFileModel userFile);

  List<UserFileModel> findByUserId(UserId userId);

  Optional<UserFileModel> findById(String fileId);

  void deleteById(String fileId);
}
