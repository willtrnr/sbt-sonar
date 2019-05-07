package sbtsonar

import sbt._
import sbt.Keys._

import scala.collection.JavaConverters._

import java.io.StringWriter
import java.util.{Map => JMap, Properties}

import org.sonarsource.scanner.api.{EmbeddedScanner, LogOutput}

object SonarKeys {

  val sonarHostUrl = settingKey[URL]("Sonar host URI")
  val sonarLogin = settingKey[String]("Sonar login")

  val sonarProjectKey = settingKey[String]("Sonar project key")
  val sonarProjectName = settingKey[String]("Sonar project name")
  val sonarProjectVersion = settingKey[String]("Sonar project version")

  val sonarSourceEncoding = taskKey[String]("Sonar source encoding")

  val sonarModules = settingKey[Seq[String]]("Sonar sub-modules")

  val sonarSources = settingKey[Seq[File]]("Sonar source directories")
  val sonarTests = settingKey[Seq[File]]("Sonar test source directories")

  val sonarJavaSource = taskKey[String]("Sonar Java source version")
  val sonarJavaBinaries = settingKey[File]("Sonar Java class directory")
  val sonarJavaLibraries = taskKey[Classpath]("Sonar Java libraries")
  val sonarJavaTestBinaries = settingKey[File]("Sonar Java test class directory")
  val sonarJavaTestLibraries = taskKey[Classpath]("Sonar Java test libraries")

  val sonarJacocoReportPaths = settingKey[Seq[File]]("Sonar Jacoco .exec report paths")

  val sonarConfig = taskKey[Map[String, String]]("Sonar project full configuration")
  val generateSonarConfig = taskKey[Unit]("Generate the Sonar project configuration")
  val sonar = taskKey[Unit]("Run Sonar scanner")

}

object SonarPlugin extends AutoPlugin {

  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements
  override def projectSettings: Seq[Def.Setting[_]] = sonarDefaultSettings

  val autoImport = SonarKeys

  import autoImport._

  def sonarDefaultSettings: Seq[Def.Setting[_]] = Seq(
    sonarHostUrl := url("http://localhost:9000"),

    sonarProjectKey := organization.value,
    sonarProjectName := name.value,
    sonarProjectVersion := version.value,

    sonarModules := name.all(ScopeFilter(inDependencies(ThisProject, includeRoot = false))).value,

    sonarSourceEncoding := {
      (javacOptions.value ++ scalacOptions.value)
        .sliding(2)
        .collectFirst { case Seq("-encoding", e) => e }
        .getOrElse("UTF-8")
    },

    sonarSources := Seq((javaSource in Compile).value, (scalaSource in Compile).value).distinct,
    sonarTests := Seq((javaSource in Test).value, (scalaSource in Test).value).distinct,

    sonarJavaSource := {
      javacOptions.value
        .sliding(2)
        .collectFirst { case Seq("-source", v) => v }
        .getOrElse("7")
    },

    sonarJavaBinaries := (classDirectory in Compile).value,
    sonarJavaLibraries := (dependencyClasspath in Compile).value,

    sonarJavaTestBinaries := (classDirectory in Test).value,
    sonarJavaTestLibraries := (dependencyClasspath in Test).value,

    sonarJacocoReportPaths := Seq(crossTarget.value / "jacoco" / "data" / "jacoco.exec"),

    aggregate in sonarConfig := false,
    sonarConfig := sonarConfigTask.value,

    aggregate in generateSonarConfig := false,
    generateSonarConfig := generateSonarConfigTask.value,

    aggregate in sonar := false,
    sonar := sonarTask.value
  )

  private[this] def rel(base: File, file: File): String =
    IO.relativize(base, file).getOrElse(file.toString)

  case class SonarModuleConfig(
    name: String,
    base: File,
    sources: Seq[File],
    binaries: File,
    libraries: Classpath,
    testSources: Seq[File],
    testBinaries: File,
    testLibraries: Classpath,
    jacocoReports: Seq[File]
  )

