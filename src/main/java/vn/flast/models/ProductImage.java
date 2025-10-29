package vn.flast.models;
/**************************************************************************/
/*  ProductImage.java                                                     */
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

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.flast.utils.NumberUtils;

@Table(name = "product_image")
@Entity
@Getter @Setter
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "is_slideshow")
    private Integer isSlideshow;

    @Column(name = "is_featured")
    private Integer isFeatured;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id",referencedColumnName = "id", insertable=false, updatable=false)
    private Product product;

    @PrePersist
    public void beforePersist() {
        if(NumberUtils.isNull(isSlideshow)) {
            isSlideshow = 0;
        }
        if(NumberUtils.isNull(isFeatured)) {
            isFeatured = 0;
        }
    }

    public void toggleFeatured() {
        isFeatured ^= 1;
    }

    public boolean isFeatured() {
        return NumberUtils.isNotNull(isFeatured) && isFeatured == 1;
    }
}
