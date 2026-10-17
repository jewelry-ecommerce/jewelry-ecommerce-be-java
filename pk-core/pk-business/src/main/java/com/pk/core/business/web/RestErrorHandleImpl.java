package com.pk.core.business.web;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.web.BaseRes;
import com.pk.core.common.web.ErrorDetailRes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;

/**
 * Chuẩn hoá mọi lỗi thành BaseRes, dùng chung cho try/catch trong Rest controller (qua
 * AbstractRest) lẫn GlobalExceptionHandler (lỗi framework xảy ra ngoài try/catch, VD
 * MethodArgumentNotValidException trước khi vào thân method). Tái dùng BusinessException/ErrorCode/
 * MessageSource sẵn có của dự án (không dựng lại ExceptionCode/DetailException như bản gốc
 * boxchatSocket) vì hệ thống i18n hiện tại đã đầy đủ hơn.
 */
@Service
@RequiredArgsConstructor
public class RestErrorHandleImpl implements RestErrorHandle {

    private static final Logger log = LoggerFactory.getLogger(RestErrorHandleImpl.class);

    private final MessageSource messageSource;

    private String resolve(String code, Object[] args, String defaultMessage) {
        return messageSource.getMessage(code, args, defaultMessage, LocaleContextHolder.getLocale());
    }

    private static String titleFor(int status) {
        return switch (status) {
            case 400 -> "Dữ liệu không hợp lệ";
            case 401 -> "Chưa xác thực";
            case 403 -> "Không đủ quyền";
            case 404 -> "Không tìm thấy";
            case 409 -> "Xung đột dữ liệu";
            default -> status >= 500 ? "Lỗi hệ thống" : "Lỗi nghiệp vụ";
        };
    }

    private static void setStatus(HttpServletResponse response, int status) {
        if (response != null) {
            response.setStatus(status);
        }
    }

    @Override
    public BaseRes handleException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        if (ex instanceof BusinessException be) {
            String message = resolve(be.getCode(), be.getArgs(), be.getMessage());
            BaseRes res = new BaseRes(be.getStatus(), message, titleFor(be.getStatus()));
            ErrorDetailRes detail = new ErrorDetailRes();
            detail.setCode(be.getCode());
            detail.setMessage(message);
            res.addError(detail);
            res.setDependencies(be.getDependencies());
            setStatus(response, be.getStatus());
            return res;
        }
        if (ex instanceof AccessDeniedException) {
            String message = resolve(ErrorCode.FORBIDDEN, null, "Bạn không có quyền thực hiện thao tác này");
            BaseRes res = new BaseRes(403, message, titleFor(403));
            ErrorDetailRes detail = new ErrorDetailRes();
            detail.setCode(ErrorCode.FORBIDDEN);
            detail.setMessage(message);
            res.addError(detail);
            setStatus(response, 403);
            return res;
        }
        if (ex instanceof AuthenticationException) {
            String message = resolve(ErrorCode.UNAUTHORIZED, null, "Cần đăng nhập hoặc token không hợp lệ/đã hết hạn");
            BaseRes res = new BaseRes(401, message, titleFor(401));
            ErrorDetailRes detail = new ErrorDetailRes();
            detail.setCode(ErrorCode.UNAUTHORIZED);
            detail.setMessage(message);
            res.addError(detail);
            setStatus(response, 401);
            return res;
        }
        if (ex instanceof OptimisticLockingFailureException) {
            String message = resolve(ErrorCode.CONCURRENT_UPDATE, null,
                    "Dữ liệu vừa được người khác cập nhật, vui lòng thử lại");
            BaseRes res = new BaseRes(409, message, titleFor(409));
            ErrorDetailRes detail = new ErrorDetailRes();
            detail.setCode(ErrorCode.CONCURRENT_UPDATE);
            detail.setMessage(message);
            res.addError(detail);
            setStatus(response, 409);
            return res;
        }
        if (ex instanceof DataIntegrityViolationException dive) {
            log.warn("Vi phạm ràng buộc dữ liệu: {}", dive.getMostSpecificCause().getMessage());
            String message = resolve(ErrorCode.DATA_CONFLICT, null,
                    "Dữ liệu vi phạm ràng buộc (trùng lặp hoặc tham chiếu không hợp lệ)");
            BaseRes res = new BaseRes(409, message, titleFor(409));
            ErrorDetailRes detail = new ErrorDetailRes();
            detail.setCode(ErrorCode.DATA_CONFLICT);
            detail.setMessage(message);
            res.addError(detail);
            setStatus(response, 409);
            return res;
        }
        log.error("Lỗi không lường trước", ex);
        String message = resolve(ErrorCode.INTERNAL_ERROR, null, "Lỗi hệ thống, vui lòng thử lại sau");
        BaseRes res = new BaseRes(HttpStatus.INTERNAL_SERVER_ERROR.value(), message, titleFor(500));
        ErrorDetailRes detail = new ErrorDetailRes();
        detail.setCode(ErrorCode.INTERNAL_ERROR);
        detail.setMessage(message);
        res.addError(detail);
        setStatus(response, 500);
        return res;
    }

    @Override
    public BaseRes handleValidation(BindingResult bindingResult, HttpServletRequest request, HttpServletResponse response) {
        String message = resolve(ErrorCode.VALIDATION_FAILED, null, "Dữ liệu không hợp lệ");
        BaseRes res = new BaseRes(400, message, titleFor(400));
        List<ErrorDetailRes> details = new ArrayList<>();
        for (FieldError fe : bindingResult.getFieldErrors()) {
            ErrorDetailRes detail = new ErrorDetailRes();
            detail.setCode(fe.getCode());
            detail.setMessage(fe.getDefaultMessage());
            detail.setObjectName(fe.getObjectName());
            detail.setField(fe.getField());
            detail.setRejectValue(fe.getRejectedValue());
            details.add(detail);
        }
        res.setErrors(details);
        setStatus(response, 400);
        return res;
    }

    @Override
    public BaseRes handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request, HttpServletResponse response) {
        String message = resolve(ErrorCode.VALIDATION_FAILED, null, "Dữ liệu không hợp lệ");
        BaseRes res = new BaseRes(400, message, titleFor(400));
        List<ErrorDetailRes> details = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            ErrorDetailRes detail = new ErrorDetailRes();
            detail.setCode(violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
            detail.setMessage(violation.getMessage());
            detail.setField(violation.getPropertyPath().toString());
            detail.setRejectValue(violation.getInvalidValue());
            details.add(detail);
        }
        res.setErrors(details);
        setStatus(response, 400);
        return res;
    }
}
