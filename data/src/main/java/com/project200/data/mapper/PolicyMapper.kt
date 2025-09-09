package com.project200.data.mapper

import com.project200.data.dto.PolicyDTO
import com.project200.data.dto.PolicyGroupDTO
import com.project200.domain.model.Policy
import com.project200.domain.model.PolicyGroup

fun PolicyGroupDTO.toDomain(): PolicyGroup {
    return PolicyGroup(
        groupName = groupName,
        size = size,
        policies = policies.map { it.toDomain() },
    )
}

fun PolicyDTO.toDomain(): Policy {
    return Policy(
        policyKey = policyKey,
        policyValue = policyValue,
        policyUnit = policyUnit,
        policyDescription = policyDescription,
    )
}
