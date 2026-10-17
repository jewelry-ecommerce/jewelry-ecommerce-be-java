package com.pk.core.business.service.impl;

import com.pk.core.business.service.AdminProductQuery;
import com.pk.core.business.service.ImportExportFileStore;
import com.pk.core.business.service.ImportExportService;
import com.pk.core.business.service.ProductService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminCreateProductRequestDto;
import com.pk.core.model.dto.request.AdminSkuRequestDto;
import com.pk.core.model.dto.request.IeProductExportRequestDto;
import com.pk.core.model.dto.response.IeJobResponseDto;
import com.pk.core.model.dto.response.IeResultRowDto;
import com.pk.core.model.dto.response.ProductResponseDto;
import com.pk.core.model.dto.response.ProductSkuResponseDto;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ImportExportJobEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Import/export CSV sản phẩm (V24), đơn giản hoá so với hạ tầng queue của công ty: chạy đồng bộ, mỗi lần gọi tạo 1 job
 * (DONE/FAILED) kèm kết quả từng dòng. KHÔNG có @Transactional ở lớp này: mỗi dòng import là 1 lệnh ProductService có
 * giao dịch riêng nên dòng lỗi chỉ rollback chính nó, các dòng khác vẫn thành công (partial success).
 */
@Service
@RequiredArgsConstructor
public class ImportExportServiceImpl implements ImportExportService {

    static final List<String> EXPORT_HEADER = List.of("product_code", "product_name", "slug", "category_id",
            "category_slug", "status", "short_description", "material", "occasion", "thumbnail_url", "sku_code",
            "sku_name", "size", "list_price", "sale_price", "stock", "sku_status");
    static final int MAX_IMPORT_ROWS = 2000;
    private static final int EXPORT_PAGE = 100;

    private final ImportExportJobStore store;
    private final ImportExportFileStore files;
    private final ProductService productService;

