package vn.flast.domains.order;
/**************************************************************************/
/*  vn.flast.domains.order.OrderService.java                              */
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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.flast.entities.order.OrderDetail;
import vn.flast.entities.order.OrderResponse;
import vn.flast.exception.ResourceNotFoundException;
import vn.flast.models.CustomerOrder;
import vn.flast.models.CustomerOrderDetail;
import vn.flast.models.CustomerOrderStatus;
import vn.flast.models.Data;
import vn.flast.orchestration.EventDelegate;
import vn.flast.orchestration.EventTopic;
import vn.flast.orchestration.Message;
import vn.flast.orchestration.MessageInterface;
import vn.flast.orchestration.Publisher;
import vn.flast.pagination.Ipage;
import vn.flast.repositories.*;
import vn.flast.searchs.OrderFilter;
import vn.flast.service.DataService;
import vn.flast.utils.*;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
@Service("orderService")
public class OrderService  implements Publisher, Serializable {

    private EventDelegate eventDelegate;

    @Autowired
    private EntityManager entityManager;

    private final CustomerOrderRepository orderRepository;
    private final CustomerPersonalRepository customerRepository;
    private final CustomerContractRepository customerContractRepository;
    private final DataRepository dataRepository;
    private final CustomerOrderDetailRepository detailRepository;
    private final CustomerOrderStatusRepository statusOrderRepository;

    @Transactional(rollbackFor = Exception.class)
    public CustomerOrder saveOpportunity(OrderInput input) {

        var order = new CustomerOrder();
        order.setCode(OrderUtils.createOrderCode());
        order.setUserCreateUsername(Common.getSsoId());
        order.setUserCreateId(Common.getUserId());
        input.transformOrder(order);

        List<Long> removedIds = new ArrayList<>();
        if(NumberUtils.isNotNull(order.getId())) {
            var entity = orderRepository.findById(order.getId()).orElseThrow(
                () -> new ResourceNotFoundException("Not Found Order .!")
            );
            if(Common.CollectionIsEmpty(input.details())) {
                throw new RuntimeException("Lỗi sửa chưa cập nhật đơn ! ");
            }
            List<Long> detailInputIds = input.details().stream()
                .map(OrderDetail::getDetailId)
                .filter(NumberUtils::isNotNull).toList();
            List<Long> detailIds = entity.getDetails().stream().map(CustomerOrderDetail::getId).toList();

            removedIds = detailIds.stream().filter(id -> !detailInputIds.contains(id)).toList();
            Iterator<CustomerOrderDetail> iterator = entity.getDetails().iterator();
            while (iterator.hasNext()) {
                CustomerOrderDetail detail = iterator.next();
                if (removedIds.contains(detail.getId())) {
                    iterator.remove();
                }
            }
            CopyProperty.CopyIgnoreNull(entity, order);
        } else {
            Data data = null;
            if(NumberUtils.isNotNull(input.dataId())) {
                data = dataRepository.findById(input.dataId()).orElseThrow(
                    () -> new ResourceNotFoundException("Lead Id Not Found .!")
                );
            }
            if(Objects.isNull(data)) {
                data = dataRepository.findFirstByPhone(input.customer().getMobile()).orElseThrow(
                    () -> new ResourceNotFoundException("Customer Lead Not Found .!")
                );
            }
            data.setStatus(DataService.DATA_STATUS.THANH_CO_HOI.getStatusCode());
            dataRepository.save(data);

            order.setDataId(data.getId());
            order.setSource(data.getSource());
            order.setPaid(0.0);
        }

        var modelStatus = statusOrderRepository.findStartOrder().orElseThrow(
            () -> new RuntimeException("Trạng thái mới chưa được cấu hình")
        );
        order.setStatus(modelStatus.getId());

        var listDetails = input.transformOrderDetail(order, order.getStatus());
        if (!removedIds.isEmpty()) {
            Set<Long> removedIdSet = new HashSet<>(removedIds);
            listDetails.removeIf(detail -> removedIdSet.contains(detail.getId()));
        }
        order.setDetails(listDetails);
        OrderUtils.calculatorPrice(order);

        /* CascadeType.ALL thì không cần detail save */
        orderRepository.save(order);
        listDetails.forEach(detail -> detail.setCustomerOrder(order));
        listDetails.forEach(detail -> detail.setCustomerOrderId(order.getId()));

        this.sendMessageOnOrderChange(order);
        return order;
    }

    public void save(CustomerOrder order) {
        orderRepository.save(order);
        this.sendMessageOnOrderChange(order);
    }

