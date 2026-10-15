package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.CustomerAddressRepo;
import com.pk.core.business.repository.OrderItemRepo;
import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.OrderTimelineRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.business.repository.UserRepo;
import com.pk.core.business.service.CustomerService;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.response.AddressResponseDto;
import com.pk.core.model.dto.response.AdminCustomerDetailResponseDto;
import com.pk.core.model.dto.response.AdminCustomerResponseDto;
import com.pk.core.model.dto.response.OrderItemResponseDto;
import com.pk.core.model.dto.response.OrderResponseDto;
import com.pk.core.model.dto.response.OrderTimelineResponseDto;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import com.pk.core.model.entity.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final UserRepo users;
    private final CustomerAddressRepo addresses;
    private final OrderRepo orders;
    private final OrderItemRepo orderItems;
    private final OrderTimelineRepo orderTimelines;
    private final PaymentRepo payments;

    @Transactional(readOnly = true)
    @Override
    public PageResponse<AdminCustomerResponseDto> search(String keyword, int page, int take) {
        List<AdminCustomerResponseDto> all = users.searchCustomers(keyword == null ? "" : keyword.trim()).stream()
                .map(u -> AdminCustomerResponseDto.from(u, orders.findByUserId(u.getId()).size()))
                .toList();
        return PageResponse.paginate(all, page, take);
    }

    @Transactional(readOnly = true)
    @Override
    public AdminCustomerDetailResponseDto findByIdForAdmin(Long customerId) {
        UserEntity user = users.findOne(customerId);
        if (user == null) {
            throw new ResourceNotFoundException("Khách hàng", "Customer", customerId);
        }
        List<AddressResponseDto> addressDtos = addresses.findByUserId(customerId).stream()
                .map(AddressResponseDto::from).toList();
        List<OrderResponseDto> orderDtos = orders.findByUserId(customerId).stream()
                .map(this::toOrderDto).toList();
        return AdminCustomerDetailResponseDto.from(user, addressDtos, orderDtos);
    }

    /** Sao chép từ OrderServiceImpl.toDto() (private, không public được để tái dùng) - cùng logic
     * gộp OrderItemResponseDto/OrderTimelineResponseDto/paymentId cho 1 đơn. */
    private OrderResponseDto toOrderDto(OrderEntity o) {
        List<OrderItemResponseDto> items = orderItems.findByOrderId(o.getId()).stream()
                .map(OrderItemResponseDto::from).toList();
        List<OrderTimelineResponseDto> timeline = orderTimelines.findByOrderId(o.getId()).stream()
                .map(OrderTimelineResponseDto::from).toList();
        PaymentEntity payment = payments.findByOrderId(o.getId());
        return OrderResponseDto.from(o, payment != null ? payment.getId() : null, items, timeline);
    }
}
