package com.pk.core.business.service.impl;

import com.pk.core.business.repository.BrandRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.service.BrandService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminBrandRequestDto;
import com.pk.core.model.dto.response.BrandResponseDto;
import com.pk.core.model.entity.BrandEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Thương hiệu: code chuẩn hoá IN HOA, duy nhất trong số bản ghi chưa xoá; INACTIVE không gán được cho sản phẩm mới. */
@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{2,40}");

    private final BrandRepo brands;
    private final ProductRepo products;

    @Transactional(readOnly = true)
    @Override
    public PageResponse<BrandResponseDto> list(String search, String status, int page, int take) {
        String kw = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!st.isEmpty() && !"ALL".equals(st)) {
            checkStatus(st);
        } else {
            st = "";
        }
        final String statusFilter = st;
        List<BrandResponseDto> all = live().stream()
                .filter(b -> kw.isEmpty() || contains(b.getName(), kw) || contains(b.getCode(), kw))
                .filter(b -> statusFilter.isEmpty() || statusFilter.equals(b.getStatus()))
                .sorted(Comparator.comparing(BrandEntity::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(BrandEntity::getId))
                .map(BrandResponseDto::from).toList();
        return PageResponse.paginate(all, page, take);
    }

    @Transactional(readOnly = true)
    @Override
    public BrandResponseDto detail(Long id) {
        return BrandResponseDto.from(find(id));
    }

    @Transactional
    @Override
    public BrandResponseDto create(AdminBrandRequestDto req) {
        BrandEntity b = new BrandEntity();
        b.setName(requireName(req.getName()));
        b.setCode(normalizeCode(req.getCode(), true));
        assertCodeFree(b.getCode(), null);
        b.setImageUrl(blankToNull(req.getImage()));
        b.setWebsiteUrl(checkUrl(req.getWebsiteUrl()));
        b.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? BrandEntity.ACTIVE : checkStatus(req.getStatus()));
        brands.create(b);
        return BrandResponseDto.from(b);
    }

    @Transactional
    @Override
    public BrandResponseDto update(Long id, AdminBrandRequestDto req) {
        BrandEntity b = find(id);
        if (req.getName() != null) {
            b.setName(requireName(req.getName()));
        }
        if (req.getCode() != null) {
            String code = normalizeCode(req.getCode(), true);
            assertCodeFree(code, b.getId());
            b.setCode(code);
        }
        if (req.getImage() != null) {
            b.setImageUrl(blankToNull(req.getImage()));
        }
        if (req.getWebsiteUrl() != null) {
            b.setWebsiteUrl(checkUrl(req.getWebsiteUrl()));
        }
        if (req.getStatus() != null) {
            b.setStatus(checkStatus(req.getStatus()));
        }
        b.touch();
        brands.update(b);
        return BrandResponseDto.from(b);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        BrandEntity b = find(id);
        Long used = products.countByBrandId(b.getId());
        if (used != null && used > 0) {
            throw BusinessException.conflict("BRAND_IN_USE",
                    "Thương hiệu đang được " + used + " sản phẩm sử dụng, hãy chuyển sản phẩm sang thương hiệu khác hoặc ngừng kích hoạt", used);
        }
        b.setDeletedDate(new Date());
        b.touch();
        brands.update(b);
    }

    // ----------------------------------------------------------------------

    private List<BrandEntity> live() {
        return brands.findAll(Sort.unsorted()).stream().filter(b -> b.getDeletedDate() == null).toList();
    }

    private BrandEntity find(Long id) {
        BrandEntity b = id == null ? null : brands.findOne(id);
        if (b == null || b.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Thương hiệu", "Brand", id);
        }
        return b;
    }

    private void assertCodeFree(String code, Long selfId) {
        for (BrandEntity other : live()) {
            if (code.equalsIgnoreCase(other.getCode()) && !other.getId().equals(selfId)) {
                throw BusinessException.conflict("BRAND_CODE_DUPLICATE", "Mã thương hiệu đã tồn tại: " + code, code);
            }
        }
    }

    private static boolean contains(String v, String kw) {
        return v != null && v.toLowerCase(Locale.ROOT).contains(kw);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw BusinessException.badRequest("BRAND_INVALID", "Tên thương hiệu là bắt buộc", "name");
        }
        if (name.trim().length() > 150) {
            throw BusinessException.badRequest("BRAND_INVALID", "Tên thương hiệu tối đa 150 ký tự", "name");
        }
        return name.trim();
    }

    private static String normalizeCode(String code, boolean required) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (c.isEmpty() && !required) {
            return null;
        }
        if (!CODE.matcher(c).matches()) {
            throw BusinessException.badRequest("BRAND_INVALID",
                    "Mã thương hiệu gồm 2-40 ký tự chữ, số, gạch ngang hoặc gạch dưới", "code");
        }
        return c;
    }

    private static String checkStatus(String status) {
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (!BrandEntity.ACTIVE.equals(s) && !BrandEntity.INACTIVE.equals(s)) {
            throw BusinessException.badRequest("BRAND_INVALID", "status không hợp lệ: " + status + " (ACTIVE | INACTIVE)", "status");
        }
        return s;
    }

    private static String checkUrl(String url) {
        String u = blankToNull(url);
        if (u != null && (u.length() > 500 || !(u.startsWith("http://") || u.startsWith("https://")))) {
            throw BusinessException.badRequest("BRAND_INVALID", "websiteUrl phải bắt đầu bằng http:// hoặc https:// (tối đa 500 ký tự)", "websiteUrl");
        }
        return u;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