    public Map<Integer, List<CustomerOrder>> fetchKanban() {
        List<Integer> status = statusOrderRepository.findAll().stream().map(CustomerOrderStatus::getId).toList();
        if(status.isEmpty()) {
            throw new RuntimeException("Chưa cấu hình trạng thái đơn hàng");
        }

        Map<Integer, List<CustomerOrder>> mOrders = status.stream().collect(Collectors.toMap(
            i -> i,
            i -> new ArrayList<>()
        ));
        var mStatus = detailRepository.findOrderGroupsByStatus(status);
        if (mStatus.isEmpty()) {
            return mOrders;
        }

        /* Map [{status: 1, orderIds: [34012, 34011, ...]}] */
        Map<Integer, List<Long>> statusToOrderIds = mStatus.stream().collect(Collectors.toMap(
            row -> ((Number) row[0]).intValue(),
            row -> JsonUtils.Json2ListObject((String) row[1], Long.class),
            (existing, replacement) -> existing
        ));
        List<Long> allOrderIds = statusToOrderIds.values()
            .stream()
            .flatMap(List::stream)
            .toList();
        if(allOrderIds.isEmpty()) {
            return mOrders;
        }

        List<CustomerOrder> orders = orderRepository
            .fetch("details")
            .in("id", allOrderIds)
            .findAll();
        for (CustomerOrder order : orders) {
            List<Integer> detailStatus = order.getDetails().stream().map(CustomerOrderDetail::getStatus).toList();
            addOrderToBuckets(order, detailStatus, mOrders);
        }
        return mOrders;
    }

    /**
     * Thêm một {@code order} vào các bucket tương ứng với {@code detailStatuses}.
     * Nếu bucket không tồn tại (status không có trong map) thì bỏ qua.
     *
     * @param order          đơn hàng cần thêm
     * @param detailStatuses danh sách trạng thái của các chi tiết đơn hàng
     * @param statusBuckets  map: status → List<CustomerOrder>
     */
    private void addOrderToBuckets(CustomerOrder order, Collection<Integer> detailStatuses, Map<Integer, List<CustomerOrder>> statusBuckets) {
        for (Integer status : detailStatuses) {
            Optional.ofNullable(statusBuckets.get(status)).ifPresent(bucket -> bucket.add(order));
        }
    }

    public List<CustomerOrder>fetchKanbanDetail(OrderFilter filter) {

        int LIMIT = filter.limit();
        final String totalSQL = "FROM `customer_order` c left join `customer_order_detail` d on c.id = d.customer_order_id ";
        SqlBuilder sqlBuilder = SqlBuilder.init(totalSQL);
        sqlBuilder.addIntegerEquals("d.status", filter.status());
        sqlBuilder.addStringEquals("c.customer_mobile_phone", filter.customerPhone());
        sqlBuilder.addStringEquals("c.code", filter.code());

        String finalQuery = sqlBuilder.builder();
        var nativeQuery = entityManager.createNativeQuery("SELECT c.* " + finalQuery , CustomerOrder.class);
        nativeQuery.setMaxResults(LIMIT);
        var orders = EntityQuery.<CustomerOrder>getListOfNativeQuery(nativeQuery);
        return transformDetails(orders);
    }

    public Ipage<CustomerOrder>fetchList(OrderFilter filter) {
        var sale = Common.getInfo();
        if(Objects.isNull(sale)) {
            throw new RuntimeException("User not identity !");
        }

        boolean isAdminOrManager = sale.getAuthorities().stream().anyMatch(auth
            -> auth.getAuthority().equals("ROLE_ADMIN") || auth.getAuthority().equals("ROLE_SALE_MANAGER")
        );
        Integer userCreateId = (filter.saleId() != null) ?
            (isAdminOrManager ? filter.saleId() : sale.getId()) :
            (isAdminOrManager ? null : sale.getId());

        int PAGE = filter.page();
        int LIMIT = 10;
        Sort SORT = Sort.by(Sort.Direction.DESC, "createdAt");

        GenericRepository.SpecificationBuilder<CustomerOrder> oRepoFactory = orderRepository.fetch("details")
            .isEqual("userCreateId", userCreateId)
            .isEqual("customerName", filter.customerName())
            .isEqual("customerId", filter.customerId())
            .isEqual("enterpriseId", filter.enterpriseId())
            .isEqual("status", filter.status())
            .isEqual("paidStatus", filter.paidStatus())
            .isEqual("customerMobile", filter.customerPhone())
            .isEqual("customerEmail", filter.customerEmail())
            .isEqual("code", filter.code())
            .isEqual("type", filter.type());
        return oRepoFactory.toPage(PAGE * LIMIT, LIMIT, SORT);
    }

