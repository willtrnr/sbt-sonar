import ReleaseTransformations._

lazy val sonarScanner = "org.sonarsource.scanner.api" % "sonar-scanner-api" % "2.14.0.2002"

lazy val `sbt-sonar` = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    organization := "net.archwill.sbt",
    version := (version in ThisBuild).value,

    name := "sbt-sonar",
    description := "Sonar Scanner plugin for sbt",

    homepage := Some(url("https://github.com/willtrnr/sbt-sonar")),

    licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT"))),

    scmInfo := Some(ScmInfo(
      url("https://github.com/willtrnr/sbt-sonar"),
      "scm:git:https://github.com/willtrnr/sbt-sonar.git",
      "scm:git:ssh://git@github.com/willtrnr/sbt-sonar.git"
    )),

    publishTo := Some("GitHub" at "https://maven.pkg.github.com/willtrnr/maven-repo"),

    sbtPlugin := true,
    crossSbtVersions := Seq("1.2.8", "0.13.18"),

    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-Xfuture",
      "-Ywarn-adapted-args",
      "-Ywarn-dead-code"
    ),

    libraryDependencies += sonarScanner,

    publishMavenStyle := true,
    publishArtifact in Test := false,

    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      "sonarScannerVersion" -> sonarScanner.revision
    ),

    buildInfoPackage := "sbtsonar",

    releaseCrossBuild := false,

    releaseProcess := Seq(
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      releaseStepCommandAndRemaining("^test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("^publish"),
      setNextVersion,
      commitNextVersion,
      pushChanges,
    ),
  )
