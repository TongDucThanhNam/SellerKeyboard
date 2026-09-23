package com.terasumi.sellerkeyboard.seller

sealed interface TemplateRenderResult {
    data class Ready(val text: String) : TemplateRenderResult
    data class Unresolved(val variables: Set<String>) : TemplateRenderResult
    data class Invalid(val variables: Set<String>) : TemplateRenderResult
}

object TemplateRenderer {
    val supportedVariables = setOf(
        "customer_name", "order_id", "tracking_code", "product_name", "product_url",
        "price", "discount_code", "shop_name", "shipping_date"
    )
    // Escape both closing braces explicitly. The desktop JVM accepts bare `}}`
    // here, but Android's ICU regex compiler rejects that pattern at runtime.
    private val placeholder = Regex("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}\\}")

    fun variables(template: String): Set<String> = placeholder.findAll(template)
        .map { it.groupValues[1] }
        .toSet()

    fun render(template: String, values: Map<String, String>): TemplateRenderResult {
        val variables = variables(template)
        val invalid = variables - supportedVariables
        if (invalid.isNotEmpty()) return TemplateRenderResult.Invalid(invalid)
        val unresolved = variables.filterTo(linkedSetOf()) { values[it].isNullOrBlank() }
        if (unresolved.isNotEmpty()) return TemplateRenderResult.Unresolved(unresolved)
        val rendered = placeholder.replace(template) { match ->
            values.getValue(match.groupValues[1])
        }
        return TemplateRenderResult.Ready(rendered)
    }
}
