import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import scala.actors.Actor._

import default._

class PHFlatSpec extends FlatSpec with ShouldMatchers {
	"A Packet History" should "not allow a negative id" in {
		evaluating(PacketHistory(-1)) should produce[IllegalArgumentException]
	}

	it should "update the status" in {
		val ph = PacketHistory(1)
		ph.received should be === false
		ph.updateStatus().received should be === true
	}
}
