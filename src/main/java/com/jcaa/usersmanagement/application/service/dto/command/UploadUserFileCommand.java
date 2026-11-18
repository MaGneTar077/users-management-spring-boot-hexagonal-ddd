package com.jcaa.usersmanagement.application.service.dto.command;

import com.jcaa.usersmanagement.application.port.out.dto.FileUploadData;
import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadUserFileCommand(
    @NotBlank(message = "userId must not be blank") String userId,
    @NotNull(message = "file must not be null") FileUploadData file,
    @Valid @NotNull(message = "requester must not be null") RequesterContext requester) {}
