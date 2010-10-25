/*
abstract class C { val c: Int; val s: Int; val p: String; val o: Int; def read(payload: Array[Char]): Int; def write(payload: Array[Char]): Unit  }

class RC(_c: Int; _s: Int; _o: Int, _p: String) extends C {
	require(_o == 1)
	val c: Int = _c
	val s: Int = _s
	val o: Int = _o
	val p: String = _p
	val br = new BufferedReader( new FileReader( new File(p) ) )
	def read(payload: Array[Char]): Int = br.read(payload) //TODO implement close logic
}

class WC(_c: Int; _s: Int; _o: Int, _p: String) extends C {
	require(_o == 2)
	val c: Int = _c
	val s: Int = _s
	val o: Int = _o
	val p: String = _p
	val bw = new BufferedWriter( new FileWriter( new File(p) ) ) //TODO implement close logic
	def write(payload: Array[Char]): Unit = bw.write(payload)
}
*/
/* 
 * TODO: Add Error Handling
 */

package default

import scala.actors.Actor
import scala.actors.Actor._
import scala.util.Random
import scala.collection.mutable.ListBuffer

import java.io.File
import java.io.FileWriter
import java.io.FileReader
import java.io.BufferedWriter
import java.io.BufferedReader

case class GET(path: String)
case class SEND(path: String)
abstract class MSG
trait Retransmitable { val client: Int; val server: Int; val id: Int }
case class RRQ(ref: Actor, client: Int, server: Int, path: String) extends MSG //Read Request
case class WRQ(ref: Actor, client: Int, server: Int, path: String) extends MSG //Write Request
case class ACK(client: Int, server: Int, id: Int) extends MSG with Retransmitable
case class DATA(client: Int, server: Int, id: Int, data: String) extends MSG with Retransmitable
case class ERR(client: Int, server: Int) extends MSG
case class W84ME(caller: Actor, time: Int, msg: MSG, attempts: Int)
case class Retransmit(msg: MSG, attempts: Int)
case object Stop
case object TID

case class PacketHistory(id: Int, received: Boolean = false) {
	require(id >= 0)
	def updateStatus(status: Boolean = true): PacketHistory = PacketHistory(id, status)
	override def toString = "("+id+","+received+")"
}

class PacketHistoryList {
	val packetsHistory: ListBuffer[PacketHistory] = ListBuffer( PacketHistory(1) ) //TODO remove this first packet
	
	def add(x: PacketHistory) = { 
		require (x != null)
		packetsHistory += x
	}
	def sub(x: PacketHistory) = {
		require (x != null)
		if (!packetsHistory.contains(x)) throw new NoSuchElementException
		packetsHistory -= x
	}
	def update(id: Int) = {
		require (id > 0)
		var packetHistory: PacketHistory = null
		for (x <- packetsHistory) if (x.id == id) packetHistory = x
		
		val idx = packetsHistory.indexOf( packetHistory )
		if (idx == -1) throw new NoSuchElementException

		packetsHistory.update(idx, packetHistory.updateStatus( true ))
	}
	def get(id: Int): PacketHistory = {
		require (id > 0)
		var packetHistory: PacketHistory = null
		for (x <- packetsHistory) if (x.id == id) packetHistory = x
		
		if (packetHistory == null) throw new NoSuchElementException
		return packetHistory
	}
	
	def last = packetsHistory.last
	
	override def toString = { var ph = ""; packetsHistory foreach (x => ph+=x+" "); ph }
}

case class Connection(ref: Actor, client: Int, server: Int, path: String, opcode: Int, closed: Boolean = false, packetsHistory: PacketHistoryList = new PacketHistoryList) {
	require(ref != null && client > 0 && server > 0 && !path.isEmpty && (opcode == 1 || opcode == 2))
	val file: File = if (server != 69) new File(path) else null //TODO do it only after server is set
	val reader: BufferedReader = if (opcode == 1 && !closed && file != null) new BufferedReader( new FileReader(file) ) else null //TODO do it only if it is a read request
	val writer: BufferedWriter = if (opcode == 2 && !closed && file != null) new BufferedWriter( new FileWriter(file) ) else null //TODO do it only if it is a write request
	
	def updateServer(server: Int): Connection = { if (opcode == 1 && file != null) reader.close; if (opcode == 2 && file != null) writer.close; new Connection(ref, client, server, path, opcode) }
	def updateStatus: Connection = { if (opcode == 1) reader.close; if (opcode == 2) writer.close; new Connection(ref, client, server, path, opcode, true, packetsHistory) }
	
	def isClosed = closed
	def wasPacketReceived(id: Int): Boolean = { val packetHistory = packetsHistory.get(id); if (packetHistory == null) false else packetHistory.received } //TODO remove unnecessary check
	def lastPacketReceived: Boolean = packetsHistory.last.received
	
	override def toString = " ("+client+( if (opcode==2) "<-" else "->"  )+server+") "
}

