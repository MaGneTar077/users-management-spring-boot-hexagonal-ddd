package com.jcaa.usersmanagement.application.port.in;

import com.jcaa.usersmanagement.application.service.dto.command.DeleteUserFileCommand;

public interface DeleteUserFileUseCase {

  void execute(DeleteUserFileCommand command);
}
