# Contract API chi tiết cho FE (sinh tự động từ source)
Nguồn: Controller (`pk-api`) và DTO (`pk-model`). Tiền tố `/api/v1`. Thành công: `{ "data": ... }`; lỗi: `{ "status", "message", "errors":[{ "code", "message" }] }` với HTTP status thật. Danh sách phân trang: `{ content, page, size, totalElements, totalPages }`. Cột "Query": `?` = tuỳ chọn, `=x` = mặc định. Cột "Auth" suy ra từ `SecurityConfig`.
File này do script `claude/_gen/gen_contract.py` sinh; sửa source rồi chạy lại để cập nhật, đừng sửa tay.

## OMS - Đơn hàng

### Endpoint — `AdminOrderRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/orders` | ADMIN, ORDER_MANAGER | `search`?, `status`?, `paymentStatus`?, `fromDate`?, `toDate`?, `dateField`?, `skuCode`?, `shippingMethod`?, `provinceName`?, `source`?, `orderBy`?, `orderType`?, `createdById`?, `page`? =1, `take`? =20 |  |  |
| GET | `/admin/orders/counts` | ADMIN, ORDER_MANAGER | `search`?, `paymentStatus`?, `fromDate`?, `toDate`?, `dateField`?, `skuCode`?, `shippingMethod`?, `provinceName`?, `source`?, `createdById`? |  |  |
| GET | `/admin/orders/{orderCode}` | ADMIN, ORDER_MANAGER |  |  |  |
| PATCH | `/admin/orders/{orderCode}/status` | ADMIN, ORDER_MANAGER |  | `AdminOrderStatusRequestDto` |  |
| POST | `/admin/orders/{orderCode}/cancel` | ADMIN, ORDER_MANAGER |  | `CancelOrderRequestDto` |  |
| POST | `/admin/orders/{orderCode}/return` | ADMIN, ORDER_MANAGER |  | `AdminOrderReturnRequestDto` |  |
| POST | `/admin/orders/{orderCode}/refund` | ADMIN, ORDER_MANAGER |  | `AdminOrderRefundRequestDto` |  |
| GET | `/admin/orders/audit-logs` | ADMIN, ORDER_MANAGER | `entityName`?, `entityId`, `page`? =1, `take`? =20 |  |  |
| POST | `/admin/orders/pricing-context/quote` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.OrderUpsert` |  |
| POST | `/admin/orders` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.OrderUpsert` |  |
| POST | `/admin/orders/duplicate` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.Duplicate` |  |
| PUT | `/admin/orders/{orderCode}` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.OrderUpsert` |  |
| PUT | `/admin/orders/{orderCode}/confirm-payment` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.ConfirmPayment` |  |
| GET | `/admin/orders/{orderCode}/notes` | ADMIN, ORDER_MANAGER | `type`? |  |  |
| POST | `/admin/orders/{orderCode}/notes` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.Note` |  |

### Endpoint — `AdminOrderReturnRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| POST | `/admin/order-returns/quote` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.OrderReturn` |  |
| POST | `/admin/order-returns/commit` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.OrderReturn` |  |
| POST | `/admin/order-returns` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.OrderReturn` |  |
| POST | `/admin/order-returns/list` | ADMIN, ORDER_MANAGER |  | `Map<String, Object>` |  |
| GET | `/admin/order-returns/{id}` | ADMIN, ORDER_MANAGER |  |  |  |
| PUT | `/admin/order-returns/{id}` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.OrderReturn` |  |
| PUT | `/admin/order-returns/{id}/approve` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.Resolve` |  |
| PUT | `/admin/order-returns/{id}/reject` | ADMIN, ORDER_MANAGER |  | `OmsRequestDtos.Resolve` |  |

### Endpoint — `OrderRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| POST | `/storefront/order` | Customer (Bearer) |  | `CreateOrderRequestDto` | Idempotency-Key |
| POST | `/storefront/order/guest` | Public |  | `GuestOrderRequestDto` | X-Guest-Cart-Id, x-guest-id, Idempotency-Key |
| GET | `/storefront/order/guest/{orderCode}` | Public | `phone` |  |  |
| GET | `/storefront/order` | Customer (Bearer) | `page`? =1, `take`? =20 |  |  |
| GET | `/storefront/order/{orderCode}` | Customer (Bearer) |  |  |  |
| POST | `/storefront/order/{orderCode}/cancel` | Customer (Bearer) |  | `CancelOrderRequestDto` |  |

### Endpoint — `CheckoutRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| POST | `/storefront/checkout/quote` | Customer (Bearer) |  | `QuoteRequestDto` |  |
| POST | `/storefront/checkout/quote/guest` | Public |  | `GuestOrderRequestDto` |  |

### DTO

**CreateOrderRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `addressId` | `Long` | bắt buộc |
| `shippingMethod` | `String` | bắt buộc; size max=30 |
| `paymentMethod` | `String` | bắt buộc; size max=30 |
| `voucherCode` | `String` | size max=40 |
| `note` | `String` | size max=500 |
| `items` | `List<OrderItemRequestDto>` | bắt buộc |

**GuestOrderRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `recipientName` | `String` | bắt buộc; size max=120 |
| `phone` | `String` | bắt buộc; chỉ nhận [0-9+ .-]{8,20} |
| `email` | `String` | size max=150 |
| `province` | `String` | bắt buộc; size max=100 |
| `district` | `String` | size max=100 |
| `ward` | `String` | size max=100 |
| `addressLine` | `String` | bắt buộc; size max=255 |
| `shippingMethod` | `String` | bắt buộc; size max=30 |
| `paymentMethod` | `String` | bắt buộc; size max=30 |
| `voucherCode` | `String` | size max=40 |
| `note` | `String` | size max=500 |
| `items` | `List<OrderItemRequestDto>` | bắt buộc |

**OrderItemRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `skuId` | `Long` | bắt buộc |
| `quantity` | `int` | Min 1 |

**AdminOrderStatusRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `status` | `String` | bắt buộc |
| `note` | `String` | size max=500 |
| `trackingCode` | `String` | size max=100 |

**AdminOrderRefundRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `amount` | `BigDecimal` | bắt buộc |
| `reason` | `String` | bắt buộc; size max=500 |

**AdminOrderReturnRequestDto**

`class AdminOrderReturnRequestDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `lineIds` | `List<Long>` |  |
| `lines` | `List<Line>` |  |

`class Line`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `lineId` | `Long` |  |
| `quantity` | `int` |  |

**CancelOrderRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `reason` | `String` | size max=500 |

**OrderResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `code` | `String` |  |
| `status` | `String` |  |
| `paymentStatus` | `String` |  |
| `paymentMethod` | `String` |  |
| `shippingMethod` | `String` |  |
| `trackingCode` | `String` |  |
| `shipRecipientName` | `String` |  |
| `shipPhone` | `String` |  |
| `shipProvince` | `String` |  |
| `shipDistrict` | `String` |  |
| `shipWard` | `String` |  |
| `shipAddressLine` | `String` |  |
| `subtotal` | `BigDecimal` |  |
| `productDiscount` | `BigDecimal` |  |
| `voucherDiscount` | `BigDecimal` |  |
| `shippingFee` | `BigDecimal` |  |
| `grandTotal` | `BigDecimal` |  |
| `appliedVoucherCode` | `String` |  |
| `note` | `String` |  |
| `placedAt` | `Date` |  |
| `paymentId` | `Long` |  |
| `refundedAmount` | `BigDecimal` |  |
| `items` | `List<OrderItemResponseDto>` |  |
| `timeline` | `List<OrderTimelineResponseDto>` |  |
| `customerId` | `Long` |  |
| `contactEmail` | `String` |  |
| `deliveredAt` | `Date` |  |
| `codAmount` | `BigDecimal` |  |
| `createdById` | `Long` |  |
| `source` | `String` |  |

**OrderItemResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `productId` | `Long` |  |
| `skuId` | `Long` |  |
| `name` | `String` |  |
| `imageUrl` | `String` |  |
| `quantity` | `int` |  |
| `unitPrice` | `BigDecimal` |  |
| `lineTotal` | `BigDecimal` |  |
| `returnedQuantity` | `int` |  |
| `skuCode` | `String` |  |
| `sizeLabel` | `String` |  |
| `metalColorLabel` | `String` |  |

**OrderTimelineResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `status` | `String` |  |
| `occurredAt` | `Date` |  |
| `note` | `String` |  |
| `actor` | `String` |  |

**OmsRequestDtos**

`class OrderUpsert`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `contextVersion` | `Integer` |  |
| `receiverPhone` | `String` |  |
| `firstName` | `String` |  |
| `lastName` | `String` |  |
| `provinceCode` | `Long` |  |
| `provinceName` | `String` |  |
| `wardCode` | `Long` |  |
| `wardName` | `String` |  |
| `districtName` | `String` |  |
| `addressLine` | `String` |  |
| `items` | `List<Item>` |  |
| `couponCodes` | `List<String>` |  |
| `shippingMethod` | `String` |  |
| `paymentMethod` | `String` |  |
| `pricingEvaluationId` | `String` |  |
| `expectedGrandTotalMinor` | `Long` |  |
| `idempotencyKey` | `String` |  |
| `note` | `String` |  |
| `notePayment` | `String` |  |
| `contactEmail` | `String` |  |
| `source` | `String` |  |
| `purpose` | `String` |  |
| `orderKind` | `String` |  |

`class Item`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `variationId` | `Long` |  |
| `skuId` | `Long` |  |
| `quantity` | `Integer` |  |

`class Note`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `content` | `String` |  |
| `type` | `String` |  |

`class Duplicate`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `sourceOrderId` | `String` |  |
| `replaceSourceAfterDuplicate` | `Boolean` |  |
| `cancellationReason` | `String` |  |
| `cancellationNote` | `String` |  |

`class ConfirmPayment`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `note` | `String` |  |

`class OrderReturn`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `orderId` | `String` |  |
| `type` | `String` |  |
| `reason` | `String` |  |
| `note` | `String` |  |
| `status` | `String` |  |
| `paymentMethod` | `String` |  |
| `trackingCode` | `String` |  |
| `returnFaultParty` | `String` |  |
| `returnItems` | `List<ReturnItem>` |  |
| `exchangeItems` | `List<ExchangeItem>` |  |
| `shippingAddress` | `Map<String, Object>` |  |
| `evidenceImageUrls` | `List<String>` |  |
| `refundBankAccountHolder` | `String` |  |
| `refundBankAccountNumber` | `String` |  |
| `refundBankName` | `String` |  |
| `refundBankBranch` | `String` |  |
| `bankAccountHolder` | `String` |  |
| `bankAccountNumber` | `String` |  |
| `bankName` | `String` |  |
| `bankBranch` | `String` |  |
| `pricingQuote` | `Map<String, Object>` |  |

`class ReturnItem`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `orderItemId` | `Long` |  |
| `quantity` | `Integer` |  |

`class ExchangeItem`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `variationId` | `Long` |  |
| `quantity` | `Integer` |  |

`class Resolve`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `note` | `String` |  |

## Tồn kho

### Endpoint — `AdminInventoryRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/inventory/stock-levels` | ADMIN, ORDER_MANAGER | `warehouseId`?, `search`?, `keyword`? |  |  |
| POST | `/admin/inventory/adjustments` | ADMIN, ORDER_MANAGER |  | `AdminStockAdjustmentRequestDto` |  |

### DTO

**AdminStockAdjustmentRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `skuId` | `Long` | bắt buộc |
| `quantityDelta` | `Integer` | bắt buộc |
| `reason` | `String` | bắt buộc |
| `warehouseId` | `String` |  |

**StockLevelResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `skuId` | `Long` |  |
| `productId` | `Long` |  |
| `skuCode` | `String` |  |
| `name` | `String` |  |
| `warehouseId` | `String` |  |
| `onHand` | `int` |  |
| `reserved` | `int` |  |
| `available` | `int` |  |
| `stockStatus` | `String` |  |

## PIM - Sản phẩm / Danh mục / Bộ sưu tập / Thuộc tính

### Endpoint — `AdminCatalogRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/catalog/products` | ADMIN, CATALOG_MANAGER | `search`?, `status`?, `categorySlug`?, `collectionSlug`?, `minPrice`?, `maxPrice`?, `timeFilter`?, `timeFrom`?, `timeTo`?, `createdFrom`?, `createdTo`?, `updatedFrom`?, `updatedTo`?, `hasPendingApproval`?, `approvalStatus`?, `stockStatus`?, `brandId`?, `seasonId`?, `seasonUuid`?, `orderBy`?, `orderType`?, `createdById`?, `page`? =1, `take`? =20 |  |  |
| GET | `/admin/catalog/products/counts` | ADMIN, CATALOG_MANAGER | `search`?, `categorySlug`?, `collectionSlug`?, `minPrice`?, `maxPrice`?, `timeFilter`?, `timeFrom`?, `timeTo`?, `createdFrom`?, `createdTo`?, `updatedFrom`?, `updatedTo`?, `stockStatus`?, `brandId`?, `seasonId`?, `seasonUuid`?, `createdById`? |  |  |
| POST | `/admin/catalog/products` | ADMIN, CATALOG_MANAGER |  | `AdminCreateProductRequestDto` |  |
| GET | `/admin/catalog/products/{productId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/catalog/products/{productId}` | ADMIN, CATALOG_MANAGER |  | `AdminUpdateProductRequestDto` |  |
| DELETE | `/admin/catalog/products/{productId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/catalog/products/{productId}/archive` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/catalog/products/{productId}/discontinue` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/catalog/products/{productId}/restore` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/catalog/products/{productId}/publish` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/catalog/products/{productId}/unpublish` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/catalog/products/{productId}/variants` | ADMIN, CATALOG_MANAGER |  | `AdminSkuRequestDto` |  |
| PUT | `/admin/catalog/products/{productId}/variants/{skuId}` | ADMIN, CATALOG_MANAGER |  | `AdminSkuRequestDto` |  |
| POST | `/admin/catalog/upload` | ADMIN, CATALOG_MANAGER |  | `multipart: file` |  |
| GET | `/admin/catalog/products/{productId}/media` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/catalog/products/{productId}/media` | ADMIN, CATALOG_MANAGER |  | `AdminProductMediaRequestDto` |  |
| PUT | `/admin/catalog/products/{productId}/media/{mediaId}` | ADMIN, CATALOG_MANAGER |  | `AdminProductMediaRequestDto` |  |
| DELETE | `/admin/catalog/products/{productId}/media/{mediaId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/catalog/products/{productId}/variants/{skuId}/attributes` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/catalog/products/{productId}/variants/{skuId}/attributes` | ADMIN, CATALOG_MANAGER |  | `AdminSkuAttributesRequestDto` |  |
| GET | `/admin/catalog/products/{productId}/variants` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/catalog/variants` | ADMIN, CATALOG_MANAGER | `search`? |  |  |
| GET | `/admin/catalog/categories` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/catalog/categories` | ADMIN, CATALOG_MANAGER |  | `AdminCategoryRequestDto` |  |
| DELETE | `/admin/catalog/categories/{categoryId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/catalog/categories/{categoryId}` | ADMIN, CATALOG_MANAGER |  | `AdminCategoryRequestDto` |  |
| GET | `/admin/catalog/collections` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/catalog/collections` | ADMIN, CATALOG_MANAGER |  | `AdminCollectionRequestDto` |  |
| DELETE | `/admin/catalog/collections/{collectionId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/catalog/collections/{collectionId}` | ADMIN, CATALOG_MANAGER |  | `AdminCollectionRequestDto` |  |
| POST | `/admin/catalog/collections/{collectionId}/products` | ADMIN, CATALOG_MANAGER |  | `AdminCollectionProductsRequestDto` |  |
| DELETE | `/admin/catalog/collections/{collectionId}/products/{productId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/catalog/attributes` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/catalog/attributes` | ADMIN, CATALOG_MANAGER |  | `AdminAttributeRequestDto` |  |
| DELETE | `/admin/catalog/attributes/{attributeId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/catalog/attributes/{attributeId}` | ADMIN, CATALOG_MANAGER |  | `AdminAttributeRequestDto` |  |

### Endpoint — `AdminProductApprovalRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| POST | `/admin/catalog/products/{productId}/submit` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/catalog/product-approvals` | ADMIN, CATALOG_MANAGER | `status`?, `search`?, `page`? =1, `take`? =20 |  |  |
| POST | `/admin/catalog/product-approvals/review` | ADMIN, CATALOG_MANAGER |  | `AdminProductApprovalReviewRequestDto` |  |
| POST | `/admin/catalog/product-approvals/{requestId}/cancel` | ADMIN, CATALOG_MANAGER |  |  |  |

### Endpoint — `ProductRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/storefront/product/search-pinned` | Public |  |  |  |
| GET | `/storefront/product` | Public | `category`?, `collection`?, `search`?, `material`?, `minPrice`?, `maxPrice`?, `categorySlugs`?, `attributes`?, `inStock`?, `sort`? =NEWEST, `page`? =1, `take`? =20 |  |  |
| GET | `/storefront/product/filters` | Public | `category`?, `categorySlugs`?, `collection`?, `search`?, `minPrice`?, `maxPrice`?, `inStock`? |  |  |
| GET | `/storefront/product/categories` | Public |  |  |  |
| GET | `/storefront/product/collections` | Public |  |  |  |
| GET | `/storefront/product/{slug}` | Public |  |  |  |

### DTO

**AdminCreateProductRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `categoryId` | `Long` | bắt buộc |
| `code` | `String` | bắt buộc |
| `name` | `String` | bắt buộc |
| `slug` | `String` |  |
| `shortDescription` | `String` |  |
| `description` | `String` |  |
| `material` | `String` |  |
| `occasion` | `String` |  |
| `thumbnailUrl` | `String` |  |

**AdminUpdateProductRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `code` | `String` |  |
| `categoryId` | `Long` | bắt buộc |
| `name` | `String` | bắt buộc |
| `shortDescription` | `String` |  |
| `description` | `String` |  |
| `material` | `String` |  |
| `occasion` | `String` |  |
| `thumbnailUrl` | `String` |  |

**AdminSkuRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `skuCode` | `String` | bắt buộc |
| `name` | `String` |  |
| `material` | `String` |  |
| `gemstone` | `String` |  |
| `sizeLabel` | `String` |  |
| `metalColorLabel` | `String` |  |
| `caratWeight` | `BigDecimal` |  |
| `weightGram` | `BigDecimal` |  |
| `listPrice` | `BigDecimal` | bắt buộc; >= 0 |
| `salePrice` | `BigDecimal` |  |
| `costPrice` | `BigDecimal` | >= 0 |
| `b2bWholesalePrice` | `BigDecimal` | >= 0 |
| `taxRateOverride` | `BigDecimal` |  |
| `length` | `BigDecimal` | >= 0 |
| `width` | `BigDecimal` | >= 0 |
| `height` | `BigDecimal` | >= 0 |
| `onHand` | `int` | >= 0 |
| `isDefault` | `boolean` |  |
| `status` | `String` |  |

**AdminProductMediaRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `url` | `String` | bắt buộc; size max=500 |
| `alt` | `String` | size max=200 |
| `type` | `String` |  |
| `sortOrder` | `Integer` |  |
| `skuId` | `Long` |  |
| `primary` | `Boolean` |  |

**AdminProductExtraRequestDto**

`class AdminProductExtraRequestDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `unitOfMeasure` | `String` |  |
| `taxRateOverride` | `BigDecimal` | DecimalMin 0; DecimalMax 100 |
| `weightGram` | `BigDecimal` | >= 0 |
| `length` | `BigDecimal` | >= 0 |
| `width` | `BigDecimal` | >= 0 |
| `height` | `BigDecimal` | >= 0 |
| `metaTitle` | `String` | size max=200 |
| `metaDescription` | `String` | size max=500 |
| `metaKeywords` | `String` | size max=500 |
| `brandId` | `Long` |  |
| `seasonId` | `Long` |  |

`class Info`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `title` | `String` | size max=200 |
| `description` | `String` | bắt buộc; size max=4000 |
| `sortOrder` | `Integer` | >= 0 |

**AdminSkuAttributesRequestDto**

`class AdminSkuAttributesRequestDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `values` | `List<Item>` | bắt buộc |

`class Item`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `attributeId` | `Long` |  |
| `attributeCode` | `String` |  |
| `value` | `String` |  |
| `values` | `List<String>` |  |

**AdminCategoryRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | bắt buộc |
| `slug` | `String` |  |
| `description` | `String` |  |
| `imageUrl` | `String` |  |
| `parentId` | `Long` |  |
| `sortOrder` | `int` |  |
| `code` | `String` |  |
| `status` | `String` |  |
| `directAttributeIds` | `List<Long>` |  |

**AdminCollectionRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | bắt buộc |
| `slug` | `String` |  |
| `description` | `String` |  |
| `heroImageUrl` | `String` |  |
| `status` | `String` |  |

**AdminCollectionProductsRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `productIds` | `List<Long>` | bắt buộc |

**AdminAttributeRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | bắt buộc |
| `code` | `String` | bắt buộc |
| `type` | `String` | bắt buộc |
| `displayType` | `String` |  |
| `status` | `String` |  |
| `values` | `List<AdminAttributeValueRequestDto>` |  |

**AdminAttributeValueRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `code` | `String` | size max=255 |
| `value` | `String` | size max=255 |
| `imageUrl` | `String` | size max=500 |

**AdminProductApprovalReviewRequestDto**

`class AdminProductApprovalReviewRequestDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `items` | `List<Item>` | bắt buộc |
| `decision` | `String` |  |
| `reason` | `String` |  |

`class Item`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `productId` | `Long` |  |
| `approvalRequestId` | `Long` |  |

**ProductResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `code` | `String` |  |
| `name` | `String` |  |
| `slug` | `String` |  |
| `shortDescription` | `String` |  |
| `description` | `String` |  |
| `status` | `String` |  |
| `categoryId` | `Long` |  |
| `categoryName` | `String` |  |
| `material` | `String` |  |
| `occasion` | `String` |  |
| `thumbnailUrl` | `String` |  |
| `priceFrom` | `BigDecimal` |  |
| `skus` | `List<ProductSkuResponseDto>` |  |
| `collections` | `List<CollectionResponseDto>` |  |
| `publishedAt` | `Date` |  |
| `categorySlug` | `String` |  |
| `createdById` | `Long` |  |
| `brandId` | `Long` |  |
| `brandName` | `String` |  |
| `brandCode` | `String` |  |
| `brandImage` | `String` |  |
| `seasonId` | `Long` |  |
| `seasonName` | `String` |  |
| `media` | `List<ProductMediaResponseDto>` |  |
| `unitOfMeasure` | `String` |  |
| `taxRateOverride` | `BigDecimal` |  |
| `weightGram` | `BigDecimal` |  |
| `length` | `BigDecimal` |  |
| `width` | `BigDecimal` |  |
| `height` | `BigDecimal` |  |
| `metaTitle` | `String` |  |
| `metaDescription` | `String` |  |
| `metaKeywords` | `String` |  |
| `productInfos` | `List<ProductInfoResponseDto>` |  |
| `productMixMatch` | `List<ProductMixMatchResponseDto>` |  |
| `approval` | `ProductApprovalSummaryDto` |  |
| `hasPendingApproval` | `Boolean` |  |
| `pendingApproval` | `Map<String, Object>` |  |

**ProductSkuResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `skuCode` | `String` |  |
| `name` | `String` |  |
| `status` | `String` |  |
| `material` | `String` |  |
| `gemstone` | `String` |  |
| `sizeLabel` | `String` |  |
| `metalColorLabel` | `String` |  |
| `caratWeight` | `BigDecimal` |  |
| `weightGram` | `BigDecimal` |  |
| `listPrice` | `BigDecimal` |  |
| `salePrice` | `BigDecimal` |  |
| `availableStock` | `int` |  |
| `stockStatus` | `String` |  |
| `isDefault` | `boolean` |  |
| `attributeBindings` | `List<Map<String, Object>>` |  |
| `costPrice` | `BigDecimal` |  |
| `b2bWholesalePrice` | `BigDecimal` |  |
| `taxRateOverride` | `BigDecimal` |  |
| `length` | `BigDecimal` |  |
| `width` | `BigDecimal` |  |
| `height` | `BigDecimal` |  |

**ProductMediaResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `productId` | `String` |  |
| `skuId` | `String` |  |
| `url` | `String` |  |
| `alt` | `String` |  |
| `type` | `String` |  |
| `sortOrder` | `int` |  |
| `primary` | `boolean` |  |

**ProductInfoResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `title` | `String` |  |
| `description` | `String` |  |
| `sortOrder` | `int` |  |

**ProductCountsDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `all` | `long` |  |
| `draft` | `long` |  |
| `unpublished` | `long` |  |
| `published` | `long` |  |
| `discontinued` | `long` |  |
| `archived` | `long` |  |
| `hasPendingApproval` | `long` |  |
| `approvalApproved` | `long` |  |
| `approvalRejected` | `long` |  |
| `approvalCancelled` | `long` |  |

**CategoryResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `parentId` | `Long` |  |
| `name` | `String` |  |
| `slug` | `String` |  |
| `description` | `String` |  |
| `imageUrl` | `String` |  |
| `sortOrder` | `int` |  |
| `code` | `String` |  |
| `status` | `String` |  |
| `level` | `int` |  |
| `ancestorCategoryIds` | `List<String>` |  |
| `ancestorCategorySlugs` | `List<String>` |  |
| `directAttributeIds` | `List<Long>` |  |
| `inheritedAttributeIds` | `List<Long>` |  |
| `effectiveAttributeIds` | `List<Long>` |  |
| `directAttributes` | `List<CategoryAttributeRefDto>` |  |
| `inheritedAttributes` | `List<CategoryAttributeRefDto>` |  |
| `effectiveAttributes` | `List<CategoryAttributeRefDto>` |  |
| `allowedAttributeIds` | `List<String>` |  |
| `allowedFilterAttributes` | `List<CategoryAttributeRefDto>` |  |

**CollectionResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `name` | `String` |  |
| `slug` | `String` |  |
| `description` | `String` |  |
| `heroImageUrl` | `String` |  |
| `status` | `String` |  |

**ProductAttributeResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `name` | `String` |  |
| `code` | `String` |  |
| `type` | `String` |  |
| `options` | `List<String>` |  |
| `status` | `String` |  |
| `displayType` | `String` |  |
| `values` | `List<ProductAttributeValueResponseDto>` |  |

**ProductApprovalRequestResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `productId` | `String` |  |
| `productCode` | `String` |  |
| `productName` | `String` |  |
| `productStatus` | `String` |  |
| `status` | `String` |  |
| `actionType` | `String` |  |
| `submittedAt` | `String` |  |
| `submittedBy` | `String` |  |
| `reviewedAt` | `String` |  |
| `reviewedBy` | `String` |  |
| `rejectedReason` | `String` |  |
| `createdAt` | `String` |  |

## Banner

### Endpoint — `AdminBannerRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/banner/banners` | ADMIN, CATALOG_MANAGER | `page`? =1, `take`? =20, `status`?, `mediaType`?, `actionType`?, `internalName`? |  |  |
| POST | `/admin/banner/banners` | ADMIN, CATALOG_MANAGER |  | `AdminBannerRequestDto` |  |
| GET | `/admin/banner/banners/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/banner/banners/{id}` | ADMIN, CATALOG_MANAGER |  | `AdminBannerRequestDto` |  |
| DELETE | `/admin/banner/banners/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/banner/placements` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/banner/placements` | ADMIN, CATALOG_MANAGER |  | `AdminBannerPlacementRequestDto` |  |
| PATCH | `/admin/banner/placements/{id}` | ADMIN, CATALOG_MANAGER |  | `AdminBannerPlacementRequestDto` |  |
| GET | `/admin/banner/product-list-configs` | ADMIN, CATALOG_MANAGER | `conditionType`? |  |  |
| POST | `/admin/banner/product-list-configs` | ADMIN, CATALOG_MANAGER |  | `AdminProductListBannerConfigRequestDto` |  |
| PATCH | `/admin/banner/product-list-configs/{id}` | ADMIN, CATALOG_MANAGER |  | `AdminProductListBannerConfigRequestDto` |  |
| DELETE | `/admin/banner/product-list-configs/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| DELETE | `/admin/banner/placements/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/banner/placements/{id}/layouts` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/banner/placements/{id}/layouts/{layoutId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/banner/placements/{id}/layouts` | ADMIN, CATALOG_MANAGER |  | `AdminBannerLayoutRequestDto` |  |
| PATCH | `/admin/banner/placements/{id}/layouts/{layoutId}` | ADMIN, CATALOG_MANAGER |  | `AdminBannerLayoutRequestDto` |  |
| PATCH | `/admin/banner/placements/{id}/layouts/{layoutId}/items/{itemId}` | ADMIN, CATALOG_MANAGER |  | `AdminBannerLayoutItemRequestDto` |  |
| DELETE | `/admin/banner/placements/{id}/layouts/{layoutId}/items/{itemId}` | ADMIN, CATALOG_MANAGER |  |  |  |

### Endpoint — `BannerRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/storefront/banner/product-list` | Public | `conditionType`, `targetSlug` |  |  |
| GET | `/storefront/banner/placements/code/{code}/render` | Public | `platform`? |  |  |

### DTO

**AdminBannerRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `internalName` | `String` | bắt buộc; size max=150 |
| `mediaUrl` | `String` | size max=500 |
| `mediaMobileUrl` | `String` | size max=500 |
| `mediaLinkUrl` | `String` | size max=500 |
| `mediaPosterUrl` | `String` | size max=500 |
| `mediaMobilePosterUrl` | `String` | size max=500 |
| `mediaFit` | `String` | chỉ nhận (cover|contain|fill|none) |
| `mediaType` | `String` | chỉ nhận (GIF|IMAGE|VIDEO|VIRTUAL) |
| `layout` | `String` | chỉ nhận (RIGHT_TOP|CENTER_TOP|LEFT_TOP|RIGHT_CENTER|CENTER|LEFT_CENTER|RIGHT_BOTTOM|CENTER_BOTTOM|LEFT_BOTTOM) |
| `title` | `String` | size max=200 |
| `subtitle` | `String` | size max=300 |
| `titleColor` | `String` | size max=30 |
| `actionsLayout` | `String` | chỉ nhận (STACK|INLINE) |
| `actions` | `List<BannerActionRequestDto>` | size max=10 |
| `overlayOpacity` | `Double` | DecimalMin 0; DecimalMax 1 |
| `status` | `String` | chỉ nhận (DRAFT|ACTIVE|INACTIVE|ARCHIVED) |
| `placementCode` | `String` | size max=60 |
| `sortOrder` | `Integer` |  |

**BannerActionRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `actionType` | `String` | bắt buộc; chỉ nhận (URL|LINK|NEWS|POPUP|PRODUCT|CATEGORY|COLLECTION) |
| `actionTarget` | `String` | size max=500 |
| `ctaText` | `String` | size max=100 |
| `ctaBg` | `String` | size max=30 |
| `ctaColor` | `String` | size max=30 |

**AdminBannerPlacementRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `code` | `String` | size max=60 |
| `name` | `String` | size max=150 |
| `displayType` | `String` |  |
| `platform` | `String` |  |
| `status` | `String` |  |
| `settings` | `Map<String, Object>` |  |

**AdminBannerLayoutRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | size max=150 |
| `status` | `String` |  |
| `activeFrom` | `Date` |  |
| `activeTo` | `Date` |  |
| `note` | `String` | size max=500 |
| `items` | `List<AdminBannerLayoutItemRequestDto>` |  |

**AdminBannerLayoutItemRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `slotKey` | `String` |  |
| `bannerId` | `Long` |  |
| `isActive` | `Boolean` |  |
| `priority` | `Integer` |  |
| `displayMetadata` | `Map<String, Object>` |  |

**BannerResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `internalName` | `String` |  |
| `mediaUrl` | `String` |  |
| `mediaMobileUrl` | `String` |  |
| `mediaLinkUrl` | `String` |  |
| `mediaPosterUrl` | `String` |  |
| `mediaMobilePosterUrl` | `String` |  |
| `mediaFit` | `String` |  |
| `mediaType` | `String` |  |
| `layout` | `String` |  |
| `title` | `String` |  |
| `subtitle` | `String` |  |
| `titleColor` | `String` |  |
| `actionsLayout` | `String` |  |
| `actions` | `List<BannerActionResponseDto>` |  |
| `overlayOpacity` | `Double` |  |
| `status` | `String` |  |
| `placementCode` | `String` |  |
| `sortOrder` | `int` |  |

**BannerPlacementResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `code` | `String` |  |
| `name` | `String` |  |
| `displayType` | `String` |  |
| `platform` | `String` |  |
| `status` | `String` |  |
| `settings` | `Map<String, Object>` |  |

**BannerLayoutResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `placementId` | `String` |  |
| `name` | `String` |  |
| `status` | `String` |  |
| `activeFrom` | `Date` |  |
| `activeTo` | `Date` |  |
| `note` | `String` |  |
| `revision` | `int` |  |
| `createdDate` | `Date` |  |
| `updatedDate` | `Date` |  |
| `items` | `List<BannerLayoutItemResponseDto>` |  |

**BannerLayoutItemResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `layoutId` | `String` |  |
| `slotKey` | `String` |  |
| `bannerId` | `String` |  |
| `isActive` | `Boolean` |  |
| `priority` | `int` |  |
| `displayMetadata` | `Map<String, Object>` |  |
| `banner` | `BannerResponseDto` |  |

**BannerPlacementRenderResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `code` | `String` |  |
| `name` | `String` |  |
| `displayType` | `String` |  |
| `banners` | `List<BannerResponseDto>` |  |
| `platform` | `String` |  |
| `settings` | `Map<String, Object>` |  |
| `layoutSlots` | `List<Map<String, Object>>` |  |

**AdminProductListBannerConfigRequestDto**

`class AdminProductListBannerConfigRequestDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `conditionType` | `String` |  |
| `targetId` | `String` |  |
| `targetSlug` | `String` |  |
| `targetName` | `String` |  |
| `categoryPathSlugs` | `List<String>` |  |
| `isActive` | `Boolean` |  |
| `banners` | `List<Banner>` |  |

`class Banner`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `enabled` | `Boolean` |  |
| `imageUrl` | `String` |  |
| `startTime` | `String` |  |
| `endTime` | `String` |  |
| `actionType` | `String` |  |
| `actionUrl` | `String` |  |
| `openInNewTab` | `Boolean` |  |
| `mixMatchSkus` | `List<MixMatchSku>` |  |

`class MixMatchSku`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `variationId` | `String` |  |
| `sortOrder` | `Integer` |  |

**ProductListBannerConfigResponseDto**

`record ProductListBannerConfigResponseDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `conditionType` | `String` | |
| `targetId` | `String` | |
| `targetSlug` | `String` | |
| `targetName` | `String` | |
| `categoryPathSlugs` | `List<String>` | |
| `isActive` | `boolean` | |
| `banners` | `List<Banner>` | |
| `createdAt` | `String` | |
| `updatedAt` | `String` | |

`record Banner`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` | |
| `enabled` | `boolean` | |
| `imageUrl` | `String` | |
| `startTime` | `String` | |
| `endTime` | `String` | |
| `actionType` | `String` | |
| `actionUrl` | `String` | |
| `openInNewTab` | `boolean` | |
| `mixMatchSkus` | `List<MixMatchSku>` | |

`record MixMatchSku`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `variationId` | `String` | |
| `sortOrder` | `int` | |

## CMS - Trang & block

### Endpoint — `AdminCmsRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/cms/pages` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/cms/pages` | ADMIN, CATALOG_MANAGER |  | `AdminCmsPageRequestDto` |  |
| GET | `/admin/cms/pages/{pageId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/cms/pages/{pageId}` | ADMIN, CATALOG_MANAGER |  | `AdminCmsPageRequestDto` |  |
| POST | `/admin/cms/pages/{pageId}/publish` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/cms/pages/{pageId}/unpublish` | ADMIN, CATALOG_MANAGER |  |  |  |
| DELETE | `/admin/cms/pages/{pageId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/cms/pages/{pageId}/blocks` | ADMIN, CATALOG_MANAGER |  | `AdminCmsBlockRequestDto` |  |
| PUT | `/admin/cms/pages/{pageId}/blocks/{blockId}` | ADMIN, CATALOG_MANAGER |  | `AdminCmsBlockRequestDto` |  |
| DELETE | `/admin/cms/pages/{pageId}/blocks/{blockId}` | ADMIN, CATALOG_MANAGER |  |  |  |

### Endpoint — `CmsRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/storefront/cms/pages/{slug}` | Public | `device`? |  |  |

### DTO

**AdminCmsPageRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | bắt buộc |
| `slug` | `String` |  |
| `status` | `String` |  |
| `locale` | `String` |  |
| `active` | `Boolean` |  |
| `seo` | `java.util.Map<String, Object>` |  |
| `blocks` | `List<AdminCmsBlockRequestDto>` |  |
| `targetDevice` | `String` |  |
| `scheduleStart` | `Date` |  |
| `scheduleEnd` | `Date` |  |
| `scheduleStartProvided` | `boolean` |  |
| `scheduleEndProvided` | `boolean` |  |

**AdminCmsBlockRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `type` | `String` | bắt buộc |
| `sortOrder` | `Integer` | bắt buộc; >= 0 |
| `config` | `Map<String, Object>` | bắt buộc |
| `targetSegment` | `String` |  |
| `visible` | `Boolean` |  |

**CmsPageResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `name` | `String` |  |
| `slug` | `String` |  |
| `status` | `String` |  |
| `blocks` | `List<CmsBlockResponseDto>` |  |
| `locale` | `String` |  |
| `active` | `boolean` |  |
| `seo` | `Map<String, Object>` |  |
| `targetDevice` | `String` |  |
| `scheduleStart` | `Date` |  |
| `scheduleEnd` | `Date` |  |
| `displayStatus` | `String` |  |
| `hasUnpublishedChanges` | `boolean` |  |
| `versionId` | `int` |  |
| `publishedAt` | `Date` |  |
| `live` | `Map<String, Object>` |  |

**CmsBlockResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `type` | `String` |  |
| `sortOrder` | `int` |  |
| `config` | `Map<String, Object>` |  |
| `targetSegment` | `String` |  |
| `visible` | `boolean` |  |

**CmsStorefrontPageResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `name` | `String` |  |
| `slug` | `String` |  |
| `status` | `String` |  |
| `blocks` | `List<CmsStorefrontBlockResponseDto>` |  |
| `locale` | `String` |  |
| `seo` | `Map<String, Object>` |  |
| `targetDevice` | `String` |  |
| `versionId` | `int` |  |

**CmsStorefrontBlockResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `type` | `String` |  |
| `sortOrder` | `int` |  |
| `config` | `Map<String, Object>` |  |
| `content` | `Map<String, Object>` |  |

## Header / Footer / Logo

### Endpoint — `AdminSiteLayoutRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/cms/headers` | ADMIN, CATALOG_MANAGER | `search`?, `status`?, `page`? =1, `take`? =20 |  |  |
| GET | `/admin/cms/headers/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/cms/headers` | ADMIN, CATALOG_MANAGER |  | `AdminSiteHeaderRequestDto` |  |
| PATCH | `/admin/cms/headers/{id}` | ADMIN, CATALOG_MANAGER |  | `AdminSiteHeaderRequestDto` |  |
| PATCH | `/admin/cms/headers/{id}/status` | ADMIN, CATALOG_MANAGER |  | `AdminSiteStatusRequestDto` |  |
| DELETE | `/admin/cms/headers/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/cms/footer` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/cms/footer` | ADMIN, CATALOG_MANAGER |  | `AdminSiteFooterRequestDto` |  |
| GET | `/admin/cms/logos` | ADMIN, CATALOG_MANAGER | `search`?, `status`?, `type`?, `page`? =1, `take`? =20 |  |  |
| GET | `/admin/cms/logos/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/cms/logos` | ADMIN, CATALOG_MANAGER |  | `AdminSiteLogoRequestDto` |  |
| PATCH | `/admin/cms/logos/{id}` | ADMIN, CATALOG_MANAGER |  | `AdminSiteLogoRequestDto` |  |
| PATCH | `/admin/cms/logos/{id}/status` | ADMIN, CATALOG_MANAGER |  | `AdminSiteStatusRequestDto` |  |
| PATCH | `/admin/cms/logos/{id}/archive` | ADMIN, CATALOG_MANAGER |  |  |  |

### Endpoint — `SiteLayoutRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/storefront/cms/site-layout` | Public |  |  |  |

### DTO

**AdminSiteHeaderRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `bannerName` | `String` | size max=150 |
| `bannerDisplayStyle` | `String` | chỉ nhận (TEXT|MEDIA) |
| `bannerContent` | `String` | size max=1000 |
| `bannerBgColor` | `String` | size max=30 |
| `bannerTextColor` | `String` | size max=30 |
| `bannerTargetUrl` | `String` | size max=500 |
| `bannerAnimationStyle` | `String` | size max=30 |
| `bannerLoopIntervalSec` | `Integer` | Min 1; Max 3600 |
| `bannerStartTime` | `String` |  |
| `bannerEndTime` | `String` |  |
| `status` | `String` | chỉ nhận (DRAFT|ACTIVE|DEACTIVE|SCHEDULED) |
| `confirmReplaceActive` | `Boolean` |  |

**AdminSiteStatusRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `status` | `String` | bắt buộc; chỉ nhận (DRAFT|ACTIVE|DEACTIVE|SCHEDULED) |
| `confirmReplaceActive` | `Boolean` |  |

**AdminSiteLogoRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | size max=150 |
| `type` | `String` | chỉ nhận (HEADER|FOOTER|AUTH|FAVICON) |
| `status` | `String` | chỉ nhận (DRAFT|ACTIVE|DEACTIVE|SCHEDULED) |
| `logoUrl` | `String` | size max=500 |
| `logoTheme` | `String` | size max=30 |
| `logoTargetUrl` | `String` | size max=500 |

**AdminSiteFooterRequestDto**

`class AdminSiteFooterRequestDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `copyrightText` | `String` | size max=500 |
| `footerLogoUrl` | `String` | size max=500 |
| `certificationImageUrl` | `String` | size max=500 |
| `certificationUrl` | `String` | size max=500 |
| `newsletterHeading` | `String` | size max=200 |
| `newsletterSubHeading` | `String` | size max=300 |
| `socialLinks` | `List<SocialLink>` | size max=10 |
| `menuColumns` | `List<MenuColumn>` | size max=6 |

`class SocialLink`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` | size max=60 |
| `platform` | `String` | bắt buộc; chỉ nhận (Facebook|Instagram|TikTok|YouTube|Twitter|LinkedIn|Zalo|Other) |
| `displayName` | `String` | bắt buộc; size max=100 |
| `url` | `String` | bắt buộc; size max=500 |
| `image` | `String` | size max=500 |
| `orderIndex` | `int` |  |

`class MenuColumn`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` | size max=60 |
| `title` | `String` | bắt buộc; size max=100 |
| `orderIndex` | `int` |  |
| `items` | `List<MenuItem>` | size max=12 |

`class MenuItem`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` | size max=60 |
| `label` | `String` | bắt buộc; size max=100 |
| `url` | `String` | bắt buộc; size max=500 |
| `orderIndex` | `int` |  |

**SiteLayoutResponseDto**

`record Header`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `status` | `String` | |
| `displayStatus` | `String` | |
| `bannerName` | `String` | |
| `bannerDisplayStyle` | `String` | |
| `bannerContent` | `String` | |
| `bannerBgColor` | `String` | |
| `bannerTextColor` | `String` | |
| `bannerTargetUrl` | `String` | |
| `bannerAnimationStyle` | `String` | |
| `bannerLoopIntervalSec` | `Integer` | |
| `bannerStartTime` | `String` | |
| `bannerEndTime` | `String` | |
| `createdAt` | `String` | |
| `updatedAt` | `String` | |

`record Logo`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `name` | `String` | |
| `type` | `String` | |
| `status` | `String` | |
| `logoUrl` | `String` | |
| `logoTheme` | `String` | |
| `logoTargetUrl` | `String` | |
| `createdAt` | `String` | |
| `updatedAt` | `String` | |

`record SocialLink`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` | |
| `platform` | `String` | |
| `displayName` | `String` | |
| `url` | `String` | |
| `image` | `String` | |
| `orderIndex` | `int` | |

`record MenuItem`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` | |
| `label` | `String` | |
| `url` | `String` | |
| `orderIndex` | `int` | |

`record MenuColumn`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` | |
| `title` | `String` | |
| `orderIndex` | `int` | |
| `items` | `List<MenuItem>` | |

`record Footer`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `copyrightText` | `String` | |
| `footerLogoUrl` | `String` | |
| `certificationImageUrl` | `String` | |
| `certificationUrl` | `String` | |
| `newsletterHeading` | `String` | |
| `newsletterSubHeading` | `String` | |
| `socialLinks` | `List<SocialLink>` | |
| `menuColumns` | `List<MenuColumn>` | |
| `updatedAt` | `String` | |

`record SiteLayout`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `header` | `Header` | |
| `footer` | `Footer` | |
| `logos` | `Logo>` | |

## Thương hiệu & Mùa

### Endpoint — `AdminBrandSeasonRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/catalog/brands` | ADMIN, CATALOG_MANAGER | `search`?, `status`?, `page`? =1, `take`? =20 |  |  |
| GET | `/admin/catalog/brands/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/catalog/brands` | ADMIN, CATALOG_MANAGER |  | `AdminBrandRequestDto` |  |
| PATCH | `/admin/catalog/brands/{id}` | ADMIN, CATALOG_MANAGER |  | `AdminBrandRequestDto` |  |
| DELETE | `/admin/catalog/brands/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/catalog/seasons` | ADMIN, CATALOG_MANAGER | `search`?, `isActive`?, `page`? =1, `take`? =20 |  |  |
| GET | `/admin/catalog/seasons/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/catalog/seasons` | ADMIN, CATALOG_MANAGER |  | `AdminSeasonRequestDto` |  |
| PATCH | `/admin/catalog/seasons/{id}` | ADMIN, CATALOG_MANAGER |  | `AdminSeasonRequestDto` |  |
| DELETE | `/admin/catalog/seasons/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |

### DTO

**AdminBrandRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` |  |
| `code` | `String` |  |
| `image` | `String` |  |
| `websiteUrl` | `String` |  |
| `status` | `String` |  |

**AdminSeasonRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` |  |
| `code` | `String` |  |
| `description` | `String` |  |
| `startMonth` | `Integer` |  |
| `endMonth` | `Integer` |  |
| `sortOrder` | `Integer` |  |
| `isActive` | `Boolean` |  |

**BrandResponseDto**

`record BrandResponseDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `code` | `String` | |
| `name` | `String` | |
| `image` | `String` | |
| `websiteUrl` | `String` | |
| `status` | `String` | |
| `createdDate` | `Date` | |
| `updatedDate` | `Date` | |

**SeasonResponseDto**

`record SeasonResponseDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `code` | `String` | |
| `name` | `String` | |
| `description` | `String` | |
| `startMonth` | `int` | |
| `endMonth` | `int` | |
| `sortOrder` | `int` | |
| `isActive` | `boolean` | |
| `createdDate` | `Date` | |
| `updatedDate` | `Date` | |

## Nhãn (Badge)

### Endpoint — `AdminBadgeRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/badge-templates` | ADMIN, CATALOG_MANAGER | `page`? =1, `take`? =20, `status`?, `type`?, `badgeType`?, `defaultPosition`?, `search`? |  |  |
| POST | `/admin/badge-templates` | ADMIN, CATALOG_MANAGER |  | `AdminBadgeTemplateRequestDto` |  |
| GET | `/admin/badge-templates/{badgeId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/badge-templates/{badgeId}` | ADMIN, CATALOG_MANAGER |  | `AdminBadgeTemplateRequestDto` |  |
| DELETE | `/admin/badge-templates/{badgeId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/badge-flow` | ADMIN, CATALOG_MANAGER | `page`? =1, `take`? =20, `status`?, `search`?, `displayStatus`? |  |  |
| POST | `/admin/badge-flow` | ADMIN, CATALOG_MANAGER |  | `AdminBadgeFlowRequestDto` |  |
| PUT | `/admin/badge-flow/{flowId}` | ADMIN, CATALOG_MANAGER |  | `AdminBadgeFlowRequestDto` |  |
| DELETE | `/admin/badge-flow/{flowId}` | ADMIN, CATALOG_MANAGER |  |  |  |

### Endpoint — `BadgeRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/storefront/badge` | Public | `skuIds`?, `channel`? |  |  |

### DTO

**AdminBadgeTemplateRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | bắt buộc; size max=120 |
| `code` | `String` | bắt buộc; size max=60 |
| `description` | `String` | size max=500 |
| `type` | `String` | bắt buộc |
| `badgeType` | `String` | bắt buộc |
| `status` | `String` |  |
| `displayText` | `String` | size max=60 |
| `defaultPosition` | `String` |  |
| `styleConfig` | `Map<String, Object>` |  |
| `icon` | `String` | size max=255 |
| `image` | `String` | size max=500 |
| `iconMobile` | `String` | size max=255 |
| `imageMobile` | `String` | size max=500 |
| `assetMeta` | `Map<String, Object>` |  |
| `defaultPriorityWeight` | `Integer` |  |

**AdminBadgeFlowRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | bắt buộc; size max=150 |
| `description` | `String` | size max=500 |
| `activeFrom` | `Date` |  |
| `activeTo` | `Date` |  |
| `status` | `String` |  |
| `ruleType` | `String` | bắt buộc |
| `ruleConfig` | `Map<String, Object>` |  |
| `channel` | `String` |  |
| `templates` | `List<BadgeFlowTemplateRefDto>` | bắt buộc |

**BadgeFlowTemplateRefDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `badgeTemplateId` | `String` | bắt buộc |
| `priorityWeight` | `Integer` |  |
| `isPinned` | `Boolean` |  |

**BadgeTemplateResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `name` | `String` |  |
| `code` | `String` |  |
| `description` | `String` |  |
| `type` | `String` |  |
| `badgeType` | `String` |  |
| `status` | `String` |  |
| `displayText` | `String` |  |
| `defaultPosition` | `String` |  |
| `styleConfig` | `Map<String, Object>` |  |
| `icon` | `String` |  |
| `image` | `String` |  |
| `defaultPriorityWeight` | `int` |  |
| `iconMobile` | `String` |  |
| `imageMobile` | `String` |  |
| `assetMeta` | `Map<String, Object>` |  |

**BadgeFlowResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `name` | `String` |  |
| `description` | `String` |  |
| `status` | `String` |  |
| `isActive` | `Boolean` |  |
| `activeFrom` | `Date` |  |
| `activeTo` | `Date` |  |
| `ruleType` | `String` |  |
| `ruleConfig` | `Map<String, Object>` |  |
| `channel` | `String` |  |
| `templates` | `List<BadgeFlowTemplateRefDto>` |  |
| `displayStatus` | `String` |  |

**SkuBadgeResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `skuId` | `String` |  |
| `badges` | `List<BadgeTemplateResponseDto>` |  |

## Khuyến mãi & Voucher

### Endpoint — `AdminPromotionVoucherRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/promotions` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/promotions` | ADMIN, CATALOG_MANAGER |  | `AdminPromotionRequestDto` |  |
| GET | `/admin/promotions/{promotionId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/promotions/{promotionId}` | ADMIN, CATALOG_MANAGER |  | `AdminPromotionRequestDto` |  |
| DELETE | `/admin/promotions/{promotionId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/vouchers` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/vouchers` | ADMIN, CATALOG_MANAGER |  | `AdminVoucherRequestDto` |  |
| PUT | `/admin/vouchers/{voucherId}` | ADMIN, CATALOG_MANAGER |  | `AdminVoucherRequestDto` |  |
| DELETE | `/admin/vouchers/{voucherId}` | ADMIN, CATALOG_MANAGER |  |  |  |

### DTO

**AdminPromotionRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | bắt buộc |
| `discountType` | `String` | bắt buộc |
| `discountValue` | `BigDecimal` | bắt buộc; >= 0 |
| `maxDiscountAmount` | `BigDecimal` |  |
| `startsAt` | `Date` | bắt buộc |
| `endsAt` | `Date` | bắt buộc |
| `status` | `String` |  |
| `productIds` | `List<Long>` | bắt buộc |

**AdminVoucherRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `code` | `String` | bắt buộc |
| `discountType` | `String` | bắt buộc |
| `discountValue` | `BigDecimal` | bắt buộc; >= 0 |
| `maxDiscountAmount` | `BigDecimal` |  |
| `minOrderValue` | `BigDecimal` | >= 0 |
| `usageLimit` | `Integer` |  |
| `startsAt` | `Date` | bắt buộc |
| `endsAt` | `Date` | bắt buộc |
| `status` | `String` |  |

**PromotionResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `name` | `String` |  |
| `discountType` | `String` |  |
| `discountValue` | `BigDecimal` |  |
| `maxDiscountAmount` | `BigDecimal` |  |
| `startsAt` | `Date` |  |
| `endsAt` | `Date` |  |
| `status` | `String` |  |
| `productIds` | `List<Long>` |  |

**VoucherResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `code` | `String` |  |
| `discountType` | `String` |  |
| `discountValue` | `BigDecimal` |  |
| `maxDiscountAmount` | `BigDecimal` |  |
| `minOrderValue` | `BigDecimal` |  |
| `usageLimit` | `Integer` |  |
| `usedCount` | `int` |  |
| `startsAt` | `Date` |  |
| `endsAt` | `Date` |  |
| `status` | `String` |  |

## Khuyến mãi - Chiến dịch (wizard nháp → phát hành) & Runtime

### Endpoint — `AdminPromotionCampaignRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/promotions/campaigns` | ADMIN, CATALOG_MANAGER | `params` |  |  |
| GET | `/admin/promotions/drafts` | ADMIN, CATALOG_MANAGER | `page`?, `limit`?, `take`?, `status`?, `search`? |  |  |
| POST | `/admin/promotions/drafts` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| GET | `/admin/promotions/drafts/{draftId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/promotions/drafts/{draftId}` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| DELETE | `/admin/promotions/drafts/{draftId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/promotions/drafts/{draftId}/publish` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/drafts/{draftId}/components` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/promotions/drafts/{draftId}/target` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| PUT | `/admin/promotions/drafts/{draftId}/audience` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| PUT | `/admin/promotions/drafts/{draftId}/applicability` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| GET | `/admin/promotions/drafts/{draftId}/schemas` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/promotions/drafts/{draftId}/schemas` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| PUT | `/admin/promotions/drafts/{draftId}/schemas/order` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| GET | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| DELETE | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}/components` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}/condition-config-options` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}/benefit-config-options` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}/condition` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| PUT | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}/benefit` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| GET | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}/voucher-code-authoring` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}/voucher-codes` | ADMIN, CATALOG_MANAGER |  |  |  |
| PUT | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}/voucher-codes` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| DELETE | `/admin/promotions/drafts/{draftId}/schemas/{schemaId}/voucher-codes/{voucherCodeId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/published/{promotionId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/published/{promotionId}/quota-statistics` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/published/{promotionId}/components` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/published/{promotionId}/schemas` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/published/{promotionId}/schemas/{schemaId}/components` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/promotions/published/{promotionId}/schemas/{schemaId}/voucher-codes` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/promotions/published/{promotionId}/pause` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/promotions/published/{promotionId}/resume` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/promotions/published/{promotionId}/end` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/promotions/published/{promotionId}/schemas/{schemaId}/unpublish` | ADMIN, CATALOG_MANAGER |  |  |  |

### Endpoint — `AdminPromotionRuntimeRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| POST | `/admin/promotions/runtime/evaluate` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| POST | `/admin/promotions/drafts/{draftId}/simulate` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| POST | `/admin/promotions/drafts/{draftId}/simulation-vouchers/discover` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| POST | `/admin/promotions/vouchers/discover` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| POST | `/admin/orders/promotion-vouchers/discover` | ADMIN, ORDER_MANAGER |  | `Map<String, Object>` |  |
| POST | `/admin/promotions/vouchers/validate-code` | ADMIN, CATALOG_MANAGER |  | `Map<String, Object>` |  |
| POST | `/admin/orders/promotion-vouchers/validate-code` | ADMIN, ORDER_MANAGER |  | `Map<String, Object>` |  |
| GET | `/admin/promotions/customer-audience/by-phone` | ADMIN, CATALOG_MANAGER | `phone` |  |  |

### DTO

## Điều hướng (Navigation)

### Endpoint — `AdminNavigationRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/cms/navigations` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/cms/navigations/publish-status` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/cms/navigations/publish` | ADMIN, CATALOG_MANAGER |  |  |  |
| DELETE | `/admin/cms/navigations/cache` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/cms/navigations/reorder` | ADMIN, CATALOG_MANAGER |  | `AdminNavigationReorderRequestDto` |  |
| POST | `/admin/cms/navigations` | ADMIN, CATALOG_MANAGER |  | `AdminNavigationRequestDto` |  |
| GET | `/admin/cms/navigations/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |
| PATCH | `/admin/cms/navigations/{id}` | ADMIN, CATALOG_MANAGER |  | `AdminNavigationRequestDto` |  |
| PATCH | `/admin/cms/navigations/{id}/archive` | ADMIN, CATALOG_MANAGER |  |  |  |

### Endpoint — `NavigationRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/storefront/cms/navigation` | Public |  |  |  |
| GET | `/storefront/cms/navigation/plp-rail` | Public | `categoryId`?, `rootTargetId`?, `rootUrl`? |  |  |

### DTO

**AdminNavigationRequestDto**

`class AdminNavigationRequestDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `label` | `String` |  |
| `type` | `String` |  |
| `parentId` | `Long` |  |
| `targetId` | `String` |  |
| `url` | `String` |  |
| `queryParams` | `Map<String, Object>` |  |
| `icon` | `String` |  |
| `image` | `String` |  |
| `openInNewTab` | `Boolean` |  |
| `showOnMenuPopup` | `Boolean` |  |
| `showOnPlp` | `Boolean` |  |
| `sortOrder` | `Integer` |  |
| `badge` | `Badge` |  |
| `clearBadge` | `Boolean` |  |

`class Badge`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `designType` | `String` |  |
| `displayText` | `String` |  |
| `imageUrl` | `String` |  |
| `styleConfig` | `Map<String, Object>` |  |
| `startTime` | `String` |  |
| `endTime` | `String` |  |
| `confirmReplaceSchedule` | `Boolean` |  |

**AdminNavigationReorderRequestDto**

`class AdminNavigationReorderRequestDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `items` | `List<Item>` |  |

`class Item`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `sortOrder` | `Integer` |  |

**NavigationResponseDto**

`record NavigationResponseDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `tenantCode` | `String` | |
| `parentId` | `Long` | |
| `label` | `String` | |
| `type` | `String` | |
| `targetId` | `String` | |
| `url` | `String` | |
| `queryParams` | `Object>` | |
| `icon` | `String` | |
| `image` | `String` | |
| `openInNewTab` | `boolean` | |
| `sortOrder` | `int` | |
| `showOnMenuPopup` | `boolean` | |
| `showOnPlp` | `boolean` | |
| `isArchived` | `boolean` | |
| `layer` | `String` | |
| `badges` | `List<Badge>` | |
| `children` | `List<NavigationResponseDto>` | |
| `createdAt` | `String` | |
| `updatedAt` | `String` | |

`record Badge`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `navigationId` | `Long` | |
| `designType` | `String` | |
| `displayText` | `String` | |
| `imageUrl` | `String` | |
| `styleConfig` | `Object>` | |
| `startTime` | `String` | |
| `endTime` | `String` | |
| `layer` | `String` | |
| `createdAt` | `String` | |
| `updatedAt` | `String` | |

**StorefrontNavigationDto**

`record StorefrontNavigationDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `items` | `List<Item>` | |

`record LinkTarget`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `kind` | `String` | |
| `targetId` | `String` | |
| `url` | `String` | |
| `queryParams` | `Object>` | |

`record Badge`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` | |
| `designType` | `String` | |
| `displayText` | `String` | |
| `imageUrl` | `String` | |
| `styleConfig` | `Object>` | |
| `startTime` | `String` | |
| `endTime` | `String` | |

`record Item`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` | |
| `label` | `String` | |
| `type` | `String` | |
| `targetId` | `String` | |
| `url` | `String` | |
| `queryParams` | `Object>` | |
| `icon` | `String` | |
| `image` | `String` | |
| `openInNewTab` | `boolean` | |
| `sortOrder` | `int` | |
| `parentId` | `String` | |
| `showOnMenuPopup` | `boolean` | |
| `showOnPlp` | `boolean` | |
| `linkTarget` | `LinkTarget` | |
| `badge` | `Badge` | |
| `hasChildren` | `boolean` | |
| `expandOnly` | `boolean` | |
| `viewAllLabel` | `String` | |
| `parentEffectivelyHidden` | `boolean` | |
| `children` | `List<Item>` | |

## Sản phẩm ghim tìm kiếm

### Endpoint — `AdminSearchPinnedRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/catalog/search-pinned-products` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/catalog/search-pinned-products` | ADMIN, CATALOG_MANAGER |  | `AdminSearchPinnedRequestDto` |  |
| PATCH | `/admin/catalog/search-pinned-products/{id}` | ADMIN, CATALOG_MANAGER |  | `AdminSearchPinnedRequestDto` |  |
| DELETE | `/admin/catalog/search-pinned-products/{id}` | ADMIN, CATALOG_MANAGER |  |  |  |

### DTO

**AdminSearchPinnedRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `keywordPattern` | `String` |  |
| `productId` | `Long` |  |
| `position` | `Integer` |  |
| `startDate` | `String` |  |
| `endDate` | `String` |  |
| `isActive` | `Boolean` |  |
| `clearKeywordPattern` | `Boolean` |  |
| `clearEndDate` | `Boolean` |  |

**SearchPinnedResponseDto**

`record SearchPinnedResponseDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `keywordPattern` | `String` | |
| `productId` | `Long` | |
| `position` | `int` | |
| `startDate` | `String` | |
| `endDate` | `String` | |
| `isActive` | `boolean` | |
| `createdAt` | `String` | |
| `updatedAt` | `String` | |

## Dashboard

### Endpoint — `AdminDashboardRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/dashboard/overview` | ADMIN, ORDER_MANAGER | `range`? =LAST_30_DAYS |  |  |
| GET | `/admin/dashboard/revenue` | ADMIN, ORDER_MANAGER | `range`? =LAST_30_DAYS |  |  |
| GET | `/admin/dashboard/recent-orders` | ADMIN, ORDER_MANAGER | `limit`? =8 |  |  |
| GET | `/admin/dashboard/low-stock` | ADMIN, ORDER_MANAGER |  |  |  |
| GET | `/admin/dashboard/top-products` | ADMIN, ORDER_MANAGER | `range`? =LAST_30_DAYS, `limit`? =5 |  |  |

### DTO

**DashboardResponseDto**

`record Kpi`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `key` | `String` | |
| `label` | `String` | |
| `value` | `double` | |
| `deltaPercent` | `Double` | |
| `deltaTone` | `String` | |
| `format` | `String` | |
| `caption` | `String` | |

`record Series`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `name` | `String` | |
| `values` | `List<Double>` | |

`record SeriesSet`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `labels` | `List<String>` | |
| `series` | `List<Series>` | |

`record StatusCount`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `status` | `String` | |
| `count` | `long` | |

`record RecentOrder`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `code` | `String` | |
| `customerName` | `String` | |
| `status` | `String` | |
| `paymentStatus` | `String` | |
| `channel` | `String` | |
| `grandTotal` | `double` | |
| `placedAt` | `String` | |

`record LowStockRow`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `skuCode` | `String` | |
| `productName` | `String` | |
| `variantLabel` | `String` | |
| `warehouseCode` | `String` | |
| `available` | `int` | |
| `reorderPoint` | `int` | |

`record TopProductRow`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `productId` | `String` | |
| `productName` | `String` | |
| `categoryName` | `String` | |
| `image` | `String` | |
| `unitsSold` | `long` | |
| `revenue` | `double` | |

`record Overview`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `kpis` | `List<Kpi>` | |
| `revenue` | `SeriesSet` | |
| `ordersByStatus` | `List<StatusCount>` | |
| `recentOrders` | `List<RecentOrder>` | |
| `lowStock` | `List<LowStockRow>` | |
| `topProducts` | `List<TopProductRow>` | |

## Import / Export

### Endpoint — `AdminImportExportRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/ie/topics` | ADMIN, CATALOG_MANAGER |  |  |  |
| POST | `/admin/ie/export/products` | ADMIN, CATALOG_MANAGER |  | `IeProductExportRequestDto` |  |
| POST | `/admin/ie/import/products` | ADMIN, CATALOG_MANAGER | `referenceType`?, `referenceId`? | `multipart: file` |  |
| GET | `/admin/ie/jobs` | ADMIN, CATALOG_MANAGER | `jobType`?, `status`?, `referenceType`?, `referenceId`?, `page`? =1, `take`? =20 |  |  |
| GET | `/admin/ie/job/status/{jobId}` | ADMIN, CATALOG_MANAGER |  |  |  |
| GET | `/admin/ie/jobs/{jobId}/result-rows` | ADMIN, CATALOG_MANAGER | `status`?, `page`? =1, `take`? =20 |  |  |

### DTO

**IeProductExportRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `productIds` | `List<Long>` |  |
| `search` | `String` |  |
| `status` | `String` |  |
| `categorySlug` | `String` |  |
| `collectionSlug` | `String` |  |
| `minPrice` | `BigDecimal` |  |
| `maxPrice` | `BigDecimal` |  |
| `timeFilter` | `String` |  |
| `timeFrom` | `String` |  |
| `timeTo` | `String` |  |
| `hasPendingApproval` | `Boolean` |  |
| `approvalStatus` | `String` |  |
| `isPagination` | `Boolean` |  |
| `page` | `Integer` |  |
| `take` | `Integer` |  |
| `referenceType` | `String` |  |
| `referenceId` | `String` |  |

**IeJobResponseDto**

`record IeJobResponseDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `jobId` | `Long` | |
| `id` | `Long` | |
| `jobType` | `String` | |
| `entityType` | `String` | |
| `status` | `String` | |
| `fileName` | `String` | |
| `fileUrl` | `String` | |
| `errorMessage` | `String` | |
| `totalRows` | `int` | |
| `successRows` | `int` | |
| `failedRows` | `int` | |
| `referenceType` | `String` | |
| `referenceId` | `String` | |
| `startedAt` | `String` | |
| `finishedAt` | `String` | |
| `createdAt` | `String` | |

**IeResultRowDto**

`record IeResultRowDto`

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` | |
| `lineNo` | `int` | |
| `status` | `String` | |
| `message` | `String` | |
| `data` | `String` | |

## Khách hàng & yêu cầu khách

### Endpoint — `AdminCustomerRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/customers` | ADMIN, ORDER_MANAGER | `keyword`?, `page`? =1, `take`? =20 |  |  |
| GET | `/admin/customers/{customerId}` | ADMIN, ORDER_MANAGER |  |  |  |

### Endpoint — `AdminCustomerRequestRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| GET | `/admin/customer-request/back-in-stock` | ADMIN, ORDER_MANAGER | `page`? =1, `take`? =20, `keyword`?, `status`?, `timeFilter`?, `timeFrom`?, `timeTo`?, `orderType`? |  |  |
| GET | `/admin/customer-request/order-support` | ADMIN, ORDER_MANAGER | `page`? =1, `take`? =20, `keyword`?, `status`?, `timeFilter`?, `timeFrom`?, `timeTo`?, `orderType`? |  |  |
| GET | `/admin/customer-request/newsletter` | ADMIN, ORDER_MANAGER | `page`? =1, `take`? =20, `keyword`?, `status`?, `timeFilter`?, `timeFrom`?, `timeTo`?, `orderType`? |  |  |
| GET | `/admin/customer-request/{id}` | ADMIN, ORDER_MANAGER |  |  |  |
| PATCH | `/admin/customer-request/{id}/status` | ADMIN, ORDER_MANAGER |  | `CustomerRequestStatusRequestDto` |  |
| PATCH | `/admin/customer-request/status/bulk` | ADMIN, ORDER_MANAGER |  | `CustomerRequestBulkStatusRequestDto` |  |

### Endpoint — `CustomerRequestRest`

| Method | Path | Auth | Query | Body | Header |
|---|---|---|---|---|---|
| POST | `/storefront/customer-request/back-in-stock` | Public |  | `BackInStockRequestDto` |  |
| POST | `/storefront/customer-request/order-support` | Public |  | `OrderSupportRequestDto` |  |
| POST | `/storefront/customer-request/newsletter` | Public |  | `NewsletterRequestDto` |  |

### DTO

**AdminCustomerResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `email` | `String` |  |
| `fullName` | `String` |  |
| `phone` | `String` |  |
| `enabled` | `boolean` |  |
| `orderCount` | `int` |  |

**AdminCustomerDetailResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `Long` |  |
| `email` | `String` |  |
| `fullName` | `String` |  |
| `phone` | `String` |  |
| `enabled` | `boolean` |  |
| `addresses` | `List<AddressResponseDto>` |  |
| `orders` | `List<OrderResponseDto>` |  |

**CustomerRequestResponseDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `id` | `String` |  |
| `type` | `String` |  |
| `contactChannel` | `String` |  |
| `contactValue` | `String` |  |
| `requestedAt` | `Date` |  |
| `createdAt` | `Date` |  |
| `status` | `String` |  |
| `productId` | `String` |  |
| `productVariantId` | `String` |  |
| `skuCode` | `String` |  |
| `productNameSnapshot` | `String` |  |
| `productImageUrlSnapshot` | `String` |  |
| `variantTextSnapshot` | `String` |  |
| `orderId` | `String` |  |
| `orderCode` | `String` |  |
| `orderType` | `String` |  |

**CustomerRequestStatusRequestDto**

| Field | Kiểu | Ràng buộc |
|---|---|---|
| `status` | `String` | bắt buộc; chỉ nhận (NEW|UNPROCESSED|CONTACTED|IN_PROGRESS|COMPLETED) |
