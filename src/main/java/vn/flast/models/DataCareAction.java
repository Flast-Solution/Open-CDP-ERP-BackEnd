package vn.flast.models;
/**************************************************************************/
/*  DataCareAction.java                                                   */
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
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.util.Date;
import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "data_care_action")
@NoArgsConstructor
@Getter @Setter
public class DataCareAction {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true)
    private Integer id;

    @Column(name = "sso_id")
    private String ssoId;

    @Column(name = "content")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at")
    private Date inTime;

    @JsonBackReference
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_care_id", referencedColumnName = "id")
    private DataCare dataCare;
}
