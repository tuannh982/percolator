import Sbt.ProjectExtension

lazy val root = (project in file("."))
  .aggregate(
    `core`
  )
  .settings(
    name := "percolator",
    publish / skip := true
  )

lazy val `core` = project
  .defaultSettings()
  .withCrossScalaSupports()

addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
addCommandAlias("fmtCheck", "all scalafmtSbtCheck scalafmtCheckAll")
