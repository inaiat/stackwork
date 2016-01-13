package org.stackwork.gradle.docker

import spock.lang.Specification

class DockerPluginSpecifications extends Specification {

  static final boolean NO_STACKTRACE = false

  def setup() {
    println '>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> STARTING CHILD GRADLE TEST BUILD <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<'
  }

  def cleanup() {
    println '<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<  ENDING CHILD GRADLE TEST BUILD  <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<'
  }

  def "The build image task builds the DockerFile in the project root"() {
    when:
    GradleOutput output = runGradleTask('build')

    then:
    output.process.exitValue() == 0
  }

  def "A test module that applies the JavaPlugin is allowed to connect to an image in unit tests trough appropriately set system properties"() {
    when:
    GradleOutput output = runGradleTask('unit-test')

    then:
    output.process.exitValue() == 0
  }

  def "The tag image task tags a built image with 'stackwork {imageName}:project.version' number, which is exposed as 'stackwork.fullImageName'"() {
    when:
    GradleOutput output = runGradleTask('tag')

    then:
    output.process.exitValue() == 0
  }

  def "Tagging an image does not work if the project.version is not set"() {
    when:
    GradleOutput output = runGradleTask('tag-no-version', NO_STACKTRACE)

    then:
    output.process.exitValue() != 0
    output.standardErr.contains 'No project version defined. Cannot tag image. Please set "project.version".'
  }

  def "Tagging an image does not work if the stackwork { imageName } is not set"() {
    when:
    GradleOutput output = runGradleTask('tag-no-image-name', NO_STACKTRACE)

    then:
    output.process.exitValue() != 0
    output.standardErr.contains 'No docker image name defined. Cannot tag image. Please set "stackwork { imageName }".'
  }

  def "The push image task tries to push an image."() {
    when:
    GradleOutput output = runGradleTask('push-public-hub-no-namespace', NO_STACKTRACE)

    then:
    output.process.exitValue() != 0
    output.standardErr.contains 'You cannot push a "root" repository. Please rename your repository to <user>/<repo>'
  }

  def 'The runTestImage task runs the test image built in a test-image module against the docker compose setup'() {
    when:
    GradleOutput output = runGradleTask('test-image')

    then:
    output.process.exitValue() == 0
    output.standardOut.contains 'Serving frontend'
  }

  def 'A test image designed to fail should fail the test, and thus the build'() {
    when:
    GradleOutput output = runGradleTask('test-image-failing-test', NO_STACKTRACE)

    then:
    output.process.exitValue() == 1
    output.standardErr.contains 'not.the.correct.domain'
  }

  def 'Dependencies in the "stackwork" configuration are supplied to the image build through the build directory'() {
    when:
    GradleOutput output = runGradleTask('dependencies')

    then:
    output.process.exitValue() == 0
    !output.standardErr.contains('FAILED') && !output.standardOut.contains('FAILED')
  }

  def 'Any image can be build in an "image module" and be included in a docker-compose setup'() {
    when:
    GradleOutput output = runGradleTask('proxy-with-stub-server')

    then:
    output.process.exitValue() == 0
  }

  def 'Multiple test suites with unit tests and/or test images can be set up'() {
    when:
    GradleOutput output = runGradleTask('multi-module')

    then:
    output.process.exitValue() == 0
    output.standardOut.contains 'Serving frontend'
  }

  def 'The root project does not need to do anything per se'() {
    when:
    GradleOutput output = runGradleTask('root-no-module-type')

    then:
    output.process.exitValue() == 0
  }

  def "Multiple modules can be a deliverable image, resulting in multiple pushes."() {
    when:
    GradleOutput output = runGradleTask('multiple-deliverable-images', NO_STACKTRACE)

    then:
    output.process.exitValue() == 0
    output.standardOut.contains 'Overwrote push action for test. Would otherwise now push my-image:1.1-SNAPSHOT'
    output.standardOut.contains 'Overwrote push action for test. Would otherwise now push my-second-image:1.1-SNAPSHOT'
  }

  def "The root project can be a Docker Compose module, with a base docker compose stack"() {
    when:
    GradleOutput output = runGradleTask('compose-root-project')

    then:
    output.process.exitValue() == 0
  }

  def "The root project can have a base docker compose stack that can be used by a compose module"() {
    when:
    GradleOutput output = runGradleTask('compose-root-project-compose-module')

    then:
    output.process.exitValue() == 0
  }

  def "An image build may depend on that of another module"() {
    when:
    GradleOutput output = runGradleTask('build-depending-on-build')

    then:
    output.process.exitValue() == 0
    output.standardOut.contains 'metadata-proving-we-extended-a-base-image'
  }

  def "A compose project can define a log marker indicating the stack has started"() {
    when:
    GradleOutput output = runGradleTask('compose-log-marker')

    then:
    output.process.exitValue() == 0
    output.standardOut.contains 'Log marker defined: \'https://docs.docker.com/userguide/\'. Using this to scan logs for start indicator.'
  }

  private static GradleOutput runGradleTask(String project, boolean printStacktrace = true) {
    def cmd = "./gradlew clean check cleanup -i ${printStacktrace ? '--stacktrace' : ''} --project-dir src/test/gradle-projects/$project"

    ProcessBuilder builder = new ProcessBuilder(cmd.split('\\s'))
    Process process = builder.start()

    // Capture the process output stream for later processing
    def output = new StringBuffer()
    def error = new StringBuffer()
    process.consumeProcessOutput(output, error)

    process.waitFor()
    new GradleOutput(process, output.toString(), error.toString())
  }

  private static class GradleOutput {
    private final Process process
    private final String standardOut
    private final String standardErr

    GradleOutput(Process process, String standardOut, String standardErr) {
      this.process = process
      this.standardOut = standardOut
      this.standardErr = standardErr
    }

    Process getProcess() {
      return process
    }

    String getStandardOut() {
      return standardOut
    }

    String getStandardErr() {
      return standardErr
    }
  }

}
