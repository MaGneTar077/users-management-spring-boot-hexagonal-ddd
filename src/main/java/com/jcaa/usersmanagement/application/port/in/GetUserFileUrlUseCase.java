package com.jcaa.usersmanagement.application.port.in;

import com.jcaa.usersmanagement.application.service.dto.query.GetUserFileUrlQuery;

public interface GetUserFileUrlUseCase {

  /** Devuelve la URL firmada de descarga del archivo. */
  String execute(GetUserFileUrlQuery query);
}
