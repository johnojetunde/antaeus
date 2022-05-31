package io.pleo.antaeus.app

import io.pleo.antaeus.core.jobs.BillingJob
import io.pleo.antaeus.core.services.BillingService
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory

class Scheduler {

    fun scheduleBilling(billingService: BillingService, cronExpression: String) {
        val jobDetail: JobDetail = JobBuilder.newJob(BillingJob::class.java)
                .build()
        jobDetail.jobDataMap["billingService"] = billingService

        val trigger = TriggerBuilder.newTrigger()
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build()

        val scheduler = StdSchedulerFactory().scheduler

        scheduler.start()
        scheduler.scheduleJob(jobDetail, trigger)
    }
}