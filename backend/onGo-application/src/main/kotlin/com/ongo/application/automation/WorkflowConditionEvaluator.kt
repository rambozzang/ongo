package com.ongo.application.automation

import com.ongo.domain.automation.ConditionGroupType
import com.ongo.domain.automation.ConditionOperator
import com.ongo.domain.automation.WorkflowCondition
import org.slf4j.LoggerFactory
import org.springframework.expression.EvaluationContext
import org.springframework.expression.spel.SpelEvaluationException
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.SimpleEvaluationContext
import org.springframework.stereotype.Component

@Component
class WorkflowConditionEvaluator {

    private val log = LoggerFactory.getLogger(WorkflowConditionEvaluator::class.java)
    private val parser = SpelExpressionParser()

    /**
     * Allowed root variables for SpEL expressions.
     * Only whitelisted properties can be accessed.
     */
    private val ALLOWED_ROOT_VARS = setOf(
        "video", "channel", "analytics", "comment", "schedule", "user"
    )

    fun evaluate(conditions: List<WorkflowCondition>, contextData: Map<String, Any?>): Boolean {
        if (conditions.isEmpty()) return true

        // Default: AND all root conditions
        return conditions.all { evaluateCondition(it, contextData) }
    }

    private fun evaluateCondition(condition: WorkflowCondition, contextData: Map<String, Any?>): Boolean {
        // If has nested conditions, evaluate as a group
        if (condition.nestedConditions.isNotEmpty()) {
            return when (condition.groupType) {
                ConditionGroupType.AND -> condition.nestedConditions.all { evaluateCondition(it, contextData) }
                ConditionGroupType.OR -> condition.nestedConditions.any { evaluateCondition(it, contextData) }
            }
        }

        // SpEL expression evaluation
        val expression = condition.expression
        if (!expression.isNullOrBlank()) {
            return evaluateSpelExpression(expression, contextData)
        }

        // Simple field/operator/value evaluation
        val field = condition.field
        val operator = condition.operator
        val value = condition.value
        if (field != null && operator != null && value != null) {
            return evaluateFieldCondition(field, operator, value, contextData)
        }

        log.warn("Condition has no expression, nested conditions, or field/operator/value: {}", condition)
        return true
    }

    private fun evaluateSpelExpression(expression: String, contextData: Map<String, Any?>): Boolean {
        return try {
            val context = buildSafeEvaluationContext(contextData)
            val parsed = parser.parseExpression(expression)
            parsed.getValue(context, Boolean::class.java) ?: false
        } catch (e: SpelEvaluationException) {
            log.warn("SpEL evaluation failed for expression '{}': {}", expression, e.message)
            false
        } catch (e: Exception) {
            log.error("Unexpected error evaluating SpEL expression '{}': {}", expression, e.message)
            false
        }
    }

    private fun buildSafeEvaluationContext(contextData: Map<String, Any?>): EvaluationContext {
        val context = SimpleEvaluationContext
            .forReadOnlyDataBinding()
            .build()

        // Only set whitelisted variables
        for ((key, value) in contextData) {
            if (key in ALLOWED_ROOT_VARS && value != null) {
                context.setVariable(key, value)
            }
        }

        return context
    }

    private fun evaluateFieldCondition(
        field: String,
        operator: ConditionOperator,
        value: String,
        contextData: Map<String, Any?>,
    ): Boolean {
        // Resolve field value from context using SpEL
        val actualValue = try {
            val context = buildSafeEvaluationContext(contextData)
            val expr = parser.parseExpression(field)
            expr.getValue(context)
        } catch (e: Exception) {
            log.warn("Failed to resolve field '{}': {}", field, e.message)
            return false
        }

        return when (operator) {
            ConditionOperator.EQUALS -> actualValue?.toString() == value
            ConditionOperator.NOT_EQUALS -> actualValue?.toString() != value
            ConditionOperator.GT -> compareNumeric(actualValue, value) { a, b -> a > b }
            ConditionOperator.GTE -> compareNumeric(actualValue, value) { a, b -> a >= b }
            ConditionOperator.LT -> compareNumeric(actualValue, value) { a, b -> a < b }
            ConditionOperator.LTE -> compareNumeric(actualValue, value) { a, b -> a <= b }
            ConditionOperator.CONTAINS -> actualValue?.toString()?.contains(value, ignoreCase = true) ?: false
            ConditionOperator.NOT_CONTAINS -> !(actualValue?.toString()?.contains(value, ignoreCase = true) ?: false)
            ConditionOperator.REGEX -> try {
                Regex(value).containsMatchIn(actualValue?.toString() ?: "")
            } catch (_: Exception) { false }
            ConditionOperator.IN -> value.split(",").map { it.trim() }.contains(actualValue?.toString())
        }
    }

    private fun compareNumeric(actual: Any?, expected: String, comparator: (Double, Double) -> Boolean): Boolean {
        val actualNum = when (actual) {
            is Number -> actual.toDouble()
            is String -> actual.toDoubleOrNull()
            else -> null
        } ?: return false

        val expectedNum = expected.toDoubleOrNull() ?: return false
        return comparator(actualNum, expectedNum)
    }
}
