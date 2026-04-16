package com.pch.file.dto;

import com.pch.file.domain.OwnerType;
import jakarta.validation.constraints.NotNull;

public record AttachmentOwnerRequest(
        @NotNull OwnerType ownerType,
        @NotNull Long ownerId
) {}