    @Override
    public Map<String, Object> topics() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("topics", List.of(
                Map.of("code", "PRODUCT_EXPORT", "name", "Xuất sản phẩm (CSV)", "type", "EXPORT"),
                Map.of("code", "PRODUCT_IMPORT", "name", "Nhập sản phẩm (CSV)", "type", "IMPORT")));
        out.put("exportColumns", EXPORT_HEADER);
        out.put("importRequiredColumns", List.of("product_code", "product_name", "category_code|category_id",
                "sku_code", "list_price"));
        out.put("maxImportRows", MAX_IMPORT_ROWS);
        // Fast FBO thuộc hạ tầng tích hợp của công ty, hệ thống này không hỗ trợ.
        out.put("fastFboSupported", false);
        return out;
    }

    // ===================== EXPORT =====================

    @Override
    public IeJobResponseDto exportProducts(IeProductExportRequestDto req, Long userId) {
        IeProductExportRequestDto r = req == null ? new IeProductExportRequestDto() : req;
        String name = "products-export-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        ImportExportJobEntity job = store.start(ImportExportJobEntity.EXPORT, userId, name, r.getReferenceType(),
                r.getReferenceId(), describe(r));
        try {
            List<ProductResponseDto> products = collect(r);
            StringBuilder sb = new StringBuilder("﻿").append(CsvSupport.line(EXPORT_HEADER)).append("\r\n");
            int rows = 0;
            for (ProductResponseDto p : products) {
                List<ProductSkuResponseDto> skus = p.getSkus() == null ? List.of() : p.getSkus();
                if (skus.isEmpty()) {
                    sb.append(CsvSupport.line(productCells(p, null))).append("\r\n");
                    rows++;
                }
                for (ProductSkuResponseDto s : skus) {
                    sb.append(CsvSupport.line(productCells(p, s))).append("\r\n");
                    rows++;
                }
            }
            String url = files.saveCsv(sb.toString().getBytes(StandardCharsets.UTF_8));
            store.finish(job, ImportExportJobEntity.DONE, null, rows, rows, 0, url);
        } catch (Exception ex) {
            store.finish(job, ImportExportJobEntity.FAILED, message(ex), 0, 0, 0, null);
        }
        return IeJobResponseDto.from(store.job(job.getId()));
    }

    private List<ProductResponseDto> collect(IeProductExportRequestDto r) {
        List<ProductResponseDto> out = new ArrayList<>();
        if (r.getProductIds() != null && !r.getProductIds().isEmpty()) {
            for (Long id : r.getProductIds()) {
                try {
                    out.add(productService.findByIdForAdmin(id));
                } catch (ResourceNotFoundException ex) {
                    // sản phẩm đã xoá/không tồn tại: bỏ qua
                }
            }
            return out;
        }
        boolean onePage = Boolean.TRUE.equals(r.getIsPagination());
        int page = onePage && r.getPage() != null ? r.getPage() : 1;
        int take = onePage && r.getTake() != null ? Math.min(Math.max(r.getTake(), 1), EXPORT_PAGE) : EXPORT_PAGE;
        while (true) {
            PageResponse<ProductResponseDto> res = productService.searchForAdmin(new AdminProductQuery(r.getStatus(),
                    r.getCategorySlug(), r.getCollectionSlug(), r.getSearch(), r.getMinPrice(), r.getMaxPrice(),
                    r.getTimeFilter(), r.getTimeFrom(), r.getTimeTo(), null, null, null, null, page, take,
                    r.getHasPendingApproval(), r.getApprovalStatus()));
            out.addAll(res.content());
            if (onePage || page >= res.totalPages()) {
                return out;
            }
            page++;
        }
    }

    private static List<Object> productCells(ProductResponseDto p, ProductSkuResponseDto s) {
        List<Object> c = new ArrayList<>();
        c.add(p.getCode());
        c.add(p.getName());
        c.add(p.getSlug());
        c.add(p.getCategoryId());
        c.add(p.getCategorySlug());
        c.add(p.getStatus());
        c.add(p.getShortDescription());
        c.add(p.getMaterial());
        c.add(p.getOccasion());
        c.add(p.getThumbnailUrl());
        c.add(s == null ? null : s.getSkuCode());
        c.add(s == null ? null : s.getName());
        c.add(s == null ? null : s.getSizeLabel());
        c.add(s == null ? null : plain(s.getListPrice()));
        c.add(s == null ? null : plain(s.getSalePrice()));
        c.add(s == null ? null : s.getAvailableStock());
        c.add(s == null ? null : s.getStatus());
        return c;
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private static String describe(IeProductExportRequestDto r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productIds", r.getProductIds());
        m.put("search", r.getSearch());
        m.put("status", r.getStatus());
        m.put("categorySlug", r.getCategorySlug());
        m.put("isPagination", r.getIsPagination());
        m.put("page", r.getPage());
        m.put("take", r.getTake());
        return m.toString();
    }

    // ===================== IMPORT =====================

    @Override
    public IeJobResponseDto importProducts(MultipartFile file, String referenceType, String referenceId, Long userId) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Chưa chọn file CSV (field `file`)");
        }
        String original = file.getOriginalFilename() == null ? "import.csv" : file.getOriginalFilename();
        if (!original.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Chỉ hỗ trợ file .csv");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Không đọc được file");
        }
        ImportExportJobEntity job = store.start(ImportExportJobEntity.IMPORT, userId, original, referenceType,
                referenceId, null);
        String url = files.saveCsv(bytes);
        List<List<String>> table = CsvSupport.parse(new String(bytes, StandardCharsets.UTF_8));
        if (table.isEmpty()) {
            store.finish(job, ImportExportJobEntity.FAILED, "File rỗng", 0, 0, 0, url);
            return IeJobResponseDto.from(store.job(job.getId()));
        }
        Map<String, Integer> col = new HashMap<>();
        List<String> header = table.get(0);
        for (int i = 0; i < header.size(); i++) {
            col.putIfAbsent(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = new ArrayList<>();
        for (String req : List.of("product_code", "product_name", "sku_code", "list_price")) {
            if (!col.containsKey(req)) {
                missing.add(req);
            }
        }
        if (!col.containsKey("category_code") && !col.containsKey("category_id")) {
            missing.add("category_code|category_id");
        }
        if (!missing.isEmpty()) {
            store.finish(job, ImportExportJobEntity.FAILED, "Thiếu cột bắt buộc: " + String.join(", ", missing), 0, 0, 0, url);
            return IeJobResponseDto.from(store.job(job.getId()));
        }
        int total = table.size() - 1;
        if (total > MAX_IMPORT_ROWS) {
            store.finish(job, ImportExportJobEntity.FAILED, "Tối đa " + MAX_IMPORT_ROWS + " dòng mỗi lần import", total, 0, 0, url);
            return IeJobResponseDto.from(store.job(job.getId()));
        }
        int ok = 0;
        int failed = 0;
        for (int i = 1; i < table.size(); i++) {
            List<String> row = table.get(i);
            String data = CsvSupport.line(row);
            try {
                String result = importRow(row, col);
                store.addRow(job.getId(), i + 1, "OK", result, data);
                ok++;
            } catch (Exception ex) {
                store.addRow(job.getId(), i + 1, "ERROR", message(ex), data);
                failed++;
            }
        }
        boolean allFailed = total > 0 && ok == 0;
        store.finish(job, allFailed ? ImportExportJobEntity.FAILED : ImportExportJobEntity.DONE,
                allFailed ? "Tất cả " + total + " dòng đều lỗi" : null, total, ok, failed, url);
        return IeJobResponseDto.from(store.job(job.getId()));
    }

    /** @return mô tả hành động đã làm (CREATED_PRODUCT | ADDED_SKU | UPDATED_SKU). */
    private String importRow(List<String> row, Map<String, Integer> col) {
        String code = get(row, col, "product_code");
        String name = get(row, col, "product_name");
        String skuCode = get(row, col, "sku_code");
        if (code == null || name == null || skuCode == null) {
            throw new IllegalArgumentException("Thiếu product_code / product_name / sku_code");
        }
        BigDecimal listPrice = decimal(get(row, col, "list_price"), "list_price", true);
        BigDecimal salePrice = decimal(get(row, col, "sale_price"), "sale_price", false);
        int stock = integer(get(row, col, "stock"), "stock");

        ProductEntity product = store.productByCode(code);
        if (product == null) {
            CategoryEntity category = resolveCategory(get(row, col, "category_code"), get(row, col, "category_id"));
            AdminCreateProductRequestDto req = new AdminCreateProductRequestDto();
            req.setCategoryId(category.getId());
            req.setCode(code);
            req.setName(name);
            req.setSlug(get(row, col, "slug"));
            req.setShortDescription(get(row, col, "short_description"));
            req.setMaterial(get(row, col, "material"));
            req.setOccasion(get(row, col, "occasion"));
            req.setThumbnailUrl(get(row, col, "thumbnail_url"));
            AdminSkuRequestDto sku = sku(skuCode, row, col, listPrice, salePrice, stock);
            sku.setDefault(true);
            req.setVariants(List.of(sku));
            productService.create(req);
            return "CREATED_PRODUCT";
        }
        ProductSkuEntity existing = store.skuByCode(skuCode);
        if (existing == null) {
            productService.addVariant(product.getId(), sku(skuCode, row, col, listPrice, salePrice, stock));
            return "ADDED_SKU";
        }
        if (!product.getId().equals(existing.getProductId())) {
            throw new IllegalArgumentException("Mã SKU " + skuCode + " thuộc sản phẩm khác");
        }
        // Cập nhật SKU hiện có: gộp giá trị CSV lên dữ liệu hiện tại để không xoá các trường không có trong CSV.
        // Tồn kho không đổi ở đây (đi qua module kho để có lịch sử).
        // Lấy từ ENTITY (không phải DTO) vì DTO không mang giá vốn/giá sỉ/thuế/kích thước - nếu lấy từ DTO sẽ xoá mất chúng.
        ProductSkuEntity cur = existing;
        AdminSkuRequestDto req = new AdminSkuRequestDto();
        req.setSkuCode(cur.getSkuCode());
        req.setName(cur.getName());
        req.setMaterial(cur.getMaterial());
        req.setGemstone(cur.getGemstone());
        req.setSizeLabel(cur.getSizeLabel());
        req.setMetalColorLabel(cur.getMetalColorLabel());
        req.setCaratWeight(cur.getCaratWeight());
        req.setWeightGram(cur.getWeightGram());
        req.setCostPrice(cur.getCostPrice());
        req.setB2bWholesalePrice(cur.getB2bWholesalePrice());
        req.setTaxRateOverride(cur.getTaxRateOverride());
        req.setLength(cur.getLength());
        req.setWidth(cur.getWidth());
        req.setHeight(cur.getHeight());
        req.setDefault(cur.isDefault());
        req.setStatus(cur.getStatus());
        req.setListPrice(listPrice);
        req.setSalePrice(col.containsKey("sale_price") ? salePrice : cur.getSalePrice());
        String skuName = get(row, col, "sku_name");
        if (skuName != null) {
            req.setName(skuName);
        }
        String size = get(row, col, "size");
        if (size != null) {
            req.setSizeLabel(size);
        }
        productService.updateVariant(product.getId(), existing.getId(), req);
        return "UPDATED_SKU";
    }

    private static AdminSkuRequestDto sku(String skuCode, List<String> row, Map<String, Integer> col,
                                          BigDecimal listPrice, BigDecimal salePrice, int stock) {
        AdminSkuRequestDto s = new AdminSkuRequestDto();
        s.setSkuCode(skuCode);
        s.setName(get(row, col, "sku_name"));
        s.setSizeLabel(get(row, col, "size"));
        s.setListPrice(listPrice);
        s.setSalePrice(salePrice);
        s.setOnHand(stock);
        return s;
    }

    private CategoryEntity resolveCategory(String categoryCode, String categoryId) {
        CategoryEntity c = null;
        if (categoryCode != null) {
            c = store.categoryByCode(categoryCode);
        } else if (categoryId != null) {
            try {
                c = store.categoryById(Long.valueOf(categoryId));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("category_id không hợp lệ: " + categoryId);
            }
        }
        if (c == null) {
            throw new IllegalArgumentException("Không tìm thấy danh mục (category_code/category_id)");
        }
        return c;
    }

    private static String get(List<String> row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null || i >= row.size()) {
            return null;
        }
        String v = row.get(i).trim();
        return v.isEmpty() ? null : v;
    }

    private static BigDecimal decimal(String v, String field, boolean required) {
        if (v == null) {
            if (required) {
                throw new IllegalArgumentException(field + " là bắt buộc");
            }
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(v);
            if (d.signum() < 0) {
                throw new IllegalArgumentException(field + " không được âm");
            }
            return d;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " không hợp lệ: " + v);
        }
    }

    private static int integer(String v, String field) {
        if (v == null) {
            return 0;
        }
        try {
            int n = Integer.parseInt(v);
            if (n < 0) {
                throw new IllegalArgumentException(field + " không được âm");
            }
            return n;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " không hợp lệ: " + v);
        }
    }

    private static String message(Exception ex) {
        String m = ex.getMessage();
        return m == null || m.isBlank() ? ex.getClass().getSimpleName() : m;
    }

    // ===================== JOBS =====================

    @Override
    public PageResponse<IeJobResponseDto> listJobs(String jobType, String status, int page, int take) {
        return listJobs(jobType, status, null, null, page, take);
    }

    @Override
    public PageResponse<IeJobResponseDto> listJobs(String jobType, String status, String referenceType,
                                                    String referenceId, int page, int take) {
        String rt = referenceType == null ? "" : referenceType.trim();
        String rid = referenceId == null ? "" : referenceId.trim();
        String t = jobType == null ? "" : jobType.trim().toUpperCase(Locale.ROOT);
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<IeJobResponseDto> all = store.allJobs().stream()
                .filter(j -> t.isEmpty() || t.equals(j.getJobType()))
                .filter(j -> s.isEmpty() || s.equals(j.getStatus()))
                .filter(j -> rt.isEmpty() || rt.equalsIgnoreCase(j.getReferenceType()))
                .filter(j -> rid.isEmpty() || rid.equalsIgnoreCase(j.getReferenceId()))
                .map(IeJobResponseDto::from).toList();
        return PageResponse.paginate(all, page, take);
    }

    @Override
    public IeJobResponseDto getJob(Long jobId) {
        ImportExportJobEntity j = jobId == null ? null : store.job(jobId);
        if (j == null) {
            throw new ResourceNotFoundException("Tác vụ", "Job", jobId);
        }
        return IeJobResponseDto.from(j);
    }

    @Override
    public PageResponse<IeResultRowDto> resultRows(Long jobId, String status, int page, int take) {
        getJob(jobId);
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<IeResultRowDto> all = store.rowsOf(jobId).stream()
                .filter(r -> s.isEmpty() || s.equals(r.getStatus()))
                .map(IeResultRowDto::from).toList();
        return PageResponse.paginate(all, page, take);
    }
}
