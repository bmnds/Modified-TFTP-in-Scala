import org.scalatest.FlatSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.ShouldMatchers
import scala.actors.Actor._

import default._

class ConnFlatSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {

	val path = System.getProperty("user.dir") + "/test"
	var file: java.io.File = null	

	override def beforeEach = { file = new java.io.File(path); file.createNewFile }
	override def afterEach = file.delete

	"A Connection" should "throw IllegalArgumentException in case a null reference" +
				" or an invalid client tid" +
				" or an invalid server tid" + 
				" or an invalid path string" in {
		evaluating( ReadConnection(null, 1, 2, "path") ) should produce [IllegalArgumentException]
		evaluating( ReadConnection(self, 0, 2, "path") ) should produce [IllegalArgumentException]
		evaluating( ReadConnection(self, 1, 0, "path") ) should produce [IllegalArgumentException]
		evaluating( ReadConnection(self, 1, 2, "") ) should produce [IllegalArgumentException]
		evaluating( ReadConnection(self, 1, 2, "path", true, null) ) should produce [IllegalArgumentException]
	}

	it should "update the server tid" in {
		ReadConnection(self, 1, 69, path).updateServer(3).server should be === 3
	}

	it should "update the status" in {
		ReadConnection(self, 1, 69, path).updateStatus.isClosed should be === true
	}

	it should "throw IllegalArgumentException if the packet id is invalid" in {
		evaluating( ReadConnection(self, 1, 69, "path").wasPacketReceived(-1) ) should produce [IllegalArgumentException]
	}

	it should "throw NoSuchElementException if the packet doesn't exist" in {
		evaluating( ReadConnection(self, 1, 69, "path").wasPacketReceived(2) ) should produce [NoSuchElementException]
	}

	it should "return the status of the last packet" in {
		val phl = new PacketHistoryList
		phl.add( PacketHistory(2, true))
		ReadConnection(self, 1, 69, path, false, phl).wasLastPacketReceived should be === true
	}
}
