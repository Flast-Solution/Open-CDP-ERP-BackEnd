package vn.flast.service;
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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.flast.models.ProductSkus;
import vn.flast.repositories.ProductSkusRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class SkuService {

    private final ProductSkusRepository productSkusRepository;

    public List<ProductSkus> listProductSkuAndDetail(List<Long> productIds) {
        if(Objects.isNull(productIds) || productIds.isEmpty()) {
            return new ArrayList<>();
        }
        return productSkusRepository
            .fetch("skuPrices")
            .fetch("skuDetails")
            .in("productId", productIds).findAll();
    }
}
