import org.scalatest.FlatSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.ShouldMatchers
import scala.actors.Actor._

import default._

class PHLFlatSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {

	var phl: PacketHistoryList = null

	override def beforeEach { phl = new PacketHistoryList }


	"A Packet History List" should "always start with packet 0" in {
		phl.get(1) should not be null
	}

	it should "throw NoSuchElementException when asked to get a non-existent packet" in {
		evaluating( phl.get(9999) ) should produce [NoSuchElementException]
	}


	it should "throw NoSuchElementException when asked to update a non-existent packet" in {
		evaluating( phl.update(9999) ) should produce [NoSuchElementException]
	}

	it should "throw NoSuchElementException when asked to subtract a non-existent packet" in {
		evaluating( phl.sub( PacketHistory(9999) ) ) should produce [NoSuchElementException]
	}

	it should "throw NoSuchElementException when asked for the last packet of an empty list" in {
		phl.sub( PacketHistory(1) )
		evaluating( phl.last ) should produce [NoSuchElementException]
	}
	
	it should "throw IllegalArgumentException when asked to add a null object" in {
		evaluating( phl.add(null) ) should produce [IllegalArgumentException]
	}

	it should "throw IllegalArgumentException when asked to subtract a null object" in {
		evaluating( phl.sub(null) ) should produce [IllegalArgumentException]
	}
	
	it should "throw IllegalArgumentException when asked to get a packet with negative id" in {
		evaluating( phl.get(-1) ) should produce [IllegalArgumentException]
	}

	it should "throw IllegalArgumentException when asked to update a packet with negative id" in {
		evaluating( phl.update(-1) ) should produce [IllegalArgumentException]
	}

	it should "update the received status of the given packet" in {
		phl.get(1).received should be === false
		phl.update(1)
		phl.get(1).received should be === true
	}
}
