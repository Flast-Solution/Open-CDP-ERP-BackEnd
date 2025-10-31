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
        if(input.getCode() == null) {
            Provider provider = providerRepository.findById(input.getProviderId()).orElseThrow(
                () -> new RuntimeException("Nhà cung cấp chưa tồn tại")
            );
            String twoLetterService = provider.getName().substring(0, 2);
            String code;
            do {
                String random = Common.getAlphaNumericString(8, false);
                code = (twoLetterService + random).toUpperCase();
            } while (productsRepository.findByCode(code) != null);
            input.setCode(code);
        }

        Product product = new Product();
        CopyProperty.CopyIgnoreNull(input, product);
        var data = productsRepository.save(product);

        /* Save Content */
        ProductContent content = input.getContent();
        if(Objects.nonNull(content)) {
            contentRepository.save(content);
        }

        /* Save Attributed */
        productAttributedRepository.deleteByProductId(data.getId());
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
                productAttributed.setProductId(data.getId());
                productAttributed.setAttributedId(property.getAttributedId());
                productAttributed.setAttributedValueId(propertyValueId);
                productAttributed.setName(attributed.getName());
                productAttributed.setValue(attributedValue.getValue());
                return productAttributed;
            }
        )).toList();
        productAttributedRepository.saveAll(productAttributedList);

        /* Save Property */
        productPropertyRepository.deleteByProductId(data.getId());
        List<ProductProperty> productPropertyList = input.getListOpenInfo().stream().map(productProperty -> {
            ProductProperty property = new ProductProperty();
            CopyProperty.CopyIgnoreNull(productProperty, property);
            property.setName(productProperty.getName());
            property.setValue(productProperty.getValue());
            property.setProductId(data.getId());
            return property;
        }).toList();
        productPropertyRepository.saveAll(productPropertyList);

        /* Save SKU */
        saveSkuProduct(input.getSkus(), data.getId());
        return data;
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

    public void saveSkuProduct(List<ProductSkus> input, Long productId){
        var skuModels = productSkusRepository.findByProductId(productId);
        Set<Long> inputSkuIds = input.stream()
            .map(ProductSkus::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List<Long> skusToDelete = skuModels.stream()
            .map(ProductSkus::getId)
            .filter(id -> !inputSkuIds.contains(id))
            .collect(Collectors.toList());
        if (!skusToDelete.isEmpty()) {
            productSkusRepository.updateDelProductSkus(productId,skusToDelete);
        }
        input.forEach(productSkus -> {
            ProductSkus skus = new ProductSkus();
            CopyProperty.CopyIgnoreNull(productSkus, skus);
            skus.setProductId(productId);
            ProductSkus savedSku = productSkusRepository.save(skus);
            /* Save Price Range */
            skusPriceRepository.deleteBySkuId(savedSku.getId());
            productSkus.getSkuPrices().forEach(priceRange -> {
                ProductSkusPrice price = new ProductSkusPrice();
                CopyProperty.CopyIgnoreNull(priceRange, price);
                price.setProductId(productId);
                price.setSkuId(savedSku.getId());
                price.setPrice(priceRange.getPrice());
                skusPriceRepository.save(price);
            });
            var skuDetailOld = productSkusDetailsRepository.findBySkuId(savedSku.getId());
            Set<Integer> inputSkuDetailIds = productSkus.getSku().stream()
                .map(SkuAttributed::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            List<Integer> skusDetailToDelete = skuDetailOld.stream()
                .map(ProductSkusDetails::getId)
                .filter(id -> !inputSkuDetailIds.contains(id))
                .collect(Collectors.toList());
            if (!skusToDelete.isEmpty()) {
                productSkusDetailsRepository.updateDelProductSkus(productId, skusDetailToDelete);
            }
            productSkus.getSku().forEach(sku -> {
                ProductSkusDetails skusDetails = new ProductSkusDetails();
                Attributed attributed = attributedRepository.findById(sku.getAttributedId()).orElseThrow(
                    () -> new RuntimeException("không tồn tại bản ghi")
                );
                AttributedValue attributedValue = attributedValueRepository.findById(sku.getAttributedValueId()).orElseThrow(
                    () -> new RuntimeException("không tồn tại bản ghi")
                );
                CopyProperty.CopyIgnoreNull(sku, skusDetails);
                skusDetails.setSkuId(savedSku.getId());
                skusDetails.setProductId(productId);
                skusDetails.setName(attributed.getName());
                skusDetails.setValue(attributedValue.getValue());
                productSkusDetailsRepository.save(skusDetails);
            });
        });
    }
}
