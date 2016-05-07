# sliding-window
A minimal Socket API that sends a byte stream over a network using UDP.

An application that implements a minimal Socket API and can transfer files over a network while dropping, delaying, and reordering UDP packets. The file is reassembled in the proper order using Go-Back-N Sliding Window protocol.
