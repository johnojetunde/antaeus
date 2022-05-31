## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!


## Implementation Doc

### Rest Endpoint
* An endpoint was added to allow the manual triggering of invoice charge. .../invoices/:id/charge
* Also, the fetch all invoice endpoint was modified to allow the filtering of the invoice by status

### Billing Service
* public chargeInvoice -> This method takes in invoice id
  * fetch invoice by id, if not found, a not found exception is cascaded from the invoiceService
  * check the status of the invoice to ensure that it's still pending, 
    * if it is not pending, then fail the request by throwing InvoiceChargeException because we want to avoid duplicate charge
    * if it is pending, call the private charge invoice method asynchronously and return a message; This is because the process of charging may take time. So, it's like a fire and forget approach
    
* chargePendingInvoices -> Fetches all the pending invoices in  the store and sends them to chargeInvoice to charge. This method leveraged on parallelStream to send them in parallel
* private chargeInvoice -> This method tries to charge a pending invoice
    * if invoice charge is successful then update the invoice status to paid
    * if there is a CustomerNotFoundException or CurrencyMismatchException, log it and maybe send it to datadog for properly alert. This 2 exceptions were handled separately should the business wants to have a custom action per exception type
    * if a network error occurs from the payment provider, retry for a specified number of times (the time can be configured as an Environmental variable [MAX_RETRY_TIMES]); This is because NetworkException may be temporal. If the max retries is reached and payment provider still failed with NetworkException, we can send some metrics to datadog to capture the failure rate of the payment provider due to this error. This will help in ensuring that we have enough visibility to either change the provider based on SLA breached or probe further
    * if the charge is not successful (i.e due to balance or something), maybe an email could be sent to the customer to fund their account

*Other Suggestions: 
* When processing the invoices, we can leverage on queueing system to help scale the number of invoices that can be processed at once. Also, to ensure that we cater for system failures. 
* Also, we can load the pending invoices in batches. If we have lots of invoices, it would be too much for us to load them at once before processing.

### Billing Job/Cron
* The Scheduler is the entry point of the cron. The cron is configurable via an environmental variable [INVOICE_BILLING_CRON]
* The Scheduler contains the cron configuration. 
* The BillingJob contains the actual implementation that calls the BillingService.chargePendingInvoices()