    public CustomerOrder completeOrder(Long id) {
        CustomerOrder order = orderRepository.findById(id).orElseThrow(
            () -> new RuntimeException("error no record exists")
        );
        if (order.getType().equals(CustomerOrder.TYPE_CO_HOI)) {
            throw new RuntimeException("Không thể hoàn thành đơn hàng cơ hội.");
        }
        order.setDoneAt(new Date());
        orderRepository.save(order);
        return order;
    }

    public List<CustomerOrder> transformDetails(List<CustomerOrder> orders) {
        if(Common.CollectionIsEmpty(orders)) {
            return new ArrayList<>();
        }
        var newOrders = orders.stream().map(CustomerOrder::cloneNoDetail).toList();
        var orderIds = newOrders.stream().map(CustomerOrder::getId).toList();
        var details = detailRepository.fetchDetailOrdersId(orderIds);

        Map<Long, List<CustomerOrderDetail>> mDetails = MapUtils.groupBy(details, CustomerOrderDetail::getCustomerOrderId);
        for( CustomerOrder order : newOrders ) {
            order.setDetails(MapUtils.getOrEmpty(mDetails, order.getId()));
        }
        return newOrders;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatusOrder(Long orderId, Long detailId, Integer statusId) {
        CustomerOrder order = orderRepository.findById(orderId).orElseThrow(
            () -> new RuntimeException("error no record exists")
        );
        if (order.getType().equals(CustomerOrder.TYPE_CO_HOI)) {
            throw new RuntimeException("Không thể cập nhật trạng thái vì đơn hàng là cơ hội.");
        }
        CustomerOrderStatus status = statusOrderRepository.findById(statusId).orElseThrow(
            () -> new RuntimeException("error no record exists")
        );
        detailRepository.updateDetailStatus(status.getId(), detailId);
    }

    public void sendMessageOnOrderChange(CustomerOrder order) {
        var message = Message.create(EventTopic.ORDER_CHANGE, order.clone());
        this.publish(message);
    }

    @Transactional
    public OrderResponse view(Long id) {
        var order = orderRepository.fetchWithCustomer(id).orElseThrow(
            () -> new ResourceNotFoundException("Order not found .!")
        );
        return withOrderDetail(order);
    }

    @Transactional
    public OrderResponse withOrderDetail(CustomerOrder order) {
        Hibernate.initialize(order.getDetails());
        var orderRep = new OrderResponse();
        CopyProperty.CopyNormal(order.cloneNoDetail(), orderRep);
        orderRep.setDetails(new ArrayList<>(order.getDetails()));
        orderRep.setCustomer(customerRepository.findById(orderRep.getCustomerId()).orElseThrow(
            () -> new RuntimeException("Customer not found !")
        ));
        return orderRep;
    }

    @Transactional
    public OrderResponse findByCode(String code) {
        var order = orderRepository.findByCode(code).orElseThrow(
            () -> new ResourceNotFoundException("Order not found .!")
        );
        return withOrderDetail(order);
    }

    @Transactional
    public OrderResponse findById(Long id) {
        var order = orderRepository.findById(id).orElseThrow(
            () -> new ResourceNotFoundException("Order not found .!")
        );
        log.info(order.getCustomerId());
        return withOrderDetail(order);
    }

    @Override
    public void setDelegate(EventDelegate eventDelegate) {
        this.eventDelegate = eventDelegate;
    }

    @Override
    public void publish(MessageInterface message) {
        if(Objects.nonNull(eventDelegate)) {
            eventDelegate.sendEvent(message);
        }
    }

    @Transactional
    public void cancelCoHoi(Long orderId, Boolean isDetail) {
        var model = statusOrderRepository.findCancelOrder().orElseThrow(
            () -> new RuntimeException("Chưa thiết lập trạng thái Hủy đơn !")
        );
        Integer statusCancel = model.getStatus();
        if(isDetail) {
            var order = detailRepository.findById(orderId).orElseThrow(
                () -> new RuntimeException("error no record exists")
            );
            order.setStatus(statusCancel);
        } else {
            var order = orderRepository.findById(orderId).orElseThrow(
                () -> new RuntimeException("error no record exists")
            );
            order.setStatus(statusCancel);
        }
    }

    @Transactional
    public void deleteContract(String code, String file) {
        customerContractRepository.deleteByOrderCode(code, file);
    }
}
