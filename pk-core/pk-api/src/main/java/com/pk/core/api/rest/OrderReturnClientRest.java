package com.pk.core.api.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.service.FileStorageService;
import com.pk.core.business.service.OrderReturnService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.request.OmsRequestDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * API khách — tạo/xem/huỷ yêu cầu đổi trả (khớp FE order/order-returns/client).
 * Hỗ trợ multipart (metadata + files) và JSON thuần.
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class OrderReturnClientRest extends AbstractRest {

    private static final String B = UrlConstant.OrderReturnClient.BASE;
    private final OrderReturnService returns;
    private final FileStorageService files;
    private final ObjectMapper mapper;

    private BaseRes run(Supplier<Object> action, HttpServletRequest rq, HttpServletResponse rs) {
        try {
            return restSuccessHandle.handleSuccess(action.get());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, rq, rs);
        }
    }

    @PostMapping(value = B, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createMultipart(@AuthenticationPrincipal SecurityUser user,
                                   @RequestParam String orderId,
                                   @RequestParam String type,
                                   @RequestParam String reason,
                                   @RequestParam String returnItems,
                                   @RequestParam(required = false, defaultValue = "[]") String exchangeItems,
                                   @RequestParam(required = false) String shippingAddress,
                                   @RequestParam(required = false) String note,
                                   @RequestParam(required = false) String refundBankAccountHolder,
                                   @RequestParam(required = false) String refundBankAccountNumber,
                                   @RequestParam(required = false) String refundBankName,
                                   @RequestParam(required = false) String refundBankBranch,
                                   @RequestPart(value = "files", required = false) List<MultipartFile> evidence,
                                   HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> {
            OmsRequestDtos.OrderReturn body = parseMultipart(orderId, type, reason, returnItems, exchangeItems,
                    shippingAddress, note, refundBankAccountHolder, refundBankAccountNumber, refundBankName,
                    refundBankBranch, evidence);
            return returns.createForCustomer(body, user.getId());
        }, rq, rs);
    }

    @PostMapping(value = B, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createJson(@AuthenticationPrincipal SecurityUser user,
                              @org.springframework.web.bind.annotation.RequestBody OmsRequestDtos.OrderReturn body,
                              HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> returns.createForCustomer(body, user.getId()), rq, rs);
    }

    @GetMapping(B + "/{id}")
    public BaseRes detail(@AuthenticationPrincipal SecurityUser user, @PathVariable String id,
                          HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> returns.detailForCustomer(id, user.getId()), rq, rs);
    }

    @GetMapping(B + "/by-code/{returnCode}")
    public BaseRes byCode(@AuthenticationPrincipal SecurityUser user, @PathVariable String returnCode,
                          HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> returns.detailForCustomer(returnCode, user.getId()), rq, rs);
    }

    @PutMapping(B + "/by-code/{returnCode}/cancel")
    public BaseRes cancel(@AuthenticationPrincipal SecurityUser user, @PathVariable String returnCode,
                          HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> returns.cancelForCustomer(returnCode, user.getId()), rq, rs);
    }

    private OmsRequestDtos.OrderReturn parseMultipart(String orderId, String type, String reason,
                                                      String returnItemsJson, String exchangeItemsJson,
                                                      String shippingAddressJson, String note,
                                                      String bankHolder, String bankNumber, String bankName,
                                                      String bankBranch, List<MultipartFile> evidence) {
        try {
            OmsRequestDtos.OrderReturn body = new OmsRequestDtos.OrderReturn();
            body.setOrderId(orderId);
            body.setType(type);
            body.setReason(reason);
            body.setNote(note);
            body.setRefundBankAccountHolder(bankHolder);
            body.setRefundBankAccountNumber(bankNumber);
            body.setRefundBankName(bankName);
            body.setRefundBankBranch(bankBranch);
            body.setReturnItems(mapper.readValue(returnItemsJson,
                    new TypeReference<List<OmsRequestDtos.OrderReturn.ReturnItem>>() {}));
            body.setExchangeItems(mapper.readValue(exchangeItemsJson == null ? "[]" : exchangeItemsJson,
                    new TypeReference<List<OmsRequestDtos.OrderReturn.ExchangeItem>>() {}));
            if (shippingAddressJson != null && !shippingAddressJson.isBlank()) {
                body.setShippingAddress(mapper.readValue(shippingAddressJson, new TypeReference<Map<String, Object>>() {}));
            }
            List<String> urls = new ArrayList<>();
            if (evidence != null) {
                for (MultipartFile file : evidence) {
                    if (file == null || file.isEmpty()) {
                        continue;
                    }
                    urls.add(files.store(file).getUrl());
                }
            }
            if (!urls.isEmpty()) {
                body.setEvidenceImageUrls(urls);
            }
            return body;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Payload đổi trả không hợp lệ");
        }
    }
}
