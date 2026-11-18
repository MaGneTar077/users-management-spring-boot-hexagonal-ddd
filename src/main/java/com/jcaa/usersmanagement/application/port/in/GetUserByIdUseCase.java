package com.jcaa.usersmanagement.application.port.in;

import com.jcaa.usersmanagement.application.service.dto.query.GetUserByIdQuery;
import com.jcaa.usersmanagement.application.service.dto.view.UserView;

public interface GetUserByIdUseCase {

  /**
   * Devuelve la proyeccion de lectura del usuario.
   *
   * <p>No devuelve {@code UserModel} porque esta lectura se sirve desde cache y en la cache nunca
   * entra la contraseña.
   */
  UserView execute(GetUserByIdQuery query);
}
