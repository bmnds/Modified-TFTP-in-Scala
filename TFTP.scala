/* 
 * TODO: Implement real File read/write
 * TODO: Add Error Handling
 */

/* 
 * Date: 08/10/2010
 * Retransmition performance enhancement -> only 1 waiting actor instead of 1 per packet
 * Switched Timer Trait by Retransmitter Class
 */

/* 
 * Date: 05/10/2010
 * Added retransmition control
 */

/* 
 * Date: 04/10/2010
 * Added packet history class and list
 */

/* 
 * Date: 03/10/2010
 * Added retransmition message and handler
 */

import scala.actors.Actor
import scala.actors.Actor._
import scala.util.Random
import scala.collection.mutable.ListBuffer

//import java.io.FileWriter
//import java.io.FileReader
case class GET(path: String)
case class SEND(path: String)
case class MSG
case class RRQ(ref: Actor, client: Int, server: Int, path: String) extends MSG //Read Request
case class WRQ(ref: Actor, client: Int, server: Int, path: String) extends MSG //Write Request
case class ACK(client: Int, server: Int, id: Int) extends MSG
case class DATA(client: Int, server: Int, id: Int, data: Byte) extends MSG
case class ERR(client: Int, server: Int) extends MSG
case class W84ME(caller: Actor, time: Int, msg: MSG, attempts: Int)
case class Retransmit(msg: MSG, attempts: Int)
case class Stop
case class TID

class PacketHistory(_id: Int, _status: Boolean) {
	val id = _id
	val status = _status
	def received = status
	def updateStatus(_status: Boolean): PacketHistory = new PacketHistory(id, _status)
	override def toString = "("+id+","+status+")"
}

class PacketHistoryList{
	val packetsHistory: ListBuffer[PacketHistory] = ListBuffer(new PacketHistory(1, false))
	
	def add(x: PacketHistory) = { packetsHistory += x }
	def sub(x: PacketHistory) = { packetsHistory -= x }
	def update(id: Int) = {
		var packetHistory: PacketHistory = null
		packetsHistory foreach (x => { if (x.id == id) packetHistory = x } )
		
		val idx = packetsHistory.indexOf( packetHistory )
		if (idx != -1)
			packetsHistory.update(idx, packetHistory.updateStatus( true ))
		
		//val idx = packetsHistory.findIndexOf( packetHistory => packetHistory.asInstanceOf[PacketHistory].id == id)
		//if (idx != -1)
		//	packetsHistory.update(idx, new PacketHistory(id, true))
	}
	def get(id: Int): PacketHistory = {
		var packetHistory: PacketHistory = null
		packetsHistory foreach (x => { if (x.id == id) packetHistory = x } )
		return packetHistory
	}
	
	def last = if (packetsHistory.isEmpty) null else packetsHistory.last //TODO remove unnecessary checks
	
	override def toString = { var ph = ""; packetsHistory foreach (x => ph+=x+" "); ph }
}

class Connection(_ref: Actor, _client: Int, _server: Int, _path: String, _opcode: Int, _closed: Boolean = false, _packetsHistory: PacketHistoryList = new PacketHistoryList) {
	val ref = _ref
	val client = _client
	val server = _server
	val path = _path
	val opcode = _opcode
	val closed = _closed
	val packetsHistory = _packetsHistory
	
	def updateServer(_server: Int): Connection = new Connection(ref, client, _server, path, opcode)
	def updateStatus: Connection = new Connection(ref, client, server, path, opcode, true, packetsHistory)
	
	def isClosed = closed
	def wasPacketReceived(id: Int): Boolean = { val packetHistory = packetsHistory.get(id); if (packetHistory == null) false else packetHistory.received } //TODO remove unnecessary check
	def lastPacketReceived: Boolean = packetsHistory.last.received
	
	override def toString = " ("+client+( if (opcode==1) "<-" else "->"  )+server+") "
}

