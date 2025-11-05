package vn.flast.searchs;
/**************************************************************************/
/*  NoteFilter.java                                                       */
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

import org.springframework.format.annotation.DateTimeFormat;
import vn.flast.utils.NumberUtils;
import java.util.Date;
import java.util.Objects;

public record NoteFilter(
    String objectType,
    Integer page,
    Integer limit,
    Integer objectId,
    Integer userId,
    Integer replyId,
    Boolean asList,
    Boolean asFirst,
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    Date from,
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    Date to
) implements BaseFilter {
    @Override
    public Integer page() {
        return NumberUtils.isNull(page) ? 0 : (page - 1);
    }

    @Override
    public Integer limit() {
        return NumberUtils.isNull(limit) ? 20 : limit;
    }

    @Override
    public Boolean asList() {
        return Objects.nonNull(asList) && asList;
    }

    @Override
    public Boolean asFirst() {
        return Objects.nonNull(asFirst) && asFirst;
    }
}
