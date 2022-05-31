package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceChargeException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.InvoiceStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BillingServiceTest {

    private val paymentProvider = mockk<PaymentProvider>()

    private val invoiceService = mockk<InvoiceService>()

    private val billingService = BillingService(paymentProvider, invoiceService, 2)

    @Test
    fun `chargePendingInvoice when the payment provider returns false`() {
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)

        every { paymentProvider.charge(pendingInvoice) } returns false
        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(pendingInvoice)

        billingService.chargePendingInvoices()

        verify(exactly = 1) { invoiceService.fetchByStatus(InvoiceStatus.PENDING) }
        verify(exactly = 1) { paymentProvider.charge(pendingInvoice) }
        verify(exactly = 0) { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }
    }

    @Test
    fun `chargePendingInvoice when the payment provider returns true`() {
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)

        every { paymentProvider.charge(pendingInvoice) } returns true
        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(pendingInvoice)
        every { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }

        billingService.chargePendingInvoices()

        verify(exactly = 1) { invoiceService.fetchByStatus(InvoiceStatus.PENDING) }
        verify(exactly = 1) { paymentProvider.charge(pendingInvoice) }
        verify(exactly = 1) { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }
    }

    @Test
    fun `chargePendingInvoice when the payment provider throws a NetworkException`() {
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)

        every { paymentProvider.charge(pendingInvoice) } throws NetworkException()
        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(pendingInvoice)
        every { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }

        billingService.chargePendingInvoices()

        verify(exactly = 1) { invoiceService.fetchByStatus(InvoiceStatus.PENDING) }
        verify(exactly = 3) { paymentProvider.charge(pendingInvoice) }
        verify(exactly = 0) { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }
    }

    @Test
    fun `chargePendingInvoice when charge throws NetworkException the first time and succeed subsequently`() {
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)

        every { paymentProvider.charge(pendingInvoice) }
                .throws(NetworkException())
                .andThen(true)
        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(pendingInvoice)
        every { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }

        billingService.chargePendingInvoices()

        verify(exactly = 1) { invoiceService.fetchByStatus(InvoiceStatus.PENDING) }
        verify(exactly = 2) { paymentProvider.charge(pendingInvoice) }
        verify(exactly = 1) { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }
    }

    @Test
    fun `chargePendingInvoice when charge throws NetworkException the first time and fail subsequently`() {
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)

        every { paymentProvider.charge(pendingInvoice) }
                .throws(NetworkException())
                .andThen(false)
        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(pendingInvoice)
        every { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }

        billingService.chargePendingInvoices()

        verify(exactly = 1) { invoiceService.fetchByStatus(InvoiceStatus.PENDING) }
        verify(exactly = 2) { paymentProvider.charge(pendingInvoice) }
        verify(exactly = 0) { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }
    }

    @Test
    fun `chargePendingInvoice when CustomerNotFound`() {
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)

        every { paymentProvider.charge(pendingInvoice) } throws CustomerNotFoundException(pendingInvoice.customerId)
        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(pendingInvoice)

        billingService.chargePendingInvoices()

        verify(exactly = 1) { invoiceService.fetchByStatus(InvoiceStatus.PENDING) }
        verify(exactly = 1) { paymentProvider.charge(pendingInvoice) }
        verify(exactly = 0) { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }
    }

    @Test
    fun `chargePendingInvoice when CurrencyMisMatched`() {
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)

        every { paymentProvider.charge(pendingInvoice) } throws CurrencyMismatchException(pendingInvoice.id, pendingInvoice.customerId)
        every { invoiceService.fetchByStatus(InvoiceStatus.PENDING) } returns listOf(pendingInvoice)

        billingService.chargePendingInvoices()

        verify(exactly = 1) { invoiceService.fetchByStatus(InvoiceStatus.PENDING) }
        verify(exactly = 1) { paymentProvider.charge(pendingInvoice) }
        verify(exactly = 0) { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }
    }

    @Test
    fun `manually charge specific invoice`() {
        val pendingInvoice = createInvoice(InvoiceStatus.PENDING)

        every { paymentProvider.charge(pendingInvoice) } returns true
        every { invoiceService.fetch(pendingInvoice.id) } returns pendingInvoice
        every { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }

        val result = billingService.chargeInvoice(pendingInvoice.id)

        //this is because the invoice charge execute asynchronously
        Thread.sleep(200)

        verify(exactly = 1) { invoiceService.fetch(pendingInvoice.id) }
        verify(exactly = 1) { paymentProvider.charge(pendingInvoice) }
        verify(exactly = 1) { invoiceService.updateStatus(pendingInvoice.id, InvoiceStatus.PAID) }
        assertEquals("Invoice charge is in progress", result)
    }

    @Test
    fun `manually charge already PAID invoice`() {
        val paidInvoice = createInvoice(InvoiceStatus.PAID)

        every { invoiceService.fetch(paidInvoice.id) } returns paidInvoice

        val exception = assertThrows<InvoiceChargeException> {
            billingService.chargeInvoice(paidInvoice.id)
        }

        assertEquals("Invalid request. Only invoice with status PENDING can be charged", exception.message)
        verify(exactly = 1) { invoiceService.fetch(paidInvoice.id) }
        verify(exactly = 0) { paymentProvider.charge(paidInvoice) }
        verify(exactly = 0) { invoiceService.updateStatus(paidInvoice.id, InvoiceStatus.PAID) }
    }
}