package com.azizutku.jacocoaggregatecoverageplugin.extensions

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class JacocoAggregateReportPluginExtension @Inject constructor(objects: ObjectFactory) {
    abstract val reportTaskName: Property<String>

    val reports: NamedDomainObjectContainer<JacocoAggregateReportSpec> =
        objects.domainObjectContainer(JacocoAggregateReportSpec::class.java)

    fun register(
        reportTaskName: String,
        action: Action<in JacocoAggregateReportSpec>,
    ): NamedDomainObjectProvider<JacocoAggregateReportSpec> = reports.register(reportTaskName, action)
}
