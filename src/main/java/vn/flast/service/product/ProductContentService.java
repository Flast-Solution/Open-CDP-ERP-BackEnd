package vn.flast.service.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.flast.models.ProductCategory;
import vn.flast.models.ProductContent;
import vn.flast.repositories.ProductCategoryRepository;
import vn.flast.repositories.ProductContentRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ProductContentService {

    private final ProductCategoryRepository mPCategoryRepository;
    private final ProductContentRepository mContentRepository;

    public void updateCategory(ProductContent input) {
        Long productId = input.getProductId();
        List<Long> newCategoryIds = input.getListCategories();

        /* === XÓA Category CŨ === */
        List<ProductCategory> oldCategories = mPCategoryRepository.findProductId(productId);
        List<Long> oldIds = oldCategories.stream().map(ProductCategory::getCategoryId).toList();
        List<Long> idsToDelete = oldIds.stream().filter(id -> !newCategoryIds.contains(id)).toList();

        if (!idsToDelete.isEmpty()) {
            mPCategoryRepository.deleteByProductIdAndCategoryIdIn(productId, idsToDelete);
        }

        if (newCategoryIds.isEmpty() && !oldIds.isEmpty()) {
            mPCategoryRepository.deleteAllByProductId(productId);
        }

        /* === THÊM Category MỚI === */
        Set<Long> existingIds = oldCategories.stream()
            .map(ProductCategory::getCategoryId)
            .collect(Collectors.toSet());

        List<ProductCategory> toInsert = new ArrayList<>();
        for (Long newCategoryId : newCategoryIds) {
            if (existingIds.contains(newCategoryId)) {
                continue;
            }
            ProductCategory mPCategory = new ProductCategory();
            mPCategory.setProductId(productId);
            mPCategory.setCategoryId(newCategoryId);
            toInsert.add(mPCategory);
        }

        if (!toInsert.isEmpty()) {
            mPCategoryRepository.saveAll(toInsert);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductContent save(ProductContent input) {
        updateCategory(input);
        return mContentRepository.save(input);
    }
}
