package vn.flast.service.product;
/**************************************************************************/
/*  app.java                                                              */
/**************************************************************************/
/*                       Tệp này là một phần của:                         */
/*                             Open CDP                                   */
/*                        https://flast.vn                                */
/**************************************************************************/
/* Bản quyền (c) 2025 - này thuộc về các cộng tác viên Flast Solution     */
/* (xem AUTHORS.md).                                                      */
/* Bản quyền (c) 2024-2025 Long Huu, Thành Trung                          */
/*                                                                        */
/* Bạn được quyền sử dụng phần mềm này miễn phí cho bất kỳ mục đích nào,  */
/* bao gồm sao chép, sửa đổi, phân phối, bán lại…                         */
/*                                                                        */
/* Chỉ cần giữ nguyên thông tin bản quyền và nội dung giấy phép này trong */
/* các bản sao.                                                           */
/*                                                                        */
/* Đội ngũ phát triển mong rằng phần mềm được sử dụng đúng mục đích và    */
/* có trách nghiệm                                                        */
/**************************************************************************/

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.flast.entities.SaleProduct;
import vn.flast.entities.SkuAttributed;
import vn.flast.models.*;
import vn.flast.pagination.Ipage;
import vn.flast.repositories.AttributedRepository;
import vn.flast.repositories.AttributedValueRepository;
import vn.flast.repositories.GenericRepository;
import vn.flast.repositories.ProductAttributedRepository;
import vn.flast.repositories.ProductContentRepository;
import vn.flast.repositories.ProductPropertyRepository;
import vn.flast.repositories.ProductRepository;
import vn.flast.repositories.ProductSkusDetailsRepository;
import vn.flast.repositories.ProductSkusPriceRepository;
import vn.flast.repositories.ProductSkusRepository;
import vn.flast.repositories.ProviderRepository;
import vn.flast.repositories.WarehouseProductRepository;
import vn.flast.searchs.ProductFilter;
import vn.flast.service.SkuService;
import vn.flast.utils.Common;
import vn.flast.utils.CopyProperty;
import vn.flast.utils.MapUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductService {

    @PersistenceContext
    protected EntityManager entityManager;

    private final ProductContentRepository contentRepository;
    private final ProductRepository productsRepository;
    private final ProviderRepository providerRepository;
    private final ProductPropertyRepository productPropertyRepository;
    private final ProductAttributedRepository productAttributedRepository;
    private final ProductSkusRepository productSkusRepository;
    private final ProductSkusPriceRepository skusPriceRepository;
    private final ProductSkusDetailsRepository productSkusDetailsRepository;
    private final AttributedRepository attributedRepository;
    private final AttributedValueRepository attributedValueRepository;
    private final SkuService skuService;
    private final WarehouseProductRepository warehouseRepository;

    @Transactional(rollbackFor = Exception.class)
    public Product saveProduct(SaleProduct input) {

        Product nProduct = Optional.of(input)
            .map(SaleProduct::getId)
            .flatMap(productsRepository::findById)
            .orElseGet(Product::new);

        if(Objects.isNull(nProduct.getCode())) {
            Provider provider = providerRepository.findById(input.getProviderId()).orElseThrow(
                () -> new RuntimeException("Nhà cung cấp chưa tồn tại")
            );
            String twoLetterService = provider.getName().substring(0, 2);
            String code;
            do {
                String random = Common.getAlphaNumericString(8, false);
                code = (twoLetterService + random).toUpperCase();
            } while (productsRepository.findByCode(code) != null);
            nProduct.setCode(code);
        }

        CopyProperty.CopyIgnoreNull(input, nProduct);
        productsRepository.save(nProduct);

        /* Save Content */
        ProductContent content = input.getContent();
        if(Objects.nonNull(content)) {
            contentRepository.save(content);
        }

        /* Save Attributed */
        productAttributedRepository.deleteByProductId(nProduct.getId());
        List<ProductAttributed> productAttributedList = input.getListProperties().stream().flatMap(
            property -> property.getPropertyValueId().stream().map(
            propertyValueId -> {
                Attributed attributed = attributedRepository.findById(property.getAttributedId()).orElseThrow(
                    () -> new RuntimeException("không tồn tại bản ghi")
                );
                AttributedValue attributedValue = attributedValueRepository.findById(propertyValueId).orElseThrow(
                    () -> new RuntimeException("không tồn tại bản ghi")
                );
                ProductAttributed productAttributed = new ProductAttributed();
                productAttributed.setProductId(nProduct.getId());
                productAttributed.setAttributedId(property.getAttributedId());
                productAttributed.setAttributedValueId(propertyValueId);
                productAttributed.setName(attributed.getName());
                productAttributed.setValue(attributedValue.getValue());
                return productAttributed;
            }
        )).toList();
        productAttributedRepository.saveAll(productAttributedList);

        /* Save Property */
        productPropertyRepository.deleteByProductId(nProduct.getId());
        List<ProductProperty> productPropertyList = input.getListOpenInfo().stream().map(productProperty -> {
            ProductProperty property = new ProductProperty();
            CopyProperty.CopyIgnoreNull(productProperty, property);
            property.setName(productProperty.getName());
            property.setValue(productProperty.getValue());
            property.setProductId(nProduct.getId());
            return property;
        }).toList();
        productPropertyRepository.saveAll(productPropertyList);

        /* Save SKU */
        saveSkuProduct(input.getSkus(), nProduct.getId());
        /* clone để bỏ qua images và content */
        return nProduct.clone();
    }

    public SaleProduct findById(Long id) {
        GenericRepository.SpecificationBuilder<Product> builder = productsRepository
            .fetch("content")
            .fetch("images")
            .isEqual("id", id);

        var entity = builder.findOne().orElseThrow(
            () -> new RuntimeException("Bản ghi không tồn tại !")
        );

        List<Product> toList = Collections.singletonList(entity);
        List<SaleProduct> mProducts = generateProduct(toList);
        return mProducts.stream().findFirst().orElseThrow(
            () -> new RuntimeException("Generate product error !")
        );
    }

    public Ipage<?> fetch(ProductFilter filter) {

        int LIMIT = filter.limit();
        int PAGE = filter.page();

        Sort SORT = Sort.by(Sort.Direction.DESC, "id");
        GenericRepository.SpecificationBuilder<Product> builder = productsRepository
            .like("name", filter.name());

        Ipage<Product> iPage = builder.toPage(PAGE * LIMIT, LIMIT, SORT);
        List<Product> lists = iPage.getEmbedded();
        List<SaleProduct> mProducts = generateProduct(lists);
        return Ipage.generator(LIMIT, builder.countItem(), PAGE, mProducts);
    }

    private List<SaleProduct> generateProduct(List<Product> lists) {
        /* Map attributes */
        List<Long> pIds = lists.stream().map(Product::getId).toList();
        List<ProductAttributed> listAttrs = productAttributedRepository.findByProductIds(pIds);
        Map<Long, List<ProductAttributed>> mAttrs = MapUtils.groupBy(listAttrs, ProductAttributed::getProductId);

        /* Map Property */
        List<ProductProperty> mP2P =  productPropertyRepository.findByProductIds(pIds);
        Map<Long, List<ProductProperty>> mP2Ps = MapUtils.groupBy(mP2P, ProductProperty::getProductId);

        /* Map Warehouse */
        List<WarehouseProduct> warehouses = warehouseRepository.findByProductIds(pIds);
        warehouses.forEach(WarehouseProduct::addSKUDetailFormSkuInfo);
        Map<Long, List<WarehouseProduct>> mWPs = MapUtils.groupBy(warehouses, WarehouseProduct::getProductId);

        /* Map SKUS */
        List<ProductSkus> lSkus = skuService.listProductSkuAndDetail(pIds);
        Map<Long, List<ProductSkus>> mSkus = MapUtils.groupBy(lSkus, ProductSkus::getProductId);

        List<SaleProduct> results = new ArrayList<>();
        for (Product product : lists) {
            Long productId = product.getId();
            SaleProduct saleProduct = new SaleProduct();
            CopyProperty.CopyIgnoreNull(product, saleProduct);

            saleProduct.setListProperties(MapUtils.getOrEmpty(mAttrs, productId));
            saleProduct.setListOpenInfo(MapUtils.getOrEmpty(mP2Ps, productId));
            saleProduct.setWarehouses(MapUtils.getOrEmpty(mWPs, productId));
            saleProduct.setSkus(MapUtils.getOrEmpty(mSkus, productId));
            results.add(saleProduct);
        }
        return results;
    }

    public List<SaleProduct> findByListId(List<Long> ids) {
        var lists = productsRepository.findByListId(ids);
        return generateProduct(lists);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id){
        var data = productsRepository.findById(id).orElseThrow(
            () -> new RuntimeException("Bản ghi không tồn tại !")
        );
        productsRepository.delete(data);
    }

    private void saveSkuProduct(List<ProductSkus> skus, Long productId) {
        List<ProductSkus> skuModels = productSkusRepository.findByProductId(productId);
        Set<Long> inputSkuIds = skus.stream()
            .map(ProductSkus::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List<Long> skusToDelete = skuModels.stream()
            .map(ProductSkus::getId)
            .filter(id -> !inputSkuIds.contains(id))
            .collect(Collectors.toList());
        if (!skusToDelete.isEmpty()) {
            productSkusRepository.delProductSkus(productId, skusToDelete);
        }

        List<Long> attrIds = skus.stream()
            .map(ProductSkus::getSku)
            .flatMap(List::stream)
            .map(SkuAttributed::getAttributedId)
            .filter(Objects::nonNull)
            .toList();
        List<Attributed> lAttr = attributedRepository.findAllById(attrIds);
        Map<Long, Attributed> mAttrs = MapUtils.toIdentityMap(lAttr, Attributed::getId);

        List<Long> attrValueIds = skus.stream()
            .map(ProductSkus::getSku)
            .flatMap(List::stream)
            .map(SkuAttributed::getAttributedValueId)
            .filter(Objects::nonNull)
            .toList();
        List<AttributedValue> lAttrValue = attributedValueRepository.findAllById(attrValueIds);
        Map<Long, AttributedValue> mAttrValues = MapUtils.toIdentityMap(lAttrValue, AttributedValue::getId);

        skus.forEach(productSkus -> {
            ProductSkus mSku = new ProductSkus();
            CopyProperty.CopyIgnoreNull(productSkus, mSku);
            mSku.setProductId(productId);
            ProductSkus savedSku = productSkusRepository.save(mSku);

            /* Save Price Range */
            skusPriceRepository.deleteBySkuId(skusToDelete);
            productSkus.getSkuPrices().forEach(priceRange -> {
                ProductSkusPrice price = new ProductSkusPrice();
                CopyProperty.CopyIgnoreNull(priceRange, price);
                price.setProductId(productId);
                price.setPrice(priceRange.getPrice());
                price.setProductSku(savedSku);
                skusPriceRepository.save(price);
            });

            /* Save new Sku Details */
            productSkusDetailsRepository.delProductSkus(skusToDelete);
            List<ProductSkusDetails> skuDetails = new ArrayList<>();
            productSkus.getSku().forEach(sku -> {
                ProductSkusDetails skusDetail = new ProductSkusDetails();
                Attributed attributed = mAttrs.get(sku.getAttributedId());
                if(Objects.isNull(attributed)) {
                    throw new RuntimeException("không tồn tại attributed");
                }

                AttributedValue attributedValue = mAttrValues.get(sku.getAttributedValueId());
                if(Objects.isNull(attributedValue)) {
                    throw new RuntimeException("không tồn tại attributedValue");
                }

                CopyProperty.CopyIgnoreNull(sku, skusDetail);
                skusDetail.setProductId(productId);
                skusDetail.setName(attributed.getName());
                skusDetail.setValue(attributedValue.getValue());
                skusDetail.setProductSku(savedSku);
                skuDetails.add(skusDetail);
            });
            productSkusDetailsRepository.saveAll(skuDetails);
        });
    }
}
