package org.example.gogoma.domain.marathon.service;

import org.example.gogoma.domain.marathon.dto.UserApplyMarathonDto;

public interface MarathonApplyService {
    void applyMarathon(UserApplyMarathonDto userApplyMarathonDto, String marathonApplyUrl, int formNumber);
}