class ConnectionList {
	val connections: ListBuffer[Connection] = ListBuffer()
	def add(x: Connection) = {
		require(x != null)
		connections += x
	}
	def sub(x: Connection) = {
		require(x != null)
		connections -= x
	}
	def update(client: Int, server: Int) = {
		require(client > 0 && server > 0)
		var connection: Connection = null
		connections foreach (x => { if (x.client == client) connection = x } )
		
		val idx = connections.indexOf( connection )
		if (idx == -1) throw new NoSuchElementException
		connections.update(idx, connection.updateServer(server))
	}
	def updateStatus(conn: Connection) = {
		require(conn != null)
		
		val idx = connections.indexOf(conn)
		if (idx == -1) throw new NoSuchElementException
		connections.update(idx, conn.updateStatus)
	}
	def get(client: Int, server: Int): Connection = {
		require(client > 0 && server > 0)
		var connection: Connection = null
		connections foreach (x => { if (x.client == client && x.server == server) connection = x } )
		if (connection == null) throw new NoSuchElementException
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
					if (caller.getState.id != 6) { //TODO improve this by not waiting to retransmit an already acknowledged packet
						Thread.sleep(time)
						caller ! Retransmit(msg, attempts+1)
					}
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
		val path = System.getProperty("user.dir")+"/server/"
		retransmitter.start
		println("Server ONLINE\n")
		loop {
			react {
				case RRQ(ref, client, server, fileName) =>
					val filePath = path+fileName;
					if (!(new File(fileName)).exists) ref ! ERR(client, server)
					else {
						val tid = TIDGenerator !? TID match { case tid: Int => tid }
						val connection = new Connection(ref, client, tid, filePath, 1)
						
						connections.add( connection )
						val payload = new Array[Char](512);
						val len = connection.reader.read(payload)
						var pl: String = ""
						payload foreach (c => if (c != 0) pl += c)
						retransmitter ! W84ME(self, 200, new DATA(client, tid, 1, pl), 0)
						connection.ref ! DATA(client, tid, 1, pl)
						if (len < 512) connections.updateStatus(connection)
					}
				case WRQ(ref, client, server, fileName) =>
					val filePath = path+fileName;
					val tid = TIDGenerator !? TID match { case tid: Int => tid }
					connections.add( new Connection(ref, client, tid, filePath, 2) )
					ref ! ACK(client, tid, 0)
				case ACK(client, server, id) =>
					val connection = connections.get(client, server)
					connection.packetsHistory.update( id )
					if (connection.isClosed) connections.sub(connection)
					else {
						val payload = new Array[Char](512);
						val len = connection.reader.read(payload)
						var pl: String = ""
						payload foreach (c => if (c != 0) pl += c)
						connection.packetsHistory.add( new PacketHistory(id+1, false) )
						retransmitter ! W84ME(self, 200, new DATA(client, server, id+1, pl), 0)
						connection.ref ! DATA(client, server, id+1, pl)
						if (len < 512) connections.updateStatus(connection)
					}
				case DATA(client, server, id, data) => 
					val connection = connections.get(client, server)
					if (connection.isClosed) connections.sub( connection )
					else {
						connection.writer.write( data )
						connection.ref ! ACK(client, server, id)
						if (data.length < 512) connections.updateStatus(connection)
					}
				case ERR(client, server) =>
					//TODO implement error handling code
					null
				case "FakeAConnection" =>
					connections.add( new Connection(null, 96, 69, "fakeFile1", 1) )
					connections.get(96, 69).packetsHistory.add(new PacketHistory(1, true))
					connections.get(96, 69).packetsHistory.add(new PacketHistory(2, true))
					connections.get(96, 69).packetsHistory.add(new PacketHistory(3, false))
				case "FakeAnotherConnection" =>
					connections.add( new Connection(null, 101, 69, "fakeFile2", 2) )
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
					if (msg.isInstanceOf[Retransmitable]) {
                                        	val client = msg.asInstanceOf[Retransmitable].client
						val server = msg.asInstanceOf[Retransmitable].server
						val id = msg.asInstanceOf[Retransmitable].id
						
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
		val path = System.getProperty("user.dir")+"/client/"
		retransmitter.start
		loop {
			react {
				case GET(fileName: String) =>
					val filePath = path+fileName;
					println("Client getting file "+path)
					val tid = TIDGenerator !? TID match { case tid: Int => tid }
					connections.add( new Connection(TFTPServer, tid, 69, filePath, 2) )
					TFTPServer ! RRQ(self, tid, 69, fileName)
				case SEND(fileName: String) =>
					val filePath = path+fileName;
					println("Sending file "+path)
					val tid = TIDGenerator !? TID match { case tid: Int => tid }
					connections.add( new Connection(TFTPServer, tid, 69, filePath, 1) )
					TFTPServer ! WRQ(self, tid, 69, fileName)
				case ACK(client, server, id) =>
					if (connections.get(client, server) == null && id==0) {
						connections.update( client, server )
						println(connections.get(client, server)+" SEND Connection estabilished...")
					}
					
					val connection = connections.get(client, server)
					connection.packetsHistory.update( id )
					if (connection.isClosed) connections.sub(connection)
					else {
						val payload = new Array[Char](512);
						val len = connection.reader.read(payload)
						var pl: String = ""
						payload foreach (c => if (c != 0) pl += c)
						connection.packetsHistory.add( new PacketHistory(id+1, false) )
						retransmitter ! W84ME(self, 200, new DATA(client, server, id+1, pl), 0)
						TFTPServer ! DATA(client, server, id+1, pl)
						println(connection+"["+id+"]: '" + /* pl + */ "' ("+pl.length+")")
						if (len < 512) {
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
						println(connection+"["+id+"]: '" + /* data+ */ "'"+" ("+data.length+")")
						connection.writer.write( data )
						TFTPServer ! ACK(client, server, id)
						if (data.length < 512) {
							println(connection+" GET complete! Closing connection...")
							connections.updateStatus( connection )
						}
					}
				case ERR(client, server) =>
					var connection = connections.get(client, server)
					println(connection+" Connection rejected...")
					if (connection != null) connections.sub( connection )
				case "FakeAConnection" =>
					connections.add( new Connection(null, 96, 69, "fakeFile1", 1) )
					connections.get(96, 69).packetsHistory.add(new PacketHistory(1, true))
					connections.get(96, 69).packetsHistory.add(new PacketHistory(2, true))
					connections.get(96, 69).packetsHistory.add(new PacketHistory(3, false))
				case "FakeAnotherConnection" =>
					connections.add( new Connection(null, 103, 69, "fakeFile2", 2) )
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
					if (msg.isInstanceOf[Retransmitable]) {
						/* Capturing message parameters */
						val client: Int = msg.asInstanceOf[Retransmitable].client
						val server: Int = msg.asInstanceOf[Retransmitable].server
						val id: Int = msg.asInstanceOf[Retransmitable].id
						
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
	val clientDirPath = System.getProperty("user.dir") + "/client/"
	val serverDirPath = System.getProperty("user.dir") + "/server/"
	val clientDir = new File(clientDirPath)
	val serverDir = new File(serverDirPath)
	
	def cleanFiles = {
		println("Cleaning files...")
	        if (!clientDir.exists) clientDir.mkdir	
                for (file <- clientDir.listFiles)
			file.delete
	        if (!serverDir.exists) serverDir.mkdir	
		for (file <- serverDir.listFiles)
			file.delete
		println("Files cleaned.")
	}
	
	def createFiles = {
		def createFile(path: String, fileNumber: Int, packetsNumber: Int) = {
			def generateContent: String = {
				def generateBodyContent: String = {
					def generateInnerBodyContent: String = {
						var innerBodyContent = ""
						for (i <- 0 to 9)
							innerBodyContent += ((i + fileNumber)%10).toString
						innerBodyContent
					}
					
					var bodyContent = ""
					val innerBodyContent = generateInnerBodyContent
					for (i <- 0 to 9)
						bodyContent += innerBodyContent
					return bodyContent
				}
				
				def generateEndContent: String = {
					var endContent = ""
					for (i <- 'a' to 'l')
						endContent += (i + fileNumber%10).toChar
					return endContent
				}
			
				var content = ""
				val bodyContent = generateBodyContent
				val endContent = generateEndContent
				for (i <- 1 to 5)
					content += bodyContent
				content += endContent
				return content
			}
		
			val fileName = "file"+fileNumber
			val file = new BufferedWriter( new FileWriter( new File( path, fileName ) ) )
			val content = generateContent
			for (i <- 1 until packetsNumber)
				file.write(content)
			file.write("\nThis is "+fileName+" with "+packetsNumber+" packets")
			file.close
		}
	
		println("Creating client files...")
		//Create Client's Files
		createFile(clientDirPath, 0, 1)
		createFile(clientDirPath, 1, 2)
		createFile(clientDirPath, 2, 5)
		createFile(clientDirPath, 3, 10)
		createFile(clientDirPath, 4, 50)
		createFile(clientDirPath, 5, 50)
		createFile(clientDirPath, 6, 100)
		createFile(clientDirPath, 7, 100)
		createFile(clientDirPath, 8, 500)
		createFile(clientDirPath, 9, 1000)
		println("Client Files created.")
		
		println("Creating server files...")
		//Create Server's Files
		createFile(serverDirPath, 10, 1)
		createFile(serverDirPath, 11, 2)
		createFile(serverDirPath, 12, 5)
		createFile(serverDirPath, 13, 10)
		createFile(serverDirPath, 14, 50)
		createFile(serverDirPath, 15, 50)
		createFile(serverDirPath, 16, 100)
		createFile(serverDirPath, 17, 100)
		createFile(serverDirPath, 18, 500)
		createFile(serverDirPath, 19, 1000)
		println("Server Files created.")
	}
	
	def main(args: Array[String]) = {
		cleanFiles
		createFiles
	
		TIDGenerator.start
		TFTPServer.start
		
		val client10 = new TFTPClient
		client10 ! SEND("file0")
		client10.start
		client10 ! Stop
		
		while (client10.getState.id != 6) Thread.sleep(100)
		println

		val client11 = new TFTPClient
		client11 ! SEND("file1")
		client11.start
		client11 ! Stop
		
		while (client11.getState.id != 6) Thread.sleep(100)
		println
		
		val client12 = new TFTPClient
		client12 ! SEND("file2")
		client12.start
		client12 ! Stop
		
		while (client12.getState.id != 6) Thread.sleep(100)
		println
		
		val client13 = new TFTPClient
		client13 ! SEND("file3")
		client13.start
		client13 ! Stop
		
		while (client13.getState.id != 6) Thread.sleep(100)
		println
		
		val client20 = new TFTPClient
		client20 ! GET("file10")
		client20.start
		client20 ! Stop
		
		if (client20.getState.id != 6) Thread.sleep(100)
		println

		val client21 = new TFTPClient
		client21 ! GET("file11")
		client21.start
		client21 ! Stop
		
		while (client21.getState.id != 6) Thread.sleep(100)
		println
		
		val client22 = new TFTPClient
		client22 ! GET("file12")
		client22.start
		client22 ! Stop
		
		while (client22.getState.id != 6) Thread.sleep(100)
		println
		
		val client23 = new TFTPClient
		client23 ! GET("file13")
		client23.start
		client23 ! Stop
		
		while (client23.getState.id != 6) Thread.sleep(100)
		println
		
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
		
		while (fakeClient1.getState.id != 6) Thread.sleep(100)
		println
		
		val fakeClient2 = new TFTPClient
		fakeClient2.start
		fakeClient2 ! "FakeAnotherConnection"
		fakeClient2 ! "FakeAnotherRetransmition"
		fakeClient2 ! Stop
		
		while (fakeClient2.getState.id != 6) Thread.sleep(100)
		println
		
		val client3 = new TFTPClient
		client3 ! GET("file14")
		client3 ! GET("file15")
		client3 ! SEND("file4")		
		client3.start
		Thread.sleep(50)
		client3 ! SEND("file5")		
		client3 ! SEND("file6")
		client3 ! GET("file16")
		client3 ! Stop
		
		while (client3.getState.id != 6) Thread.sleep(100)
		println
		
		val client4 = new TFTPClient
		client4 ! GET("file")
		client4.start
		client4 ! Stop
		
		while (client4.getState.id != 6) Thread.sleep(100)
		println
		
		TFTPServer ! Stop
		TIDGenerator ! Stop
		while (TFTPServer.getState.id != 6)
			Thread.sleep(200)
	}
}
