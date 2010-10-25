import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import scala.actors.Actor._

import default._

class TIDFlatSpec extends FlatSpec with ShouldMatchers {
	"A TID Generator" should "exist" in {
		TIDGenerator.getState.toString should be === "New"
	}

	it should "be waiting for requests" in {
		TIDGenerator.start
		Thread.sleep(200)
		TIDGenerator.getState.toString should be === "Suspended"
	}

	it should "provide a tid when requested" in {
		val tid = TIDGenerator !? TID match { case tid => tid }
		tid.isInstanceOf[Int] === true
	}

	it should "provide distinct values" in {
		import scala.collection.mutable.HashSet
		val previousTIDs = HashSet[Int]()
		var res = true
		for(i <- 1 to 100) {
			val tid: Int = TIDGenerator !? TID match { case tid: Int => tid }
			if (!previousTIDs.add(tid)) res = false
		}
		res should be === true
	}

	it should "stop when requested" in {
		TIDGenerator ! Stop
		Thread.sleep(200)
		TIDGenerator.getState.toString should be === "Terminated"
	}
}
