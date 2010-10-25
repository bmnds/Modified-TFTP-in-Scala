import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import scala.actors.Actor._

import default._

class TFTPFlatSpec extends FlatSpec with ShouldMatchers {
	"A Server" should "exist" in {
		TFTPServer.getState.toString should be === "New"
	}

	it should "Initialize and go ONLINE" in {
		TFTPServer.start
		TFTPServer.getState.toString should be === "Runnable"
	}

	it should "Suspend and wait for requests after no more than 100ms" in {
		Thread.sleep(500)
		TFTPServer.getState.toString should be === "Suspended"
	}

	it should "return an ERR message if the file doesn't exist in less than 100ms" in {
		var res = false
		val fake = actor { reactWithin(100) {
			case ERR(0, 0) => res = true
			case _ => res = false
		} }
		TFTPServer ! RRQ(fake, 0, 0, "-1")
		Thread.sleep(100)
		res should be === true
	}

	it should "return an ACK message with same client and id  equal to 0 in response to a WRQ" in {
		var res = false
		var sTID = 0
		var fake = actor { reactWithin(100) {
			case ACK(100, server, 0) => sTID = server; res = true
			case x => println(x); res = false
		} }
		TFTPServer ! WRQ(fake, 100, 69, "file")
		Thread.sleep(100)
		res should be === true
		TFTPServer ! DATA(100, sTID, 1, "") //send message to close the connection
	}

	it should "return an ACK message with the same id in response to a DATA message" in (pending)

	it should "Stop" in {
		TFTPServer ! Stop
		//while (TFTPServer.getState.id != 6) Thread.sleep(100)
		Thread.sleep(500)
		TFTPServer.getState.toString should be === "Terminated"
	}
}
