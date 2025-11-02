package vn.flast.domains.cskh;
/**************************************************************************/
/*  package vn.flast.domains.cskh                                         */
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.flast.controller.BaseController;
import vn.flast.domains.order.OrderService;
import vn.flast.entities.lead.LeadCareFilter;
import vn.flast.entities.lead.NoOrderFilter;
import vn.flast.models.*;
import vn.flast.pagination.Ipage;
import vn.flast.repositories.CustomerPersonalRepository;
import vn.flast.repositories.DataCareRepository;
import vn.flast.repositories.DataRepository;
import vn.flast.repositories.GenericRepository;
import vn.flast.service.DataService;
import vn.flast.utils.*;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataCareService extends BaseController {

    private final DataCareRepository dataCareRepository;
    private final DataRepository dataRepository;
    private final CustomerPersonalRepository customerRepository;
    private final OrderService orderService;

    @PersistenceContext
    private EntityManager entityManager;

    public Ipage<DataCare> fetch(LeadCareFilter filter) {

        int PAGE =  filter.page();
        int LIMIT = 10;

        Sort SORT = Sort.by(Sort.Direction.DESC, "id");
        GenericRepository.SpecificationBuilder<DataCare> builder = dataCareRepository
            .fetch("active")
            .isEqual("objectType", filter.getType())
            .isEqual("cause", filter.getCause())
            .isEqual("customerId", filter.getCustomerId())
            .between("inTime", filter.getFrom(), filter.getTo());
        return builder.toPage(PAGE * LIMIT, LIMIT, SORT);
    }

    public Ipage<Data> fetchLead3Day(NoOrderFilter filter){
        int LIMIT = filter.getLimit();
        int OFFSET = filter.page() * LIMIT;

        final String totalSQL = "FROM `data` d left join `data_care` r on d.id = r.object_id AND r.object_type = 'lead'";
        SqlBuilder sqlBuilder = SqlBuilder.init(totalSQL);
        sqlBuilder.addIsEmpty("r.object_id");
        sqlBuilder.addIntegerEquals("d.sale_id", filter.getUserId());
        sqlBuilder.addStringEquals("d.customer_mobile", filter.getPhone());
        sqlBuilder.addIntegerEquals("d.source", filter.getSource());
        sqlBuilder.addDateBetween("d.in_time", filter.getFrom(), filter.getTo());
        sqlBuilder.addNotIn("d.status", DataService.DATA_STATUS.THANH_CO_HOI.getStatusCode());

        Date dayBefore = DateUtils.addDays(new Date(), -3);
        sqlBuilder.addDateLessThan("d.in_time", dayBefore);

        String finalQuery = sqlBuilder.builder();
        var countQuery = entityManager.createNativeQuery(sqlBuilder.countQueryString());
        Long count = sqlBuilder.countOrSumQuery(countQuery);

        var nativeQuery = entityManager.createNativeQuery("SELECT d.* " + finalQuery , Data.class);
        nativeQuery.setMaxResults(LIMIT);
        nativeQuery.setFirstResult(OFFSET);

        var listData = EntityQuery.<Data>getListOfNativeQuery(nativeQuery);
        return Ipage.generator(LIMIT, count, filter.page(), listData);
    }

    @Transactional
    public DataCare update3Day(DataCare input) {
        if(Optional.ofNullable(input.getObjectId()).isEmpty()) {
            throw new RuntimeException("Lead id does not exist .!");
        }
        var lead = dataRepository.findById(input.getObjectId()).orElseThrow(
            () -> new RuntimeException("No lead exists .!")
        );
        var dataCare = new DataCare();
        CopyProperty.CopyIgnoreNull(input, dataCare);

        dataCare.setObjectId(lead.getId());
        dataCare.setObjectType(DataCare.OBJECT_TYPE_LEAD);
        dataCare.setUserName(getInfo().getSsoId());

        var customer = customerRepository.findByPhone(lead.getCustomerMobile());
        dataCare.setCustomerId(Optional.ofNullable(customer).map(CustomerPersonal::getId).orElse(0L));

        if (Objects.nonNull(input.getActive())) {
            DataCareAction active = new DataCareAction();
            CopyProperty.CopyIgnoreNull(input.getActive(), active);
            active.setSsoId(getInfo().getSsoId());
            active.setDataCare(dataCare);
            dataCare.setActive(active);
        }

        dataCare.setTitle(lead.getProductName());
        dataCareRepository.save(dataCare);

        /* Cập nhật lead */
        lead.setPreSaleCall(Data.PreSaleCall.DA_LIEN_HE.value());
        dataRepository.save(lead);
        return dataCare;
    }

    public Ipage<CustomerOrder> fetchCoHoiOrder(NoOrderFilter filter){
        int LIMIT = filter.getLimit();
        int OFFSET = filter.page() * LIMIT;

        String totalSQL = "FROM `customer_order` d left join `data_care` r on d.id = r.object_id ";
        totalSQL = totalSQL.concat("AND r.object_type = '").concat(filter.getType()).concat("'");

        SqlBuilder sqlBuilder = SqlBuilder.init(totalSQL);
        sqlBuilder.addIsEmpty("r.object_id");
        sqlBuilder.addIntegerEquals("d.user_create_id", filter.getUserId());
        sqlBuilder.addStringEquals("d.customer_mobile_phone", filter.getPhone());
        sqlBuilder.addIntegerEquals("d.source", filter.getSource());
        sqlBuilder.addStringEquals("d.type", filter.getType());

        if("cohoi".equals(filter.getType())) {
            Date dayBefore = DateUtils.addDays(new Date(), -7);
            sqlBuilder.addDateLessThan("d.created_at", dayBefore);
        }
        String finalQuery = sqlBuilder.builder();
        var countQuery = entityManager.createNativeQuery(sqlBuilder.countQueryString());
        Long count = sqlBuilder.countOrSumQuery(countQuery);

        var nativeQuery = entityManager.createNativeQuery("SELECT d.* " + finalQuery , CustomerOrder.class);
        nativeQuery.setMaxResults(LIMIT);
        nativeQuery.setFirstResult(OFFSET);

        List<CustomerOrder> listOrders = EntityQuery.getListOfNativeQuery(nativeQuery);
        List<CustomerOrder> orders = orderService.transformDetails(listOrders);
        return Ipage.generator(LIMIT, count, filter.page(), orders);
    }

    public DataCare updatedOrder(DataCare dataCare) {
        if(Optional.ofNullable(dataCare.getObjectId()).isEmpty()) {
            throw new RuntimeException("ObjectId id does not exist .!");
        }
        var order = orderService.view(dataCare.getObjectId());
        var model = new DataCare();
        CopyProperty.CopyIgnoreNull(dataCare, model);
        model.setObjectId(order.getId());
        model.setUserName(getInfo().getSsoId());
        model.setTitle(order.getCode());

        var customer = customerRepository.findById(order.getCustomerId()).orElseThrow(
            () -> new RuntimeException("")
        );
        model.setCustomerId(customer.getId());
        dataCareRepository.save(model);
        return model;
    }
}
