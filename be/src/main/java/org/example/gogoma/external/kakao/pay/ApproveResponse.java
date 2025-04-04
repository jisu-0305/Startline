package org.example.gogoma.external.kakao.pay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ApproveResponse {

    @JsonProperty("aid")
    private String aid;

    @JsonProperty("tid")// 요청 고유 번호
    private String tid;

    @JsonProperty("cid")// 결제 고유 번호
    private String cid;// 가맹점 코드

    @JsonProperty("partner_order_id")
    private String partnerOrderId;    // 가맹점 주문번호

    @JsonProperty("partner_user_id")
    private String partnerUserId;    // 가맹점 회원 id

    @JsonProperty("payment_method_type")
    private String paymentMethodType; // 결제 수단, CARD 또는 MONEY 중 하나

    @JsonProperty("item_name")
    private String itemName;           // 상품 이름

    @JsonProperty("item_code")
    private String itemCode;           // 상품 코드

    @JsonProperty("quantity")
    private int quantity;               // 상품 수량

    @JsonProperty("created_at")
    private String createdAt;          // 결제 준비 요청 시각

    @JsonProperty("approved_at")
    private String approvedAt;         // 결제 승인 시각

    @JsonProperty("payload")
    private String payload;             // 결제 승인 요청에 대해 저장한 값, 요청 시 전달된 내용
}