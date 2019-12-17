import ReleaseTransformations._

lazy val sonarScanner = "org.sonarsource.scanner.api" % "sonar-scanner-api" % "2.14.0.2002"

lazy val `sbt-sonar` = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "sbt-sonar",
    organization := "net.archwill.sbt",

    licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/mit-license.php")),

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

    publishMavenStyle := false,
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
