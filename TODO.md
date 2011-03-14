
# TODO

* Chunk queuing in WritableStream
* Connection reuse + testing
* Connection keep-alive/close + testing
* Idle connection timeout on the server side
* Idle connection timeout on the client side
* Timeout header as per http://www.ietf.org/id/draft-thomson-hybi-http-timeout-00.txt
* Header validation - let apps register handlers to validate headers
* Reject requests with multiple CL headers
* Attachments on handlers should flow through
* Add Future support to make the API consistent for all reads and writes
* Partial message (multipart) handlers
* Client connection lifecycle tests