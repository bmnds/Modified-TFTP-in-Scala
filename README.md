Modified TFTP
=============

The idea behind this project was to implement a [TFTP](www.faqs.org/rfcs/rfc1350.html) simulation in Scala.
Now this project aims to make an improvement to simple TFTP, by increasing throughput and lowering resources consumption.

The Basic Idea
-------

A server usually creates a new child process to handle each client or even each connection. In the proposed scenario, a client must be able to have several connections, so what happens if we have millions of clients with thousands of connections each? Yeah, the server crashes or the performance goes to the trash can and the clients get pissed off!
The idea here is to use Scala actors, which are a lightweight abstraction of a process to represent both our clients and our server.
Moreover, since TFTP has ACK packets, we must implement some kind of retransmition/timeout strategy.
To start, the retransmition with a max number of attempts after a timeout will be used.
The principle here is: each process has one retransmitter associated with it. Whenever a client/server has to retransmit something, the retransmitter object will wait so the client/server can handle other tasks in the meantime.
Problem is, if we are giving the wait responsability to another process, what happens if in the middle of the timeout we receive an acknowledge? Should we wait the timeout and then send the next packet? That would be very ugly...
Here we are gonna move on the the next packet and whenever a retransmitter wakes up trying to retransmit an already received packet, we simply discard it. But if the packet hasn't been received yet, we are gonna send it again and set a new retransmition until the maximum number of attempts isn't exceeded.
By doing that we focus on the main transfer and just in case of failures we will have some sort of "low performance".


What's the point?
------------

You should be thinking right now:
"Ok, he said the focus is on performance but whenever a failure occurs during the transmittion there is a performance leak. WTF!?"
Remember our scenario here is considering a client will probably have several connections at the same time, so our goal is to solve the parallelization problem.
With our strategy of lowering the resources of an error-prone transfer, we have more resources for transfers needing more. How?
Suppose we have two connections. Since we are using scala actors, whenever a message arrives, we handle it and move to the next one, right?
So what happens if our response to a DATA packet doesn't come? We will probably keep receiving ACK responses to the other connection's DATA packets, and since we have a retransmitter object waiting instead of ourselves, we can keep handling the other connection's ACKs and maximize our throughput without wasting resources.
Why did I say 'without wasting resources'? Imagine our destination for the first connection is down (but we can't know it) but the destination of the second connection is still up. A straightforward strategy would go and send all the packets at once and then retransmit the necessary ones, but all the packets sent to the first one would need retransmition. So if it was a large file, a lot of wasted retransmitions would be done. The way we propose only the retransmition of the first packet would have to be done.


Wait a sec... doesn't TFTP ignores the packets' history?
------------

Yeah, TFTP ignores history and uses ACKs to avoid sequencing the packets.
But here we only maintain a list consisting of an integer and a boolean value, that's what? 4bytes + 1bit = 33bits for each 512bytes sent to the destination.
So in a file of 1MB we would use (1.048.576B/512B)*33b/8 = 8KB of extra memory, pretty low for the gain in performance, huh?


Current Status
------------

Right now the message control, file handling and data transfer are done. So basically, we can start connections, end them, send packets, receive and acknowledge them, retrasmit when necessary and have multiple clients with multiple connections as well as getting existing files from the destination machine and writing files to them too. There is no browsing, since we just have access to one folder we work with the filename only.
The next steps are:
1. (DONE) Handle search/creation of files (as well as preventing the clients from accessing improper areas of the disk)
2. (DONE) Read/Write from real files (after 1 is complete)
3. (DONE) Change the DATA packet to carry 512bytes instead of 1byte (after 2 is complete)
4. Change actors to remote actors so real transfer can be accomplished between different machines (after 3 is complete)
5. Conduct a performance test and compare with other strategies (after all the rest is complete)