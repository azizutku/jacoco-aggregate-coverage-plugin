package com.azizutku.jacocoaggregatecoverageplugin.extensions

import org.gradle.api.Named
import org.gradle.api.file.DirectoryProperty
import javax.inject.Inject

abstract class JacocoAggregateReportSpec @Inject constructor(private val reportTaskName: String) : Named {

    override fun getName(): String = reportTaskName

    abstract val htmlOutputLocation: DirectoryProperty
}
