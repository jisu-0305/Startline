package org.example.gogoma.controller.request;


import lombok.*;
import org.example.gogoma.domain.marathon.enums.MarathonStatus;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarathonSearchRequest {

    private MarathonStatus marathonStatus; // OPEN, CLOSED, FINISHED
    private String city;                   // 시/도 (예: 서울, 부산...)
    private String year;                   // 대회 년 (예: 1998, 2015...)
    private String month;                  // 대회 월 (예: 1,2,3,...)
    private List<Integer> courseTypeList;   // 종목 거리 (cm)
    private String keyword;                // 마라톤 명 (예: 머니투데이)
}
