package com.example.los_service.api.record;

import java.math.BigDecimal;

public record InvestorReq(Long investorId, Long userId, Long loanId, BigDecimal amount) {
}

