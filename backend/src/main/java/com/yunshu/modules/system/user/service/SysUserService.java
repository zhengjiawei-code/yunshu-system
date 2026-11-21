package com.yunshu.modules.system.user.service;

import com.yunshu.modules.system.user.dto.UserQuery;
import com.yunshu.modules.system.user.dto.UserSaveRequest;
import com.yunshu.modules.system.user.vo.UserPageVO;
import com.yunshu.modules.system.user.vo.UserPermVO;
import com.yunshu.modules.system.user.vo.UserVO;

import java.util.List;

public interface SysUserService {

    UserPageVO page(UserQuery query);

    UserVO detail(Long id);

    Long create(UserSaveRequest request, Long currentUserId);

    void update(Long id, UserSaveRequest request, Long currentUserId);

    void delete(Long id, Long currentUserId);

    void changeStatus(Long id, Boolean status, Long currentUserId);

    void resetPassword(Long id, Long currentUserId);

    UserPermVO perms(Long id);

    void savePerms(Long id, List<Long> grantedIds, List<Long> deniedIds, Long currentUserId);
}
