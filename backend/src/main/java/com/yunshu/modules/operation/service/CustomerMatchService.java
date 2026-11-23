package com.yunshu.modules.operation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunshu.modules.operation.entity.BizCustomer;
import com.yunshu.modules.operation.mapper.BizCustomerMapper;
import com.yunshu.modules.operation.vo.CustomerMatchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户自动匹配（方案 3.1.4）：发货人名称精确匹配，同名时用发货电话精确消歧。
 * 数据权限由拦截器自动追加 dept_id；未命中返回空数组，绝不拦截开单。
 */
@Service
@RequiredArgsConstructor
public class CustomerMatchService {

    private final BizCustomerMapper customerMapper;

    public CustomerMatchVO match(String senderName, String senderPhone) {
        CustomerMatchVO result = new CustomerMatchVO();
        result.setCandidates(List.of());
        result.setMatched(false);
        if (!StringUtils.hasText(senderName)) {
            return result;
        }
        String name = senderName.trim();
        String phone = StringUtils.hasText(senderPhone) ? senderPhone.trim() : null;
        List<BizCustomer> byName = customerMapper.selectList(Wrappers.<BizCustomer>lambdaQuery()
                .eq(BizCustomer::getCustomerName, name)
                .eq(BizCustomer::getStatus, 1)
                .orderByAsc(BizCustomer::getId)
                .last("LIMIT 20"));
        if (byName.isEmpty()) {
            return result;
        }
        List<BizCustomer> selected = byName;
        if (phone != null) {
            List<BizCustomer> byPhone = byName.stream()
                    .filter(item -> phone.equals(item.getContactPhone()))
                    .toList();
            if (!byPhone.isEmpty()) {
                selected = byPhone;
            }
        }
        List<CustomerMatchVO.Item> items = new ArrayList<>();
        for (BizCustomer customer : selected) {
            CustomerMatchVO.Item item = new CustomerMatchVO.Item();
            item.setId(customer.getId());
            item.setCustomerName(customer.getCustomerName());
            item.setContactPhone(customer.getContactPhone());
            item.setAddress(customer.getAddress());
            items.add(item);
        }
        result.setCandidates(items);
        result.setMatched(items.size() == 1);
        return result;
    }

}
