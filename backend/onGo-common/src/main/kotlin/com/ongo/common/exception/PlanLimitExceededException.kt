package com.ongo.common.exception

class PlanLimitExceededException(
    val feature: String,
    val limit: Int,
) : BusinessException("PLAN_LIMIT_EXCEEDED", "$feature 한도를 초과했습니다. 현재 플랜 한도: $limit")
