package vn.flast.service;
/**************************************************************************/
/*  FlastNoteService.java                                                 */
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import vn.flast.entities.NoteAction;
import vn.flast.exception.ResourceNotFoundException;
import vn.flast.models.Data;
import vn.flast.models.FlastNote;
import vn.flast.pagination.Ipage;
import vn.flast.repositories.FlastNoteRepository;
import vn.flast.repositories.GenericRepository;
import vn.flast.searchs.NoteFilter;
import vn.flast.utils.BuilderParams;
import vn.flast.utils.Common;
import java.util.Optional;

@Service("flastNoteServer")
@RequiredArgsConstructor
public class FlastNoteService {

    private final FlastNoteRepository noteRepository;

    public Ipage<FlastNote> fetch(NoteFilter filter) {

        int LIMIT = filter.limit();
        int PAGE = filter.page();

        Sort SORT = Sort.by(Sort.Direction.DESC, "id");
        GenericRepository.SpecificationBuilder<FlastNote> builder = noteRepository
            .isEqual("objectType", filter.objectType())
            .isEqual("objectId", filter.objectId())
            .group()
                .isEqual("userId", filter.userId())
                .or()
                .isEqual("replyId", filter.replyId())
            .endGroup()
            .between("updatedAt", filter.from(), filter.to());
        return builder.toPage(PAGE * LIMIT, LIMIT, SORT);
    }

    public void createLeadNote(Data lead) {

        BuilderParams body = BuilderParams.create()
            .addParam("name", lead.getProductName())
            .addParam("note", lead.getNote());

        FlastNote flastNote = factoryAsLead();
        flastNote.setObjectId(lead.getId());
        flastNote.setDataType(FlastNote.DATA_TYPE_LEAD_NOTE);
        flastNote.setReplyId(lead.getSaleId());

        NoteAction noteAction = NoteAction.create().addNote(body).addReply("{}");
        flastNote.setContent(noteAction);
        noteRepository.save(flastNote);
    }

    public FlastNote createReply(FlastNote fNote) {
        FlastNote flastNote = Optional.ofNullable(fNote)
            .map(FlastNote::getId)
            .flatMap(noteRepository::findById)
            .orElseThrow(() -> new ResourceNotFoundException(""));
        return noteRepository.save(flastNote);
    }

    public FlastNote createNote(FlastNote fNote) {
        return noteRepository.save(fNote);
    }

    private FlastNote factoryAsLead() {
        FlastNote flastNote = new FlastNote();
        flastNote.setObjectType(FlastNote.OBJECT_TYPE_LEAD);
        assignUser(flastNote);
        return flastNote;
    }

    private void assignUser(FlastNote flastNote) {
        flastNote.setUserId(Common.getUserId());
        flastNote.setUserNote(Common.getSsoId());
    }
}
