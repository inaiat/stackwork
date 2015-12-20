package org.stackwork.gradle.docker.tasks

import org.gradle.api.tasks.Copy

class CollectImageDependenciesTask extends Copy {

  final static NAME = 'collectImageDependencies'

  CollectImageDependenciesTask() {
    description = 'Copies the Docker dependencies to the build folder to make them available during an image build'
    group = 'Stackwork'

    // doLast {
      project.configurations.stackwork.resolvedConfiguration.resolvedArtifacts.each {
        from it.file
        into project.file("${project.buildDir}/stackwork-deps")
        def filename = it.name + (it.classifier ? '-' + it.classifier : '') + '.' + it.extension
        println "Preparing build/docker-artifacts/${filename}"
        rename it.file.name, filename
      }
    // }
  }

}
