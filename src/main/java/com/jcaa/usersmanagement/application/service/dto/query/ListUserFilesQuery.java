package com.jcaa.usersmanagement.application.service.dto.query;

import com.jcaa.usersmanagement.application.service.dto.RequesterContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ListUserFilesQuery(
    @NotBlank(message = "userId must not be blank") String userId,
    @Valid @NotNull(message = "requester must not be null") RequesterContext requester) {}
