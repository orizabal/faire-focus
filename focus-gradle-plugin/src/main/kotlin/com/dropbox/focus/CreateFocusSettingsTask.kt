package com.dropbox.focus

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.accessors.runtime.addExternalModuleDependencyTo
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Not worth caching")
public abstract class CreateFocusSettingsTask : DefaultTask() {

  @get:OutputFile
  public abstract val settingsFile: RegularFileProperty

  init {
    outputs.upToDateWhen { false }
  }

  @TaskAction
  public fun createFocusSettings() {
    val dependencies = project.collectDependencies().sortedBy { it.path }
    val tasks = project.tasks.map { it.path }
//    val externalDependencies = project.configurations.forEach { config ->
//      config.dependencies
//        .filterIsInstance<ExternalModuleDependency>()
//        .map { it.name }
//    }

    settingsFile.get().asFile.writer().use { writer ->
      writer.write("// ${project.path} specific settings\n")
      writer.appendLine("//")
      writer.appendLine("// This file is autogenerated by the focus task. Changes will be overwritten.")
      writer.appendLine()

      // Add the includes statements
      dependencies.forEach { dep ->
        writer.appendLine("include(\"${dep.path}\")")
      }

      tasks.forEach { task ->
        writer.appendLine("include(\"${task}\")")
      }

      writer.appendLine()

      // Add overrides for projects with a root that's different from the gradle path
      val literalQuoteChar = "'"
      dependencies
        .forEach { dep ->
          val gradleProjectPath = dep.path.substring(1).replace(":", "/")
          if (project.rootDir.resolve(gradleProjectPath) != dep.projectDir) {
            writer.appendLine("project(\"${dep.path}\").projectDir = new File($literalQuoteChar${dep.projectDir}$literalQuoteChar)")
          }
        }
    }
  }

  private fun Project.collectDependencies(): Set<Project> {
    val result = mutableSetOf<Project>()
    fun addDependent(project: Project) {
      val configuredProject = this.evaluationDependsOn(project.path)
      if (result.add(configuredProject)) {
        configuredProject.configurations.forEach { config ->
          config.dependencies
            .filterIsInstance<ProjectDependency>()
            .map { it.dependencyProject }
            .forEach(::addDependent)
        }
      }
    }

    addDependent(this)
    return result
  }

  public companion object {
    public operator fun invoke(subExtension: FocusSubExtension): CreateFocusSettingsTask.() -> Unit = {
      group = FOCUS_TASK_GROUP
      settingsFile.set(subExtension.focusSettingsFile)
      notCompatibleWithConfigurationCache("This reads configurations from the project at action-time.")
    }
  }
}
