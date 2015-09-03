package nl.qkrijger.gradle.docker.tasks

import org.gradle.api.tasks.Exec

class BuildImageTask extends Exec {

  BuildImageTask() {

    commandLine('sh', project.file('.gradle/docker/build.sh').absolutePath, project.projectDir)

    def buffer = new ByteArrayOutputStream()
    setStandardOutput(buffer)

    doLast {
      def output = buffer.toString('UTF-8')
      def imageIdMatcher = (output =~ /(?s).*Successfully built (?<imageId>[0-9a-f]+).*/)

      if(!imageIdMatcher.matches()) {
        throw new IllegalStateException('Failed to parse an image id from the Docker build response')
      }

      def imageId = imageIdMatcher.group('imageId')

      logger.info "Docker image id: ${imageId}"
      project.ext.docker.put('imageId',imageId)
    }
  }
}
