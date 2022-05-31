package io.pleo.antaeus.core.jobs

import io.pleo.antaeus.core.services.BillingService
import org.quartz.Job
import org.quartz.JobExecutionContext

class BillingJob : Job {
    override fun execute(context: JobExecutionContext?) {
        val billingService = context?.jobDetail?.jobDataMap?.get("billingService") as BillingService
        billingService.chargePendingInvoices()
    }
}