class ConnectionList {
	val connections: ListBuffer[Connection] = ListBuffer()
	def add(x: Connection) = { connections += x }
	def sub(x: Connection) = { connections -= x }
	def update(client: Int, server: Int) = {
		var connection: Connection = null
		connections foreach (x => { if (x.client == client) connection = x } )
		
		val idx = connections.indexOf( connection )
		if (idx != -1)
			connections.update(idx, connection.updateServer(server))
		//var connection: Connection = null
		//val idx = connections.findIndexOf( conn => if (conn.asInstanceOf[Connection].client == client) { connection = conn; true } )
		//if (idx != -1)
		//	connections.update(idx, connection.updateServer(server))
	}
	def updateStatus(conn: Connection) = {
		connections.update(connections.indexOf(conn), conn.updateStatus)
	}
	def get(client: Int, server: Int): Connection = {
		var connection: Connection = null
		connections foreach (x => { if (x.client == client && x.server == server) connection = x } )
		return connection
	}
}

object TIDGenerator extends Actor {
	private var nextTID = 100;
	def act = {
		loop {
			react {
				case TID =>
					reply(nextTID)
					nextTID += 2
				case Stop =>
					exit('stop)
			}
		}
	}
}

class Retransmitter extends Actor {
	def act = {
		println("Retransmitter ON")
		loop {
			react {
				case W84ME(caller, time, msg, attempts) =>
					Thread.sleep(time)
					caller ! Retransmit(msg, attempts+1)
				case Stop =>
					println("Retransmitter OFF")
					exit('stop)
			}
		}
	}
}

trait Timer {
	val connections = new ConnectionList
	val retransmitter = new Retransmitter
	
	var finished = false
	
