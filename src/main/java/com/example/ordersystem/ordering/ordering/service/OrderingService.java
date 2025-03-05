package com.example.ordersystem.ordering.ordering.service;

import com.example.ordersystem.common.service.StockInventoryService;
import com.example.ordersystem.member.domain.Member;
import com.example.ordersystem.member.repository.MemberRepository;
import com.example.ordersystem.ordering.ordering.controller.SseController;
import com.example.ordersystem.ordering.ordering.domain.OrderDetail;
import com.example.ordersystem.ordering.ordering.domain.Ordering;
import com.example.ordersystem.ordering.ordering.dtos.OrderCreateDto;
import com.example.ordersystem.ordering.ordering.dtos.OrderDetailResDto;
import com.example.ordersystem.ordering.ordering.dtos.OrderListResDto;
import com.example.ordersystem.ordering.ordering.repository.OrderingDetailRepository;
import com.example.ordersystem.ordering.ordering.repository.OrderingRepository;
import com.example.ordersystem.product.domain.Product;
import com.example.ordersystem.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.aspectj.weaver.ast.Or;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class OrderingService {
    private final OrderingRepository orderingRepository;
    private  final MemberRepository memberRepository;
    private  final OrderingDetailRepository orderingDetailRepository;
    private  final ProductRepository productRepository;
    private final StockInventoryService stockInventoryService;

    private final SseController sseController;

    public OrderingService(OrderingRepository orderingRepository, MemberRepository memberRepository, OrderingDetailRepository orderingDetailRepository, ProductRepository productRepository, StockInventoryService stockInventoryService, SseController sseController) {
        this.orderingRepository = orderingRepository;
        this.memberRepository = memberRepository;
        this.orderingDetailRepository = orderingDetailRepository;
        this.productRepository = productRepository;
        this.stockInventoryService = stockInventoryService;
        this.sseController = sseController;
    }

    public Ordering orderCreate(List<OrderCreateDto> dtos){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(()->new EntityNotFoundException("no member"));
        Ordering ordering = Ordering.builder()
                .member(member)
                .build();
        for(OrderCreateDto o :  dtos){
           Product product = productRepository.findById(o.getProductId()).orElseThrow(()->new EntityNotFoundException("no producr"));
           int quantity = o.getProductCount();
//           동시성 이슈 고려안 된 코드
           if(product.getStockQuantity() < quantity){
               throw new IllegalArgumentException("재고부족");
           } else {
//               재고감소 로직
               product.updateStockQuantity(o.getProductCount());
           }

            OrderDetail orderDetail = OrderDetail.builder()
                    .ordering(ordering)
                    .product(product)
                    .quantity(o.getProductCount())
                    .build();
            ordering.getOrderDetails().add(orderDetail);
        }
           Ordering ordering1= orderingRepository.save(ordering);
        sseController.publishMessage(ordering1.fromEntity(),"admin@naver.com");
//        sse를 통한 admin계정에 메세지 발송

        return  ordering;
    }

    public List<OrderListResDto> orderList(){
        List<Ordering> orderings = orderingRepository.findAll();
        List<OrderListResDto> orderListResDtos = new ArrayList<>();
        for(Ordering o : orderings){
            List<OrderDetailResDto> orderDetailResDtos = new ArrayList<>();
            for(OrderDetail od : o.getOrderDetails()){
                OrderDetailResDto orderDetailResDto = OrderDetailResDto.builder()
                        .detailId(od.getId())
                        .productName(od.getProduct().getName())
                        .count(od.getQuantity())
                        .build();
                orderDetailResDtos.add(orderDetailResDto);
            }
            OrderListResDto orderDto = OrderListResDto
                    .builder()
                    .id(o.getId())
                    .memberEmail(o.getMember().getEmail())
                    .orderStatus(o.getOrderStatus().toString())
                    .orderDetails(orderDetailResDtos)
                    .build();
            orderListResDtos.add(orderDto);
        }
        return orderListResDtos;
    }

    public List<OrderListResDto> myOrders(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email   = authentication.getName();
        Member member = memberRepository.findByEmail(email).orElseThrow(()->new EntityNotFoundException("no member"));

        List<OrderListResDto> orderListResDtos = new ArrayList<>();

        for(Ordering o : member.getOrderingList()){
            orderListResDtos.add(o.fromEntity());

        }
        return orderListResDtos;
    }

    public Ordering orderCancel(Long id){
      Ordering ordering =  orderingRepository.findById(id).orElseThrow(()->new EntityNotFoundException("no order"));
      ordering.changeStatus();
//      orderingRepository.save(ordering);
      List<OrderDetail> orderDetailList = ordering.getOrderDetails();
      for(OrderDetail od : orderDetailList){
          Product product = od.getProduct();
        product.retrieveStrock(od.getQuantity());
      }
      return ordering;
    }

}