  def generateModuleConfig(config: SonarModuleConfig, prefix: String = "", parent: Option[File] = None): Map[String, String] = {
    import Attributed._
    Map(
      (prefix + "sonar.projectBaseDir") -> parent.fold(config.base.getAbsolutePath)(p => rel(p, config.base)),
      (prefix + "sonar.sources") -> config.sources.filter(_.exists).map(rel(config.base, _)).mkString(","),
      (prefix + "sonar.java.binaries") -> rel(config.base, config.binaries),
      (prefix + "sonar.java.libraries") -> data(config.libraries).map(rel(config.base, _)).mkString(","),
      (prefix + "sonar.tests") -> config.testSources.filter(_.exists).map(rel(config.base, _)).mkString(","),
      (prefix + "sonar.java.test.binaries") -> rel(config.base, config.testBinaries),
      (prefix + "sonar.java.test.libraries") -> data(config.testLibraries).map(rel(config.base, _)).mkString(","),
      (prefix + "sonar.jacoco.reportPaths") -> config.jacocoReports.filter(_.exists).map(rel(config.base, _)).mkString(",")
    )
  }

  def sonarModuleConfigTask: Def.Initialize[Task[SonarModuleConfig]] = Def.task {
    SonarModuleConfig(
      name.value,
      baseDirectory.value,
      sonarSources.value,
      sonarJavaBinaries.value,
      sonarJavaLibraries.value,
      sonarTests.value,
      sonarJavaTestBinaries.value,
      sonarJavaTestLibraries.value,
      sonarJacocoReportPaths.value
    )
  }

  def sonarConfigTask: Def.Initialize[Task[Map[String, String]]] = Def.task {
    val modules = sonarModules.value

    val globalConfig = Map(
      "sonar.projectKey" -> sonarProjectKey.value,
      "sonar.projectName" -> sonarProjectName.value,
      "sonar.projectVersion" -> sonarProjectVersion.value,
      "sonar.modules" -> modules.mkString(","),
      "sonar.sourceEncoding" -> sonarSourceEncoding.value,
      "sonar.java.source" -> sonarJavaSource.value,
      "sonar.scala.version" -> scalaVersion.value
    )

    val rootConfig = generateModuleConfig(sonarModuleConfigTask.value)

    val moduleConfigs = {
      sonarModuleConfigTask.all(ScopeFilter(inDependencies(ThisProject, includeRoot = false))).value
        .filter(c => modules.contains(c.name))
        .map(c => generateModuleConfig(c, prefix = c.name + ".", parent = Some(baseDirectory.value)))
    }

    globalConfig ++ rootConfig ++ moduleConfigs.foldLeft(Map.empty[String, String])(_ ++ _)
  }

  def generateSonarConfigTask: Def.Initialize[Task[Unit]] = Def.task {
    val props = new Properties
    props.putAll(sonarConfig.value.asJava)
    IO.write(baseDirectory.value / "sonar-project.properties", {
      val writer = new StringWriter
      props.store(writer, null)
      writer.toString
    })
  }

  def sonarTask: Def.Initialize[Task[Unit]] = Def.task {
    import org.sonarsource.scanner.api.Utils

    val logger = streams.value.log

    val props = new Properties
    props.put("sonar.host.url", sonarHostUrl.value.toString)
    sonarLogin.?.value.foreach(props.put("sonar.login", _))
    props.putAll(sonarConfigTask.value.asJava)
    props.putAll(System.getProperties)
    props.putAll(Utils.loadEnvironmentProperties(System.getenv))

    val scanner = EmbeddedScanner.create(BuildInfo.name, BuildInfo.version, new SbtLogOutput(logger))
      .addGlobalProperties(props.asInstanceOf[JMap[String, String]])

    scanner.start()
    logger.info("SonarQube server " + scanner.serverVersion)
    scanner.execute(props.asInstanceOf[JMap[String, String]])
  }

  private[this] class SbtLogOutput(logger: Logger) extends LogOutput {

    import LogOutput.Level

    override def log(msg: String, level: Level): Unit = level match {
      case Level.TRACE | Level.DEBUG =>
        logger.debug(msg)
      case Level.ERROR =>
        logger.error(msg)
      case Level.WARN =>
        logger.warn(msg)
      case _ =>
        logger.info(msg)
    }

  }

}