	def wait4me(time: Int) {
		val caller = self
		actor {
			Thread.sleep(time)
			caller ! Stop
		}
	}
}

object TFTPServer extends Actor with Timer {
	def act = {
		println("Starting server...")
		retransmitter.start
		println("Server ONLINE\n")
		loop {
			react {
				case RRQ(ref, client, server, path) =>
					if (path.equalsIgnoreCase("c:\\file0")) {
						//println("Server sending ERR...")
						ref ! ERR(client, server)
					} else {
						val tid = TIDGenerator !? TID match { case tid: Int => tid }
						connections.add( new Connection(ref, client, tid, path, 1) )
						//println("Server sending DATA...")
						ref ! DATA(client, tid, 1, 0x7F) //TODO change to a real byte
					}
				case WRQ(ref, client, server, path) =>
					val tid = TIDGenerator !? TID match { case tid: Int => tid }
					connections.add( new Connection(ref, client, tid, path, 2) )
					//println("Server sending ACK...")
					ref ! ACK(client, tid, 0)
				case ACK(client, server, id) =>
					val connection = connections.get(client, server)
					connection.packetsHistory.update( id )
					if (connection.isClosed) connections.sub(connection)
					else {
						val byteToSend = if (id+1<20) (id*3-1).toByte else 0.toByte; //TODO change to a real byte
						connection.packetsHistory.add( new PacketHistory(id+1, false) )
						retransmitter ! W84ME(self, 100, new DATA(client, server, id+1, byteToSend), 0)
						connection.ref ! DATA(client, server, id+1, byteToSend)
						if (byteToSend == 0) connections.updateStatus(connection)
					}
				case DATA(client, server, id, data) => 
					val connection = connections.get(client, server)
					if (connection.isClosed) connections.sub( connection )
					else {
						connection.ref ! ACK(client, server, id)
						if (data == 0) connections.updateStatus(connection)
					}
				case ERR(client, server) =>
					//TODO implement error handling code
					null
				case "FakeAConnection" =>
					connections.add( new Connection(null, 96, 69, "D:fakeFile1", 1) )
					connections.get(96, 69).packetsHistory.add(new PacketHistory(1, true))
					connections.get(96, 69).packetsHistory.add(new PacketHistory(2, true))
					connections.get(96, 69).packetsHistory.add(new PacketHistory(3, false))
				case "FakeAnotherConnection" =>
					connections.add( new Connection(null, 101, 69, "D:fakeFile2", 2) )
					connections.get(101, 69).packetsHistory.add(new PacketHistory(1, true))
					connections.get(101, 69).packetsHistory.add(new PacketHistory(2, false))
				case "FakeARetransmition" =>
					println("Faking a Server retransmition...")
					// send a message that will never be delivered
					retransmitter ! W84ME( self, 100, new ACK(96, 69, 3), 0 )
				case "FakeAnotherRetransmition" =>
					println("Faking another Server retransmition...")
					// send a message that will never be delivered
					retransmitter ! W84ME( self, 100, new ACK(101, 69, 2), 0 )
				case Retransmit(msg, attempts) =>
					if (msg.isInstanceOf[ACK] || msg.isInstanceOf[DATA] || msg.isInstanceOf[ERR]) {
						/* Capturing message parameters */
						val client: Int = msg.productElement(0).asInstanceOf[Int]
						val server: Int = msg.productElement(1).asInstanceOf[Int]
						val id: Int = msg.productElement(2).asInstanceOf[Int]
						
						val connection = connections.get(client, server)
						if (connection == null) {
							//println(connection+"Couldn't Retransmit "+msg+"! Connection not found!")
						} else {
							/* test code */ if (attempts >= 3 && client == 101) connection.packetsHistory.update( id )
							if (attempts >=5) {
								//println(connection+" "+connection.packetsHistory)
								println(connection+"Maximum number of retransmition attempts exceeded! Closing connection...")
								connections.sub( connection )
							}
							else if (!connection.wasPacketReceived(id)) {
								//println(connection+" "+connection.packetsHistory)
								println(connection+"Retransmitting "+msg+"...")
								retransmitter ! W84ME( self, 100, msg, attempts )
							} else {
								//println(connection+"Packet already received. Ignoring retransmition request...")
								/* test code */ if (client == 101 && server == 69) connections.sub( connection )
							}
						}
					} else
						println("This packet cannot be retransmitted!")
				case Stop =>
					if (finished) {
						println("Server shutting down...")
						retransmitter ! Stop
						println("Server OFFLINE")
						exit('stop)
					} else {
						wait4me(200)
						finished = true
						connections.connections foreach ( connection => if (!connection.isClosed) finished = false )
					}
			}
		}
		println("\nServer OFFLINE")
	}
}

class TFTPClient extends Actor with Timer {
	def act = {
		retransmitter.start
		loop {
			react {
				case GET(path: String) =>
					println("Client getting file "+path)
					val tid = TIDGenerator !? TID match { case tid: Int => tid }
					connections.add( new Connection(TFTPServer, tid, 69, path, 1) )
					TFTPServer ! RRQ(self, tid, 69, path)
				case SEND(path: String) =>
					println("Sending file "+path)
					val tid = TIDGenerator !? TID match { case tid: Int => tid }
					connections.add( new Connection(TFTPServer, tid, 69, path, 2) )
					TFTPServer ! WRQ(self, tid, 69, path)
				case ACK(client, server, id) =>
					if (connections.get(client, server) == null && id==0) {
						connections.update( client, server )
						println(connections.get(client, server)+" SEND Connection estabilished...")
					}
					
					val connection = connections.get(client, server)
					connection.packetsHistory.update( id )
					if (connection.isClosed) connections.sub(connection)
					else {
						val byteToSend = if (id <= 10) (id+1).toByte else 0.toByte;
						connection.packetsHistory.add( new PacketHistory(id+1, false) )
						retransmitter ! W84ME(self, 200, new DATA(client, server, id+1, byteToSend), 0)
						TFTPServer ! DATA(client, server, id+1, byteToSend)
						println(connection+"["+id+"]: "+byteToSend)
						if (byteToSend == 0) {
							println(connection+" SEND complete! Closing connection...")
							connections.updateStatus(connection)
						}
					}
				case DATA(client, server, id, data) =>
					if (connections.get(client, server) == null && id == 1) {
						connections.update( client, server )
						println(connections.get(client, server)+" GET Connection estabilished...")
					}
					
					val connection = connections.get(client, server)
					if (connection.isClosed) connections.sub( connection )
					else {
						println(connection+"["+id+"]: "+data)
						TFTPServer ! ACK(client, server, id)
						if (data == 0) {
							println(connection+" GET complete! Closing connection...")
							connections.updateStatus( connection )
						}
					}
				case ERR(client, server) =>
					var connection = connections.get(client, server)
					println(connection+" Connection rejected...")
					if (connection != null) connections.sub( connection )
				case "FakeAConnection" =>
					connections.add( new Connection(null, 96, 69, "D:fakeFile1", 1) )
					connections.get(96, 69).packetsHistory.add(new PacketHistory(1, true))
					connections.get(96, 69).packetsHistory.add(new PacketHistory(2, true))
					connections.get(96, 69).packetsHistory.add(new PacketHistory(3, false))
				case "FakeAnotherConnection" =>
					connections.add( new Connection(null, 103, 69, "D:fakeFile2", 2) )
					connections.get(103, 69).packetsHistory.add(new PacketHistory(1, true))
					connections.get(103, 69).packetsHistory.add(new PacketHistory(2, false))
				case "FakeARetransmition" =>
					println("Faking a Client retransmition...")
					// send a message that will never be delivered
					retransmitter ! W84ME( self, 100, new ACK(96, 69, 3), 0 )
				case "FakeAnotherRetransmition" =>
					println("Faking another Client retransmition...")
					// send a message that will never be delivered
					retransmitter ! W84ME( self, 100, new ACK(103, 69, 2), 0 )
				case Retransmit(msg, attempts) =>
					if (msg.isInstanceOf[ACK] || msg.isInstanceOf[DATA] || msg.isInstanceOf[ERR]) {
						/* Capturing message parameters */
						val client: Int = msg.productElement(0).asInstanceOf[Int]
						val server: Int = msg.productElement(1).asInstanceOf[Int]
						val id: Int = msg.productElement(2).asInstanceOf[Int]
						
						val connection = connections.get(client, server)
						if (connection == null) {
							//println(connection+"Couldn't Retransmit "+msg+"! Connection not found!")
						} else {
							/* test code */ if (attempts >= 3 && client == 103) connection.packetsHistory.update( id )
							if (attempts >=5) {
								//println(connection+" "+connection.packetsHistory)
								println(connection+"Maximum number of retransmition attempts exceeded! Closing connection...")
								connections.sub( connection )
							}
							else if (!connection.wasPacketReceived(id)) {
								//println(connection+" "+connection.packetsHistory)
								println(connection+"Retransmitting "+msg+"...")
								retransmitter ! W84ME( self, 200, msg, attempts )
							} else {
								//println(connection+"Packet already received. Ignoring retransmition request...")
								/* test code */ if (client == 103 && server == 69) connections.sub( connection )
							}
						}
					} else
						println("This packet cannot be retransmitted: "+msg)
				case Stop =>
					if (finished) {
						println("Client stopping...")
						retransmitter ! Stop
						exit('stop)
					} else {
						wait4me(250)
						finished = true
						connections.connections foreach ( connection => if (!connection.isClosed) finished = false )
					}
			}
		}
	}
}

object Main {
	def main = {
		TIDGenerator.start
		TFTPServer.start
		
		//create a fake connection and fake a retransmission of a packet
		TFTPServer ! "FakeAConnection"
		TFTPServer ! "FakeARetransmition"
		
		Thread.sleep(3200)
		println
		
		TFTPServer ! "FakeAnotherConnection"
		TFTPServer ! "FakeAnotherRetransmition"
		
		Thread.sleep(2500)
		println
		
		val fakeClient1 = new TFTPClient
		fakeClient1.start
		fakeClient1 ! "FakeAConnection"
		fakeClient1 ! "FakeARetransmition"
		fakeClient1 ! Stop
		
		Thread.sleep(3200)
		println
		
		val fakeClient2 = new TFTPClient
		fakeClient2.start
		fakeClient2 ! "FakeAnotherConnection"
		fakeClient2 ! "FakeAnotherRetransmition"
		fakeClient2 ! Stop
		
		Thread.sleep(2500)
		println
		
		val client1 = new TFTPClient
		client1 ! GET("C:\\file1")
		client1.start
		//Thread.sleep(2000)
		client1 ! Stop
		
		Thread.sleep(5500)
		println
		
		val client2 = new TFTPClient
		client2 ! SEND("C:\\file2")
		client2.start
		//Thread.sleep(2000)
		client2 ! Stop
		
		Thread.sleep(5500)
		println
		
		val client3 = new TFTPClient
		client3 ! GET("C:\\file1")
		client3 ! GET("C:\\file2")
		client3 ! SEND("C:\\file3")		
		client3.start
		Thread.sleep(50)
		client3 ! SEND("C:\\file4")		
		client3 ! SEND("C:\\file5")		
		client3 ! Stop
		
		Thread.sleep(10000)
		println
		
		val client4 = new TFTPClient
		client4 ! GET("C:\\file0")
		client4.start
		client4 ! Stop
		
		Thread.sleep(2200)
		println
		
		TFTPServer ! Stop
		TIDGenerator ! Stop
		Thread.sleep(2200)
	}
}