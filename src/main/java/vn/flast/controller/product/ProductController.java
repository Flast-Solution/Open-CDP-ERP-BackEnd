package vn.flast.controller.product;
/**************************************************************************/
/*  ProductController.java                                                */
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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.flast.entities.ImageSlideUpdate;
import vn.flast.entities.MyResponse;
import vn.flast.entities.ProductImageMultiPart;
import vn.flast.entities.SaleProduct;
import vn.flast.exception.ResourceNotFoundException;
import vn.flast.models.ProductContent;
import vn.flast.models.ProductImage;
import vn.flast.repositories.ProductCategoryRepository;
import vn.flast.repositories.ProductImageRepository;
import vn.flast.repositories.ProductRepository;
import vn.flast.searchs.ProductFilter;
import vn.flast.service.product.ProductContentService;
import vn.flast.service.product.ProductService;
import vn.flast.utils.UploadsUtils;
import vn.flast.validator.ValidationErrorBuilder;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/product")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ProductImageRepository imageRepository;
    private final ProductContentService contentService;
    private final ProductCategoryRepository mPCategoryRepository;

    @PostMapping("/save")
    public MyResponse<?> saveProduct(@Valid @RequestBody SaleProduct input, Errors errors) {
        if(errors.hasErrors()) {
            var newErrors = ValidationErrorBuilder.fromBindingErrors(errors);
            return MyResponse.response(newErrors, "Lỗi tham số đầu vào");
        }
        var data = productService.saveProduct(input);
        return MyResponse.response(data, "Cập nhật thành công .!");
    }

    @PostMapping("/update-content")
    public MyResponse<?> updateContent(@Valid @RequestBody ProductContent input, Errors errors) {
        if(errors.hasErrors()) {
            var newErrors = ValidationErrorBuilder.fromBindingErrors(errors);
            return MyResponse.response(newErrors, "Lỗi tham số đầu vào");
        }
        var data = contentService.save(input);
        return MyResponse.response(data, "Cập nhật nội dung thành công .!");
    }

    @GetMapping("/fetch")
    public MyResponse<?> fetch(ProductFilter filter) {
        var data = productService.fetch(filter);
        return MyResponse.response(data);
    }

    @GetMapping("/find-by-id")
    public MyResponse<?> findId(@RequestParam("id") Long id) {
        var mProduct = productService.findById(id);
        return MyResponse.response(mProduct);
    }

    @PostMapping("/delete")
    public MyResponse<?> delete(@RequestParam Long id) {
        productRepository.deleteById(id);
        mPCategoryRepository.deleteAllByProductId(id);
        return MyResponse.response("Xáo bản ghi thành công .!");
    }

    @PostMapping(path = "/upload-multi-part", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public MyResponse<?> uploadFileMedia(@ModelAttribute ProductImageMultiPart imageProduct) throws Exception {
        String file = UploadsUtils.upload(imageProduct, imageProduct.getImage());
        ProductImage mImage = imageProduct.transform();
        mImage.setFileName(file);

        imageRepository.save(mImage);
        return MyResponse.response(mImage, "Tải lên file thành công !");
    }

    @PostMapping("/remove-file/{id}")
    public MyResponse<?> removeFile(@PathVariable Integer id) {
        imageRepository.deleteById(id);
        return MyResponse.response("oke");
    }

    @GetMapping("/find-list-id")
    public MyResponse<?> findListId(@RequestParam List<Long> ids) {
        var data = productService.findByListId(ids);
        return MyResponse.response(data);
    }

    @PostMapping("/image-featured/{imageId}")
    public MyResponse<?> imageFeatured(@PathVariable Integer imageId) {
        var model = imageRepository.findById(imageId).orElseThrow(
            () -> new ResourceNotFoundException("")
        );
        model.toggleFeatured();
        if(model.isFeatured()) {
            productRepository.updateImage(model.getFileName(), model.getProductId());
        }
        imageRepository.save(model);
        return MyResponse.response(model, "Cập nhật hình ảnh thành công !");
    }

    @PostMapping("/image-slide")
    public MyResponse<?> imageSlideUpdate(@Valid @RequestBody ImageSlideUpdate image) {
        var model = imageRepository.findById(image.imageId()).orElseThrow(
            () -> new ResourceNotFoundException("")
        );
        imageRepository.resetSlideShow(image.productId());
        if(image.checked()) {
            model.setIsSlideshow(1);
            imageRepository.save(model);
        }
        return MyResponse.response(model, "Cập nhật hình ảnh thành công !");
    }
}
