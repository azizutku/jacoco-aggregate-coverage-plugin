package com.azizutku.jacocoaggregatecoverageplugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.nio.charset.StandardCharsets

@CacheableTask
internal abstract class PublishJacocoAggregationParticipantTask : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    @get:OutputFile
    abstract val markerFile: RegularFileProperty

    @TaskAction
    fun publish() {
        markerFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(modulePath.get(), StandardCharsets.UTF_8)
        }
    }
}
