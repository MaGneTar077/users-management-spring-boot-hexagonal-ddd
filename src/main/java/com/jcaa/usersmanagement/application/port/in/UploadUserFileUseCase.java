package com.jcaa.usersmanagement.application.port.in;

import com.jcaa.usersmanagement.application.service.dto.command.UploadUserFileCommand;
import com.jcaa.usersmanagement.domain.model.UserFileModel;

public interface UploadUserFileUseCase {

  UserFileModel execute(UploadUserFileCommand command);
}
