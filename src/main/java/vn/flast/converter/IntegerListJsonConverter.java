package vn.flast.converter;
/**************************************************************************/
/*  IntegerListJsonConverter.java                                         */
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

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import vn.flast.utils.JsonUtils;
import java.util.List;
import java.util.Objects;

/** Convert List<Integer> ↔ JSON String */
@Converter(autoApply = false)
public class IntegerListJsonConverter implements AttributeConverter<List<Integer>, String> {

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        if (Objects.isNull(attribute) || attribute.isEmpty()) {
            return "[]";
        }
        return JsonUtils.toJson(attribute);
    }

    @Override
    public List<Integer> convertToEntityAttribute(String dbData) {
        return JsonUtils.Json2ListObject(dbData, Integer.class);
    }
}
