plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation("org.quartz-scheduler:quartz:2.3.0")
    implementation(project(":pleo-antaeus-data"))
    api(project(":pleo-antaeus-models"))
}