package vn.flast.entities.customer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.flast.utils.NumberUtils;
import java.util.List;

@NoArgsConstructor
@Getter @Setter
public class FeedbackRequest {

    private List<String> issues;
    private Integer rating;
    private Integer satisfactionPercent;
    private String newFeatures;
    private String supportRequest;

    public Integer getSatisfactionPercent() {
        if(NumberUtils.isNull(satisfactionPercent) && NumberUtils.isNotNull(rating)) {
            return rating * 10;
        }
        return satisfactionPercent;
    }
}
