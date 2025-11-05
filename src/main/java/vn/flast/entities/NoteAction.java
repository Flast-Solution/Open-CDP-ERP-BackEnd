package vn.flast.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.flast.utils.JsonUtils;

@Getter @Setter
@NoArgsConstructor
public class NoteAction {

    private String note;
    private String reply;

    public static NoteAction create() {
        return new NoteAction();
    }

    public NoteAction addNote(Object data) {
        note = (data instanceof String) ? (String) data : JsonUtils.toJson(data);
        return this;
    }

    public NoteAction addReply(Object data) {
        reply = (data instanceof String) ? (String) data : JsonUtils.toJson(data);
        return this;
    }
}
