package com.jcaa.usersmanagement.application.service.dto.command;

import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeleteUserFileCommand(
    @NotBlank(message = "userId must not be blank") String userId,
    @NotBlank(message = "fileId must not be blank") String fileId,
    @Valid @NotNull(message = "requester must not be null") RequesterContext requester) {}
