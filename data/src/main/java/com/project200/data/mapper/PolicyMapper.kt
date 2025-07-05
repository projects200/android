package com.project200.data.mapper

import com.project200.data.dto.ScorePolicyDTO
import com.project200.domain.model.ScorePolicy

fun ScorePolicyDTO.toDomainModel(): ScorePolicy {
    return ScorePolicy(
        policyKey = this.policyKey,
        policyValue = this.policyValue,
        policyUnit = this.policyUnit,
    )
}