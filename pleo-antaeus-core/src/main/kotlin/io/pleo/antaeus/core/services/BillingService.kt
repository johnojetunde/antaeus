package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceChargeException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.lang.Thread.sleep
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

private val logger = KotlinLogging.logger {}

class BillingService(
        private val paymentProvider: PaymentProvider,
        private val invoiceService: InvoiceService,
        private val maxInvoiceRetryTimes: Int
) {
    fun chargePendingInvoices() {
        logger.info("charge of pending invoices about to start on {}", LocalDateTime.now().toString())
        logger.debug("retrieving pending invoices")

        val invoices = invoiceService.fetchByStatus(InvoiceStatus.PENDING)
        chargeInvoices(invoices)
    }

    fun chargeInvoice(invoiceId: Int): String {
        val invoice = invoiceService.fetch(invoiceId)

        if (InvoiceStatus.PENDING == invoice.status) {
            CompletableFuture.runAsync {
                chargeInvoice(invoice, maxInvoiceRetryTimes)
            }
            return "Invoice charge is in progress"
        } else {
            logger.debug("Invoice with %d is not pending. Invoice's status is %s", invoice.id, invoice.status)
            throw InvoiceChargeException("Invalid request. Only invoice with status PENDING can be charged")
        }
    }

    private fun chargeInvoices(invoices: List<Invoice>) {
        invoices.parallelStream().forEach { invoice -> chargeInvoice(invoice, maxInvoiceRetryTimes) }
    }

    private fun chargeInvoice(invoice: Invoice, tries: Int) {
        try {
            logger.debug("charging invoice with id %d", invoice.id)
            val response = paymentProvider.charge(invoice)
            if (response) {
                invoiceService.updateStatus(invoice.id, InvoiceStatus.PAID)
                return
            } else {
                logger.debug("charging of invoice %d fails due to insufficient balance", invoice.id)
                sendFundAccountNotification(invoice.customerId)
                return
            }
        } catch (e: CustomerNotFoundException) {
            //handle customerNotFound exception based on business rule
            logger.error(String.format("Unable to charge invoice %d due to: ", invoice.id), e)
        } catch (e: CurrencyMismatchException) {
            //handle currency mismatch exception separately
            logger.error(String.format("Unable to charge invoice %d due to currency mismatch.", invoice.id), e)
        } catch (e: NetworkException) {
            if (tries > 0) {
                sleep(5000) // to retry in 5 seconds
                return chargeInvoice(invoice, tries - 1)
            }
            //record metrics in datadog
            logger.error(String.format("Invoice charge fails due to network exceptions. Charging tried %d times.", maxInvoiceRetryTimes), e)
        } catch (e: Exception) {
            logger.error(String.format("Unknown error occurred while charging invoice %d: ", invoice.id), e)
        }
    }

    private fun sendFundAccountNotification(customerId: Int) {
        //send email to customer to nudge them about low account balance
    }
}
