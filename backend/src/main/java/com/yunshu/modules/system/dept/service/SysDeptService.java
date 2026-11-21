package com.yunshu.modules.system.dept.service;

import com.yunshu.modules.system.dept.dto.DeptQuery;
import com.yunshu.modules.system.dept.dto.DeptSaveRequest;
import com.yunshu.modules.system.dept.vo.DeptPageVO;
import com.yunshu.modules.system.dept.vo.DeptVO;

import java.util.List;

public interface SysDeptService {

    List<DeptVO> tree();

    /** 分页查询某机构下的全部后代（平铺，服务端筛选与分页） */
    DeptPageVO page(DeptQuery query);

    DeptVO detail(Long id);

    Long create(DeptSaveRequest request);

    void update(Long id, DeptSaveRequest request);

    void delete(Long id);

    /** 精简机构树，供用户/角色等选择器使用；type 为空时返回全部启用机构 */
    List<DeptVO> options(Integer type);

    /** 同级排序，二期拖拽预留 */
    void sort(List<Long> ids);
}
