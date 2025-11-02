package vn.flast.converter;
/**************************************************************************/
/*  FeedbackRequestJsonConverter.java                                     */
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import vn.flast.entities.customer.FeedbackRequest;

@Converter(autoApply = false)
public class FeedbackRequestJsonConverter implements AttributeConverter<FeedbackRequest, String> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final TypeReference<FeedbackRequest> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(FeedbackRequest attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot convert FeedbackRequest to JSON", e);
        }
    }

    @Override
    public FeedbackRequest convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty() || "null".equalsIgnoreCase(dbData)) {
            return null;
        }
        try {
            return mapper.readValue(dbData, TYPE_REF);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot convert JSON to FeedbackRequest", e);
        }
    }
}