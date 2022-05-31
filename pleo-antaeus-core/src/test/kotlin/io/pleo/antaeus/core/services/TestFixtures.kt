package io.pleo.antaeus.core.services

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import java.math.BigDecimal
import kotlin.random.Random

internal fun createInvoice(status: InvoiceStatus): Invoice {
    return Invoice(id = Random.nextInt(), customerId = Random.nextInt(), status = status, amount = createMoney())
}

internal fun createMoney(): Money {
    return Money(value = BigDecimal(Random.nextDouble(10.0, 500.0)), currency = Currency.values()[Random.nextInt(4)])
}