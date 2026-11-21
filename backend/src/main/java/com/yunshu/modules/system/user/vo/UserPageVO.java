package com.yunshu.modules.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户分页结果")
public class UserPageVO {

    private List<UserVO> records;

    private Integer total;
}
