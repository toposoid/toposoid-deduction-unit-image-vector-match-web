import de.heikoseeberger.sbtheader.License
name := """toposoid-deduction-unit-image-vector-match-web"""
organization := "com.linked.ideal"
version := "0.6-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
scalaVersion := "2.13.11"

libraryDependencies += guice
libraryDependencies += "com.ideal.linked" %% "scala-common" % "0.6-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-common" % "0.6-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-knowledgebase-model" % "0.6-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-deduction-protocol-model" % "0.6-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-deduction-common" % "0.6-SNAPSHOT"
libraryDependencies += "com.ideal.linked" %% "toposoid-feature-vectorizer" % "0.6-SNAPSHOT"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
libraryDependencies +=  "com.ideal.linked" %% "toposoid-test-utils" % "0.6-SNAPSHOT" % Test
libraryDependencies += "io.jvm.uuid" %% "scala-uuid" % "0.3.1" % Test

organizationName := "Linked Ideal LLC.[https://linked-ideal.com/]"
startYear := Some(2021)
licenses += ("AGPL-3.0-or-later", new URL("http://www.gnu.org/licenses/agpl-3.0.en.html"))
headerLicense := Some(License.AGPLv3("2025", organizationName.value))
