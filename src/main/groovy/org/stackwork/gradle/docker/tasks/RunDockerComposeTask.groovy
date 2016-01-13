package org.stackwork.gradle.docker.tasks

import org.gradle.api.Project
import org.gradle.api.internal.AbstractTask
import org.stackwork.gradle.docker.ModuleType
import org.stackwork.gradle.docker.StackworkExtension
import org.yaml.snakeyaml.Yaml

import static ModuleType.TEST_IMAGE

class RunDockerComposeTask extends AbstractTask {

  final static NAME = 'runDockerCompose'

  Map<String, Object> composeInfo
  List<String> longRunningServices
  String composeFile = project.stackwork.composeFile

  RunDockerComposeTask() {
    description = 'Runs the generated docker compose file.'
    group = 'Stackwork'

    doLast {
      composeInfo = (Map<String, Object>) new Yaml().load(project.file(composeFile).text)

      List<String> allServices = new ArrayList<>()
      allServices.addAll(composeInfo.keySet())

      List<List<String>> splitServices = allServices.split { this.isExecutableImage(it) }
      project.logger.info 'The following docker compose services were found to be executable: {}', splitServices[0]
      longRunningServices = splitServices[1]
      project.logger.info 'The following docker compose services were found to be long-running: {}', longRunningServices
      if (longRunningServices.empty) {
        throw new IllegalStateException('No long-running service defined in docker compose file')
      }
    }

    doLast {
      String composeProject = createRandomString()
      project.stackwork.composeProject = composeProject

      // start the compose project and monitor it's output
      String[] command = ['docker-compose', '-f', composeFile, '-p', composeProject, 'up'] + this.longRunningServices

      String marker = project.extensions.getByType(StackworkExtension).stackIsRunningWhenLogContains

      if(marker) {
        project.logger.info("Log marker defined: '$marker'. Using this to scan logs for start indicator.")
      } else {
        project.logger.info("No log marker defined, compose will be started 'fire and forget' style.")
      }

      project.logger.info('Starting Docker Compose.')

      spawnProcessAndWaitFor command, { String line ->
        project.logger.info line
        if(marker == null || line == null) {
          return true
        }

        if(line.contains(marker)){
          project.logger.info "Found marker in compose logs. Stack started."
          return true
        }

        return false
      }
    }

    // write the services ports and hosts
    doLast {
      longRunningServices.each { String serviceName ->
        Map<String, Object> serviceInfo = [:]
        String containerId = this.askComposeServicesContainerId(serviceName)
        Map<String, Object> containerInfo = this.dockerInspectContainer(containerId)

        // dockerHost is null in case of local Docker deamon, or the host to connect to for a remote Docker host

        containerInfo.NetworkSettings.Ports.each { String exposedPortProto, forwardedPorts ->
          int exposedPort = (exposedPortProto =~ /\d+/)[0] as int

          if (project.stackwork.host) {
            if (!forwardedPorts) {
              project.logger.warn("No port forwarding defined for service '$serviceName'. " +
                      "No port configuration will be exposed.")
              return
            }
            if (forwardedPorts.isEmpty()) {
              project.logger.warn("No forwarded port found for exposed port: '$exposedPort'")
            } else {
              if (forwardedPorts.size() > 1) {
                project.logger.warn("Multiple forwarded ports found for exposed port: '$exposedPort'. " +
                        "Continuing with a randomly selected port.")
              }
              serviceInfo.port = forwardedPorts.first().HostPort
            }
            serviceInfo.host = project.stackwork.host
          } else {
            serviceInfo.port = exposedPort
            serviceInfo["port.${exposedPort}"] = exposedPort
            serviceInfo.host = containerInfo.NetworkSettings.IPAddress
          }
        }
        project.stackwork.services["$serviceName"] = serviceInfo
        println "Setting stackwork.services.$serviceName: $serviceInfo"
      }
    }
  }

  static String createRandomString() {
    def pool = ['A'..'Z', 0..9].flatten()
    Random rand = new Random(System.currentTimeMillis())
    def passChars = (0..8).collect { pool[rand.nextInt(pool.size())] }
    passChars.join()
  }

  /**
   * Spawn an external process and monitor it's standard output for
   */
  static void spawnProcessAndWaitFor(String[] command, Closure logPredicate) {

    ProcessBuilder builder = new ProcessBuilder(command)
    builder.redirectErrorStream(true)
    Process process = builder.start()

    InputStream stdout = process.getInputStream()
    BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))

    String line
    while ((line = reader.readLine()) != null) {
      if(logPredicate(line)) {
        break;
      }
    }
  }

  boolean isExecutableImage(String serviceName) {
    Project composeProject = project.extensions.getByType(StackworkExtension).composeProject
    boolean serviceImageIsBuiltInModule = composeProject.stackwork.modules["$serviceName"]
    if (!serviceImageIsBuiltInModule) return false

    ModuleType moduleType = composeProject.stackwork.modules["$serviceName"]
    moduleType == TEST_IMAGE
  }

  String askComposeServicesContainerId(String serviceName) {
    OutputStream os = new ByteArrayOutputStream()
    project.exec {
      setCommandLine(['docker-compose', '-f', this.composeFile, '-p', project.stackwork.composeProject,
                      'ps', '-q', serviceName])
      setStandardOutput(os)
    }
    os.toString().trim()
  }

  Map<String, Object> dockerInspectContainer(String containerId) {
    OutputStream containerInfoOS = new ByteArrayOutputStream()
    project.exec {
      setCommandLine(['docker', 'inspect', containerId])
      setStandardOutput(containerInfoOS)
    }
    (new Yaml().load(containerInfoOS.toString()))[0] as Map<String, Object>
  }
}
