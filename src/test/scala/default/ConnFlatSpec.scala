import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import scala.actors.Actor._

import default._

class ConnFlatSpec extends FlatSpec with ShouldMatchers {
	"A Connection" should "throw IllegalArgumentException in case a null reference" +
				" or an invalid client tid" +
				" or an invalid server tid" + 
				" or an invalid path string" +
				" or an invalid opcode is passed" in {
		evaluating( Connection(null, 1, 2, "path", 1) ) should produce [IllegalArgumentException]
		evaluating( Connection(self, 0, 2, "path", 1) ) should produce [IllegalArgumentException]
		evaluating( Connection(self, 1, 0, "path", 1) ) should produce [IllegalArgumentException]
		evaluating( Connection(self, 1, 2, "", 1) ) should produce [IllegalArgumentException]
		evaluating( Connection(self, 1, 2, "path", 0) ) should produce [IllegalArgumentException]
		evaluating( Connection(self, 1, 2, "path", 3) ) should produce [IllegalArgumentException]
	}

	it should "update the server tid" in {
		val path = System.getProperty("user.dir") + "/test"
		val file = new java.io.File(path)
		file.createNewFile
		Connection(self, 1, 69, path, 1).updateServer(3).server should be === 3
		file.delete
	}

	it should "update the status" in {
		val path = System.getProperty("user.dir") + "/test"
		Connection(self, 1, 69, path, 2).updateStatus.closed should be === true
	}

	it should "throw IllegalArgumentException if the packet id is invalid" in {
		evaluating( Connection(self, 1, 69, "path", 1).wasPacketReceived(-1) ) should produce [IllegalArgumentException]
	}

	it should "throw NoSuchElementException if the packet doesn't exist" in {
		evaluating( Connection(self, 1, 69, "path", 1).wasPacketReceived(2) ) should produce [NoSuchElementException]
	}

	it should "return the status of the last packet" in {
		val path = System.getProperty("user.dir") + "/test"
		val phl = new PacketHistoryList
		phl.add( PacketHistory(2, true))
		Connection(self, 1, 69, path, 2, false, phl).lastPacketReceived should be === true
	}
}
