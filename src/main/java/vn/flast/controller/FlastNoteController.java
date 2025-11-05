package vn.flast.controller;
/**************************************************************************/
/*  FlastNoteController.java                                              */
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
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.flast.entities.MyResponse;
import vn.flast.models.FlastNote;
import vn.flast.searchs.NoteFilter;
import vn.flast.service.FlastNoteService;
import vn.flast.validator.ValidationErrorBuilder;

@RestController
@RequiredArgsConstructor
@RequestMapping("/note")
public class FlastNoteController {

    private final FlastNoteService noteService;

    @PostMapping("/save")
    public MyResponse<?> save(@Valid @RequestBody FlastNote note, Errors errors) {
        if(errors.hasErrors()) {
            var newErrors = ValidationErrorBuilder.fromBindingErrors(errors);
            return MyResponse.response(newErrors, "Lỗi tham số đầu vào");
        }
        FlastNote mNote = noteService.createNote(note);
        return MyResponse.response(mNote, "Thực hiện lưu note thành công !");
    }

    @PostMapping("/reply")
    public MyResponse<?> reply(@Valid @RequestBody FlastNote note, Errors errors) {
        if(errors.hasErrors()) {
            var newErrors = ValidationErrorBuilder.fromBindingErrors(errors);
            return MyResponse.response(newErrors, "Lỗi tham số đầu vào");
        }
        FlastNote mNote = noteService.createReply(note);
        return MyResponse.response(mNote, "Thực hiện lưu note thành công !");
    }

    @GetMapping("/fetch")
    public MyResponse<?> fetch(NoteFilter filter) {

        var data = noteService.fetch(filter);
        if(filter.asList()) {
            return MyResponse.response(data.getEmbedded());
        }

        if(!filter.asFirst()) {
            return MyResponse.response(data);
        }

        FlastNote firstItem = data.getEmbedded().stream().findFirst().orElseThrow(
            () -> new IllegalArgumentException("Embedded list is empty or null")
        );
        return MyResponse.response(firstItem);
    }
}
