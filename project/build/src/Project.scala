import sbt._

class Project(info: ProjectInfo) extends DefaultProject(info) {
	val scalaToolsRepo = "Scala-Tools Maven Repository" at
		"http://scala-tools.org/repo-releases/"
	val scalatest = "org.scalatest" % "scalatest" % "1.2"
}
