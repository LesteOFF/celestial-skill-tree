#!/usr/bin/env python3
"""TCP proxy that delays every chunk by a fixed one-way latency (order preserved), for multiplayer QA.

Usage: latency_proxy.py LISTEN_PORT TARGET_PORT ONE_WAY_MS
The Beta client connects through it, so its round trip to the dedicated server is at least 2 * ONE_WAY_MS.
"""
import asyncio
import sys
import time

LISTEN, TARGET, DELAY = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3]) / 1000.0


async def pump(reader, writer):
    queue = asyncio.Queue()

    async def read():
        while data := await reader.read(65536):
            await queue.put((time.monotonic() + DELAY, data))
        await queue.put((0, None))

    async def write():
        while True:
            due, data = await queue.get()
            if data is None:
                break
            await asyncio.sleep(max(0.0, due - time.monotonic()))
            writer.write(data)
            await writer.drain()
        writer.close()

    await asyncio.gather(read(), write(), return_exceptions=True)


async def handle(client_reader, client_writer):
    server_reader, server_writer = await asyncio.open_connection("127.0.0.1", TARGET)
    print(f"proxy: connection opened ({DELAY * 1000:.0f} ms each way)", flush=True)
    await asyncio.gather(pump(client_reader, server_writer), pump(server_reader, client_writer), return_exceptions=True)
    print("proxy: connection closed", flush=True)


async def main():
    server = await asyncio.start_server(handle, "127.0.0.1", LISTEN)
    print(f"proxy: 127.0.0.1:{LISTEN} -> 127.0.0.1:{TARGET}", flush=True)
    async with server:
        await server.serve_forever()


asyncio.run(main())
