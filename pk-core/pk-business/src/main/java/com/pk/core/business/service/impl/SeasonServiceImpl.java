package com.pk.core.business.service.impl;

import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.SeasonRepo;
import com.pk.core.business.service.SeasonService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminSeasonRequestDto;
import com.pk.core.model.dto.response.SeasonResponseDto;
import com.pk.core.model.entity.SeasonEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Mùa: code IN HOA duy nhất trong số bản ghi chưa xoá; tháng 1..12 (cho phép vắt năm, vd 11 -> 2). */
@Service
@RequiredArgsConstructor
public class SeasonServiceImpl implements SeasonService {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{2,40}");

    private final SeasonRepo seasons;
    private final ProductRepo products;

    @Transactional(readOnly = true)
    @Override
    public PageResponse<SeasonResponseDto> list(String search, Boolean isActive, int page, int take) {
        String kw = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<SeasonResponseDto> all = live().stream()
                .filter(s -> kw.isEmpty() || contains(s.getName(), kw) || contains(s.getCode(), kw))
                .filter(s -> isActive == null || isActive == s.isActive())
                .sorted(Comparator.comparingInt(SeasonEntity::getSortOrder).thenComparing(SeasonEntity::getId))
                .map(SeasonResponseDto::from).toList();
        return PageResponse.paginate(all, page, take);
    }

    @Transactional(readOnly = true)
    @Override
    public SeasonResponseDto detail(Long id) {
        return SeasonResponseDto.from(find(id));
    }

    @Transactional
    @Override
    public SeasonResponseDto create(AdminSeasonRequestDto req) {
        SeasonEntity s = new SeasonEntity();
        s.setName(requireName(req.getName()));
        s.setCode(normalizeCode(req.getCode()));
        assertCodeFree(s.getCode(), null);
        s.setDescription(blankToNull(req.getDescription()));
        s.setStartMonth(month(req.getStartMonth(), "startMonth"));
        s.setEndMonth(month(req.getEndMonth(), "endMonth"));
        s.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        s.setActive(req.getIsActive() == null || req.getIsActive());
        seasons.create(s);
        return SeasonResponseDto.from(s);
    }

    @Transactional
    @Override
    public SeasonResponseDto update(Long id, AdminSeasonRequestDto req) {
        SeasonEntity s = find(id);
        if (req.getName() != null) {
            s.setName(requireName(req.getName()));
        }
        if (req.getCode() != null) {
            String code = normalizeCode(req.getCode());
            assertCodeFree(code, s.getId());
            s.setCode(code);
        }
        if (req.getDescription() != null) {
            s.setDescription(blankToNull(req.getDescription()));
        }
        if (req.getStartMonth() != null) {
            s.setStartMonth(month(req.getStartMonth(), "startMonth"));
        }
        if (req.getEndMonth() != null) {
            s.setEndMonth(month(req.getEndMonth(), "endMonth"));
        }
        if (req.getSortOrder() != null) {
            s.setSortOrder(req.getSortOrder());
        }
        if (req.getIsActive() != null) {
            s.setActive(req.getIsActive());
        }
        s.touch();
        seasons.update(s);
        return SeasonResponseDto.from(s);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        SeasonEntity s = find(id);
        Long used = products.countBySeasonId(s.getId());
        if (used != null && used > 0) {
            throw BusinessException.conflict("SEASON_IN_USE",
                    "Mùa đang được " + used + " sản phẩm sử dụng, hãy chuyển sản phẩm sang mùa khác hoặc ngừng kích hoạt", used);
        }
        s.setDeletedDate(new Date());
        s.touch();
        seasons.update(s);
    }

    // ----------------------------------------------------------------------

    private List<SeasonEntity> live() {
        return seasons.findAll(Sort.unsorted()).stream().filter(s -> s.getDeletedDate() == null).toList();
    }

    private SeasonEntity find(Long id) {
        SeasonEntity s = id == null ? null : seasons.findOne(id);
        if (s == null || s.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Mùa", "Season", id);
        }
        return s;
    }

    private void assertCodeFree(String code, Long selfId) {
        for (SeasonEntity other : live()) {
            if (code.equalsIgnoreCase(other.getCode()) && !other.getId().equals(selfId)) {
                throw BusinessException.conflict("SEASON_CODE_DUPLICATE", "Mã mùa đã tồn tại: " + code, code);
            }
        }
    }

    private static boolean contains(String v, String kw) {
        return v != null && v.toLowerCase(Locale.ROOT).contains(kw);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 150) {
            throw BusinessException.badRequest("SEASON_INVALID", "Tên mùa là bắt buộc (tối đa 150 ký tự)", "name");
        }
        return name.trim();
    }

    private static String normalizeCode(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(c).matches()) {
            throw BusinessException.badRequest("SEASON_INVALID",
                    "Mã mùa gồm 2-40 ký tự chữ, số, gạch ngang hoặc gạch dưới", "code");
        }
        return c;
    }

    private static int month(Integer m, String field) {
        if (m == null || m < 1 || m > 12) {
            throw BusinessException.badRequest("SEASON_INVALID", field + " phải từ 1 đến 12", field);
        }
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
