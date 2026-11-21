package com.yunshu.modules.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "用户启用/禁用参数")
public class UserStatusRequest {

    @Schema(description = "true=启用 false=禁用")
    @NotNull(message = "请选择状态")
    private Boolean status;
}
