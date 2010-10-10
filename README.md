Modified TFTP
=============

The idea behind this project was to implement [TFTP](www.faqs.org/rfcs/rfc1350.html) in Scala.
Now this project aims to make an improvement to simple TFTP, by increasing throughput and
lowering resources consumption.

The Basic Idea
-------

A server usually creates a new child process to handle each client or even each connection. In this scenario, a client must be able to have several connections, so what happens if we have millions of clients with thousands of connections each? Yeah, the server crashes or the performance goes to the trash can and the clients get pissed off!
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