package vn.flast.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import vn.flast.models.ProductImage;
import vn.flast.utils.Common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@NoArgsConstructor
@Getter @Setter
public class ProductImageMultiPart implements FilesInterface {

    private MultipartFile image;
    private Boolean isFeatured;
    private Boolean isSlideshow;
    private Long productId;

    @Override
    public String createFolderUpload() {
        String FOLDER_UPLOAD = "/uploads/product/" + LocalDate.now().format(DateTimeFormatter.ofPattern("MMyyyy"));
        return Common.makeFolder(FOLDER_UPLOAD);
    }

    @Override
    public List<String> filePass() {
        return List.of("png", "jpg", "jpeg", "webp");
    }

    public ProductImage transform() {
        ProductImage mImage = new ProductImage();
        mImage.setIsFeatured(Boolean.compare(isFeatured, false));
        mImage.setIsSlideshow(Boolean.compare(isSlideshow, false));
        mImage.setProductId(productId);
        return mImage;
    }
}
