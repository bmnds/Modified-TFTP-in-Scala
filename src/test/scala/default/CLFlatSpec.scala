import org.scalatest.FlatSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.ShouldMatchers
import scala.actors.Actor._

import default._

class CLFlatSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach {

	var cl: ConnectionList = null
	val conn = ReadConnection(self, 1, 2, "path")

	override def beforeEach { cl = new ConnectionList }

	"A Connection List" should "throw IllegalArgumentException when " +
			"a null connection is passed to be added or " +
			"a null connection is passed to be removed or " +
			"a null connection is passed to be updated or " +
			"an invalid client id is passed to the get method or " +
			"an invalid server id is passed to the get method or " +
			"an invalid client id is passed to the update method or " +
			"an invalid server id is passed to the update method" in {
		evaluating( cl.add(null) ) should produce [IllegalArgumentException]
		evaluating( cl.sub(null) ) should produce [IllegalArgumentException]
		evaluating( cl.updateStatus(null) ) should produce [IllegalArgumentException]
		evaluating( cl.get(0, 1) ) should produce [IllegalArgumentException]
		evaluating( cl.get(1, 0) ) should produce [IllegalArgumentException]
		evaluating( cl.update(0, 1) ) should produce [IllegalArgumentException]
		evaluating( cl.update(1, 0) ) should produce [IllegalArgumentException]
	}

	it should "throw NoSuchElementException when " +
			"the get method can't match the given parameters with any connection in the list or " +
			"the update method can't match the given parameters with any connection in the list or " +
			"the updateStatus method can't match the given connection with any connection in the list" in {
		evaluating( cl.get(1, 2) ) should produce [NoSuchElementException]
		evaluating( cl.update(1, 2) ) should produce [NoSuchElementException]
		evaluating( cl.updateStatus( ReadConnection(self, 1, 2, "path") ) ) should produce [NoSuchElementException]
	}

	it should "add the given connection to the list" in {
		var found = false
		for (x <- cl.connections) if (x.equals(conn)) found = true
		found should be === false

		cl.add(conn)
		for (x <- cl.connections) if (x.equals(conn)) found = true
		found should be === true
	}

	it should "remove the given connection from the list" in {
		var found = false
		cl.connections += conn
		for (x <- cl.connections) if (x.equals(conn)) found = true
		found should be === true

		found = false
		cl.sub(conn)
		for (x <- cl.connections) if (x.equals(conn)) found = true
		found should be === false
	}

	it should "get the connection which matches the given client and server" in {
		cl.connections += conn
		cl.get(conn.client, conn.server) should be === conn
	}

	it should "update the server of the connection which matches the given client" in {
		cl.connections += conn
		evaluating( cl.get(conn.client, conn.server+100) ) should produce [NoSuchElementException]

		cl.update(conn.client, conn.server+100)
		evaluating( cl.get(conn.client, conn.server) ) should produce [NoSuchElementException]
	}

	it should "update the status of the given connection" in {
		cl.connections += conn
		cl.get(conn.client, conn.server).isClosed should be === false

		cl.updateStatus(conn)
		cl.get(conn.client, conn.server).isClosed should be === true
	}
}
